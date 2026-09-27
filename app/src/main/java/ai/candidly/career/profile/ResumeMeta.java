package ai.candidly.career.profile;

import java.time.Instant;

/** Metadata-only view of an uploaded resume - keeps the bytes out of ordinary JSON responses. */
public record ResumeMeta(String filename, String contentType, long sizeBytes, Instant uploadedAt) {
}
