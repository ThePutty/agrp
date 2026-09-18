package cz.demo.caselaw.justice;

import java.util.UUID;

/**
 * The finaldoc endpoint returned 404 - the decision does not exist (or was withdrawn).
 * Retrying cannot help, so Temporal activities should treat this as non-retryable.
 */
public class DecisionNotFoundException extends RuntimeException {
    public DecisionNotFoundException(UUID id) {
        super("Rozhodnutí " + id + " nebylo nalezeno (HTTP 404)");
    }
}
