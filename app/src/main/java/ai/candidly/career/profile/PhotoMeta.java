package ai.candidly.career.profile;

import java.time.Instant;

/** Metadata-only view of an uploaded profile photo - keeps the bytes out of ordinary JSON responses. */
public record PhotoMeta(String contentType, long sizeBytes, Instant uploadedAt) {
}
