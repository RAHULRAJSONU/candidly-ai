package ai.candidly.career.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The audit ledger itself (docs/02 §3.2's transactional-outbox intent, without Kafka -
 * see standing no-Kafka/Docker/K8s/Redis instruction). {@link #record} is called from
 * inside the same {@code @Transactional} method that makes the decision it logs
 * ({@code Propagation.MANDATORY} enforces this at runtime rather than silently opening a
 * second transaction that could commit independently and reintroduce the dual-write gap
 * docs/02 §3.1 warns about) so the decision and its ledger row commit or roll back
 * together as one atomic write - no broker, no second system that can diverge.
 *
 * <p>Simplification: chain continuation reads the previous row's hash and then inserts
 * the next one without a serializing lock, so two concurrent writers could theoretically
 * both read the same tail and produce two rows claiming the same {@code previousHash}.
 * Every decision path in this app is effectively single-writer per subject (one
 * screening, one rescoring, one tailoring review at a time), so this hasn't been
 * observed; a real multi-writer deployment would need `SELECT ... FOR UPDATE` on the
 * tail row or a Postgres advisory lock around the read-then-insert.
 */
@Service
public class AuditLedgerService {

    private static final String GENESIS_HASH = "0".repeat(64);

    private final AuditEventRepository repository;
    private final AuditCheckpointRepository checkpointRepository;

    public AuditLedgerService(AuditEventRepository repository, AuditCheckpointRepository checkpointRepository) {
        this.repository = repository;
        this.checkpointRepository = checkpointRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(AuditEventType eventType, UUID subjectId, String details) {
        // Falls back to the latest checkpoint's hash, not straight to GENESIS_HASH, once
        // purgeOlderThan has ever emptied the table - otherwise the first row written
        // after a purge would claim GENESIS_HASH as its previousHash, which verifyChain
        // (correctly) rejects since it expects the checkpoint's hash there instead.
        String previousHash = repository.findTopByOrderByOccurredAtDescIdDesc()
                .map(AuditEvent::getHash)
                .orElseGet(this::startingHash);
        // Truncated to microseconds because Postgres' timestamp column round-trips at
        // microsecond precision - hashing the untruncated Instant.now() nanosecond value
        // would make the hash recomputed after a reload (verifyChain) never match the one
        // computed here, reporting every row as tampered even when nothing touched it.
        Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String hash = sha256(previousHash + "|" + eventType + "|" + subjectId + "|" + details + "|" + occurredAt);
        return repository.save(new AuditEvent(eventType, subjectId, details, occurredAt, previousHash, hash));
    }

    /**
     * Recomputes every surviving row's hash from its own fields plus the recorded
     * previous hash and compares it against the stored hash (docs/02 §3.4's
     * "tamper-evident... verifiably append-only" goal). Returns the id of the first row
     * that fails to verify, or empty if the whole (surviving) chain is intact.
     *
     * <p>Starts from the latest {@link AuditCheckpoint}'s hash instead of always
     * {@link #GENESIS_HASH} once {@link #purgeOlderThan} has ever run - see that
     * method's javadoc.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> verifyChain() {
        return verifyChainFrom(startingHash());
    }

    /**
     * Deletes every audit row older than {@code cutoff}, recording a checkpoint so
     * {@link #verifyChain} keeps working on what's left (docs/02 §2 "storage
     * limitation... set TTLs" - see {@link AuditCheckpoint}'s javadoc for the
     * retention-vs-immutability tension this resolves). Refuses to purge if the chain
     * is not currently intact: a checkpoint recorded on top of an already-tampered
     * chain would make the tampering permanently unrecoverable and unverifiable, which
     * defeats the entire point of hash-chaining in the first place. Not called
     * {@code this.verifyChain()} internally on purpose - see {@link #verifyChainFrom}
     * - to keep this method's own {@code @Transactional} semantics (not
     * {@code readOnly}) the only ones in effect for the whole operation, rather than
     * relying on self-invocation to not matter here (it happens not to, since
     * {@code readOnly} is just a hint, but the private helper makes that non-issue
     * explicit rather than accidental - see CLAUDE.md's self-invocation sharp edges).
     *
     * @return the number of rows purged (0 if {@code cutoff} predates every row on file)
     */
    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        if (verifyChainFrom(startingHash()).isPresent()) {
            throw new IllegalStateException("Refusing to purge: the audit chain is not currently intact");
        }

        List<AuditEvent> toPurge = repository.findByOccurredAtBeforeOrderByOccurredAtAscIdAsc(cutoff);
        if (toPurge.isEmpty()) {
            return 0;
        }

        AuditEvent lastPurged = toPurge.get(toPurge.size() - 1);
        checkpointRepository.save(new AuditCheckpoint(lastPurged.getHash(), lastPurged.getOccurredAt(), toPurge.size()));
        checkpointRepository.flush();

        repository.deleteAll(toPurge);
        repository.flush();

        return toPurge.size();
    }

    private String startingHash() {
        return checkpointRepository.findTopByOrderByCreatedAtDesc().map(AuditCheckpoint::getHash).orElse(GENESIS_HASH);
    }

    private Optional<UUID> verifyChainFrom(String startingHash) {
        List<AuditEvent> events = repository.findAllByOrderByOccurredAtAscIdAsc();
        String expectedPrevious = startingHash;
        for (AuditEvent event : events) {
            if (!event.getPreviousHash().equals(expectedPrevious)) {
                return Optional.of(event.getId());
            }
            String recomputed = sha256(event.getPreviousHash() + "|" + event.getEventType() + "|"
                    + event.getSubjectId() + "|" + event.getDetails() + "|" + event.getOccurredAt());
            if (!recomputed.equals(event.getHash())) {
                return Optional.of(event.getId());
            }
            expectedPrevious = event.getHash();
        }
        return Optional.empty();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
