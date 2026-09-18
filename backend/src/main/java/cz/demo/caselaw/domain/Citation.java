package cz.demo.caselaw.domain;

import java.util.UUID;

/**
 * A citation produced by the LLM and checked by the deterministic CitationVerifier.
 * verified=false + reason means the model cited something that does not exist in the corpus.
 */
public record Citation(int index, UUID decisionId, String caseNumber, String quote, boolean verified, String reason, Side side) {
    public Citation withVerification(boolean ok, String why) {
        return new Citation(index, decisionId, caseNumber, quote, ok, why, side);
    }
}
