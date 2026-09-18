package cz.demo.caselaw.justice;

/** Any other failure of the rozhodnuti.justice.cz API - transient, safe for Temporal to retry. */
public class JusticeApiException extends RuntimeException {
    public JusticeApiException(String message) {
        super(message);
    }

    public JusticeApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
