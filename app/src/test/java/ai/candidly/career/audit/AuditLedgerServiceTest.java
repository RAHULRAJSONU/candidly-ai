package ai.candidly.career.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AuditLedgerServiceTest {

    private static final String GENESIS_HASH = "0".repeat(64);

    private final List<AuditEvent> ledger = new ArrayList<>();
    private final List<AuditCheckpoint> checkpoints = new ArrayList<>();
    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final AuditCheckpointRepository checkpointRepository = mock(AuditCheckpointRepository.class);
    private final AuditLedgerService service = new AuditLedgerService(repository, checkpointRepository);

    {
        when(repository.save(any(AuditEvent.class))).thenAnswer(invocation -> {
            AuditEvent event = invocation.getArgument(0);
            setId(event);
            ledger.add(event);
            return event;
        });
        when(repository.findTopByOrderByOccurredAtDescIdDesc()).thenAnswer(
                invocation -> ledger.isEmpty() ? Optional.empty() : Optional.of(ledger.get(ledger.size() - 1)));
        when(repository.findAllByOrderByOccurredAtAscIdAsc()).thenAnswer(invocation -> List.copyOf(ledger));
        when(repository.findByOccurredAtBeforeOrderByOccurredAtAscIdAsc(any())).thenAnswer(invocation -> {
            Instant cutoff = invocation.getArgument(0);
            return ledger.stream().filter(e -> e.getOccurredAt().isBefore(cutoff)).toList();
        });
        doAnswer(invocation -> {
            List<AuditEvent> toDelete = invocation.getArgument(0);
            ledger.removeAll(toDelete);
            return null;
        }).when(repository).deleteAll(anyList());
        when(checkpointRepository.findTopByOrderByCreatedAtDesc()).thenAnswer(
                invocation -> checkpoints.isEmpty() ? Optional.empty() : Optional.of(checkpoints.get(checkpoints.size() - 1)));
        when(checkpointRepository.save(any(AuditCheckpoint.class))).thenAnswer(invocation -> {
            AuditCheckpoint checkpoint = invocation.getArgument(0);
            checkpoints.add(checkpoint);
            return checkpoint;
        });
    }

    @Test
    void chainOfHonestWritesVerifiesIntact() {
        service.record(AuditEventType.JOB_POSTING_SCREENED, UUID.randomUUID(), "screened as PASS");
        service.record(AuditEventType.MATCH_SCORED, UUID.randomUUID(), "composite=0.81");
        service.record(AuditEventType.TAILORED_ARTIFACT_REVIEWED, UUID.randomUUID(), "decision=APPROVED");

        assertThat(ledger).hasSize(3);
        assertThat(ledger.get(1).getPreviousHash()).isEqualTo(ledger.get(0).getHash());
        assertThat(ledger.get(2).getPreviousHash()).isEqualTo(ledger.get(1).getHash());
        assertThat(service.verifyChain()).isEmpty();
    }

    @Test
    void mutatingAHistoricalRowBreaksVerification() {
        service.record(AuditEventType.JOB_POSTING_SCREENED, UUID.randomUUID(), "screened as PASS");
        service.record(AuditEventType.MATCH_SCORED, UUID.randomUUID(), "composite=0.81");

        // Simulate a row being tampered with directly in the database, bypassing the
        // service (the exact threat docs/02 §3.4's hash chain exists to catch).
        AuditEvent tampered = ledger.get(0);
        setDetails(tampered, "screened as BLOCK");

        assertThat(service.verifyChain()).contains(tampered.getId());
    }

    @Test
    void purgeRemovesRowsOlderThanCutoffAndKeepsChainVerifiable() {
        // record() always stamps Instant.now(), so a genuinely old row is built directly
        // here with a correctly-computed hash, standing in for one written a year ago -
        // mutating occurredAt on an already-hashed row after the fact would (correctly)
        // look like tampering instead, which is a different test (see below).
        AuditEvent oldEvent = syntheticEvent(GENESIS_HASH, Instant.now().minus(400, ChronoUnit.DAYS), "old event");
        ledger.add(oldEvent);

        service.record(AuditEventType.MATCH_SCORED, UUID.randomUUID(), "recent event");

        int purged = service.purgeOlderThan(Instant.now().minus(365, ChronoUnit.DAYS));

        assertThat(purged).isEqualTo(1);
        assertThat(ledger).hasSize(1);
        assertThat(checkpoints).hasSize(1);
        assertThat(checkpoints.get(0).getHash()).isEqualTo(oldEvent.getHash());
        assertThat(service.verifyChain()).isEmpty();
    }

    @Test
    void recordAfterPurgingEveryRowChainsFromTheCheckpointNotGenesis() {
        AuditEvent oldEvent = syntheticEvent(GENESIS_HASH, Instant.now().minus(400, ChronoUnit.DAYS), "old event");
        ledger.add(oldEvent);

        // Purge the only row, leaving the table completely empty - the case
        // findTopByOrderByOccurredAtDescIdDesc() can no longer answer from the table
        // itself, so record() must fall back to the checkpoint, not GENESIS_HASH.
        int purged = service.purgeOlderThan(Instant.now());
        assertThat(purged).isEqualTo(1);
        assertThat(ledger).isEmpty();

        AuditEvent newEvent = service.record(AuditEventType.MATCH_SCORED, UUID.randomUUID(), "post-purge event");

        assertThat(newEvent.getPreviousHash()).isEqualTo(checkpoints.get(0).getHash());
        assertThat(service.verifyChain()).isEmpty();
    }

    @Test
    void purgeRefusesWhenTheChainIsAlreadyBroken() {
        AuditEvent oldEvent = syntheticEvent(GENESIS_HASH, Instant.now().minus(400, ChronoUnit.DAYS), "screened as PASS");
        ledger.add(oldEvent);
        setDetails(oldEvent, "tampered");

        assertThatThrownBy(() -> service.purgeOlderThan(Instant.now().minus(365, ChronoUnit.DAYS)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ledger).hasSize(1);
        assertThat(checkpoints).isEmpty();
    }

    private void setId(AuditEvent event) {
        setField(event, "id", UUID.randomUUID());
    }

    private void setDetails(AuditEvent event, String details) {
        setField(event, "details", details);
    }

    private void setField(AuditEvent event, String fieldName, Object value) {
        try {
            Field field = AuditEvent.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(event, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Builds a correctly-hashed row as if {@code record()} had written it at {@code occurredAt}. */
    private AuditEvent syntheticEvent(String previousHash, Instant occurredAt, String details) {
        Instant truncated = occurredAt.truncatedTo(ChronoUnit.MICROS);
        UUID subjectId = UUID.randomUUID();
        String hash = sha256(previousHash + "|" + AuditEventType.JOB_POSTING_SCREENED + "|" + subjectId + "|"
                + details + "|" + truncated);
        AuditEvent event = new AuditEvent(AuditEventType.JOB_POSTING_SCREENED, subjectId, details, truncated,
                previousHash, hash);
        setId(event);
        return event;
    }

    private String sha256(String input) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
