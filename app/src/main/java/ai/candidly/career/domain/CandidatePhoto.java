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
 * The candidate's uploaded profile photo (mockup-driven addition, mirrors
 * {@link CandidateResume}'s storage pattern - bytes in Postgres, one row per candidate,
 * replaced wholesale on re-upload by {@code profile.CandidatePhotoService}).
 * {@code candidateId} is a bare UUID with no FK, same isolation pattern as
 * {@code CandidateResume.candidateId}. Never read by matching/tailoring.
 */
@Entity
@Table(name = "candidate_photo")
public class CandidatePhoto {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID candidateId;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Lob
    @Column(nullable = false)
    private byte[] content;

    @Column(nullable = false)
    private Instant uploadedAt;

    protected CandidatePhoto() {
        // JPA
    }

    public CandidatePhoto(UUID candidateId, String contentType, byte[] content, Instant uploadedAt) {
        this.candidateId = candidateId;
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

    public void replace(String contentType, byte[] content, Instant uploadedAt) {
        this.contentType = contentType;
        this.content = content;
        this.sizeBytes = content.length;
        this.uploadedAt = uploadedAt;
    }
}
