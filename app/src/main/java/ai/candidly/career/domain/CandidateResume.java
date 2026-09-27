package ai.candidly.career.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * The candidate's uploaded resume file (mockup-driven addition, no direct docs/00-04
 * requirement), stored as bytes in Postgres rather than an external object store - fine at
 * this stage's scale (see CLAUDE.md's Postgres-credentials note on not carrying local-only
 * patterns into a real deployment). One row per candidate, replaced wholesale on re-upload
 * by CandidateResumeService. {@code candidateId} is a bare UUID with no FK, same isolation
 * pattern as AuditEvent.subjectId/CandidateDemographics - this is a distinct, purely
 * candidate-facing artifact, never read by matching/tailoring (those are grounded against
 * Career Vault records, not an uploaded file - see DeterministicGroundingVerifier).
 */
@Entity
@Table(name = "candidate_resume")
public class CandidateResume {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID candidateId;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Lob
    @Column(nullable = false)
    private byte[] content;

    @Column(nullable = false)
    private Instant uploadedAt;

    protected CandidateResume() {
        // JPA
    }

    public CandidateResume(UUID candidateId, String filename, String contentType, byte[] content, Instant uploadedAt) {
        this.candidateId = candidateId;
        this.filename = filename;
        this.contentType = contentType;
        this.content = content;
        this.sizeBytes = content.length;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public byte[] getContent() {
        return content;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void replace(String filename, String contentType, byte[] content, Instant uploadedAt) {
        this.filename = filename;
        this.contentType = contentType;
        this.content = content;
        this.sizeBytes = content.length;
        this.uploadedAt = uploadedAt;
    }
}
