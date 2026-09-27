package ai.candidly.career.error;

/**
 * Wraps a failure talking to Groq, Jina, or TypeSafe (timeout, non-2xx response, or a
 * malformed/empty body) so {@link GlobalExceptionHandler} can map it to a distinct 502
 * instead of letting the raw {@code RestClientException}/{@code IllegalStateException}
 * fall through as an undifferentiated 500.
 */
public class ExternalAiServiceException extends RuntimeException {

    public ExternalAiServiceException(String provider, String message, Throwable cause) {
        super(provider + ": " + message, cause);
    }
}
