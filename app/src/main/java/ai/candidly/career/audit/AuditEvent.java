package ai.candidly.career.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One append-only ledger row (docs/02 §3: the transactional-outbox/Kafka ledger, minus
 * Kafka per standing instruction). Written by {@link AuditLedgerService} inside the same
 * DB transaction as the decision it records, so there is no dual-write gap between "the
 * decision happened" and "the ledger says it happened" (docs/02 §3.1) - it's just another
 * row in the same commit, not a separate broker publish that can diverge from it.
 *
 * <p>{@code previousHash}/{@code hash} form a SHA-256 hash chain (docs/02 §3.4,
 * "tamper-evident audit... hash-chaining application_event rows"): each row's hash covers
 * its own fields plus the previous row's hash, so altering or deleting a historical row
 * breaks every hash after it. {@link AuditLedgerService#verifyChain()} recomputes the
 * chain to detect exactly that.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditEventType eventType;

    /** The primary subject the event is about - typically a candidateId or jobPostingId. */
    @Column(nullable = false)
    private UUID subjectId;

    /** Human-readable decision inputs/score components/reason codes, one line per fact. */
    @Column(nullable = false, columnDefinition = "text")
    private String details;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 64)
    private String previousHash;

    @Column(nullable = false, length = 64)
    private String hash;

    protected AuditEvent() {
        // JPA
    }

    public AuditEvent(AuditEventType eventType, UUID subjectId, String details, Instant occurredAt,
            String previousHash, String hash) {
        this.eventType = eventType;
        this.subjectId = subjectId;
        this.details = details;
        this.occurredAt = occurredAt;
        this.previousHash = previousHash;
        this.hash = hash;
    }

    public UUID getId() {
        return id;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public String getDetails() {
        return details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getHash() {
        return hash;
    }
}
