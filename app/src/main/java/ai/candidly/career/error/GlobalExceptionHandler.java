package ai.candidly.career.error;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import ai.candidly.career.settings.CandidateSettingsService.AiFeatureDisabledException;

/**
 * Single point mapping every exception a controller can throw to a consistent
 * {@link ApiError} body. Before this existed, each controller hand-translated a subset
 * of exceptions to {@code ResponseStatusException} locally (see CLAUDE.md's "review
 * error handling" pass); anything not explicitly caught fell through to Spring Boot's
 * default, differently-shaped error body. This does not change status-code choices
 * already made at call sites via {@code ResponseStatusException} - it only makes the
 * *uncaught* paths (AI client failures, bad input, unexpected bugs) behave the same way.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex, WebRequest request) {
        HttpStatusCode status = ex.getStatusCode();
        if (status.is5xxServerError()) {
            log.error("Request failed: {} {}", status, ex.getReason(), ex);
        } else {
            log.warn("Request rejected: {} {}", status, ex.getReason());
        }
        return build(status, ex.getReason() == null ? status.toString() : ex.getReason(), request);
    }

    @ExceptionHandler(AiFeatureDisabledException.class)
    public ResponseEntity<ApiError> handleFeatureDisabled(AiFeatureDisabledException ex, WebRequest request) {
        log.warn("AI feature disabled: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(ExternalAiServiceException.class)
    public ResponseEntity<ApiError> handleExternalAiService(ExternalAiServiceException ex, WebRequest request) {
        log.error("External AI service call failed: {}", ex.getMessage(), ex);
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        log.warn("Validation failed: {}", message);
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, WebRequest request) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        log.warn("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex, WebRequest request) {
        log.warn("Conflicting state: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiError> build(HttpStatusCode status, String message, WebRequest request) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String reason = resolved != null ? resolved.getReasonPhrase() : status.toString();
        String path = request.getDescription(false).replaceFirst("^uri=", "");
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), reason, message, path));
    }
}
