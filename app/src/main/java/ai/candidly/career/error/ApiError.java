package ai.candidly.career.error;

import java.time.Instant;

/**
 * Uniform JSON error body for every response {@link GlobalExceptionHandler} produces,
 * replacing the mix of Spring Boot's default error shape and ad hoc
 * {@code ResponseStatusException} bodies that previously coexisted across controllers.
 */
public record ApiError(Instant timestamp, int status, String error, String message, String path) {
}
