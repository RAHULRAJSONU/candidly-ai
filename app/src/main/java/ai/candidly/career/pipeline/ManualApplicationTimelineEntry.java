package ai.candidly.career.pipeline;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One timestamped note on a {@link ManualApplication} - the mock's per-application "Timeline" tab. */
@Entity
@Table(name = "manual_application_timeline_entry")
public class ManualApplicationTimelineEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String note;

    @Column(nullable = false)
    private Instant occurredAt;

    protected ManualApplicationTimelineEntry() {
        // JPA
    }

    public ManualApplicationTimelineEntry(String note, Instant occurredAt) {
        this.note = note;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public String getNote() {
        return note;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
