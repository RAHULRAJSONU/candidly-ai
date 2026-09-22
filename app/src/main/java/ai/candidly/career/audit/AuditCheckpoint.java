package ai.candidly.career.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Marks where {@link AuditLedgerService#purgeOlderThan} last cut the ledger (docs/02 §2
 * "storage limitation... set TTLs" vs. §3.4/§5's tamper-evident, append-only chain - the
 * same kind of retention-vs-immutability tension {@code CandidateDataSubjectService}
 * documents for GDPR erasure, resolved the same way: the older rows are gone, but the
 * chain stays verifiable from here forward). {@code hash} is the last purged row's own
 * hash - once purged rows are deleted, the oldest surviving row's {@code previousHash}
 * points at a hash with no row behind it in the table, so {@link
 * AuditLedgerService#verifyChain} needs this row to know that's expected, not tampering.
 *
 * <p>One row per purge run, kept forever (this table is tiny and append-only, same as
 * {@code audit_event} itself) so the full purge history is auditable too.
 */
@Entity
@Table(name = "audit_checkpoint")
public class AuditCheckpoint {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 64)
    private String hash;

    @Column(nullable = false)
    private Instant purgedThroughOccurredAt;

    @Column(nullable = false)
    private int purgedRowCount;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditCheckpoint() {
        // JPA
    }

    public AuditCheckpoint(String hash, Instant purgedThroughOccurredAt, int purgedRowCount) {
        this.hash = hash;
        this.purgedThroughOccurredAt = purgedThroughOccurredAt;
        this.purgedRowCount = purgedRowCount;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getHash() {
        return hash;
    }

    public Instant getPurgedThroughOccurredAt() {
        return purgedThroughOccurredAt;
    }

    public int getPurgedRowCount() {
        return purgedRowCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
