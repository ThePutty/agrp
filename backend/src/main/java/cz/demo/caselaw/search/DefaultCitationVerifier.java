package cz.demo.caselaw.search;

import cz.demo.caselaw.domain.Citation;
import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.domain.Hit;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic anti-hallucination check. The LLM may only cite decisions that the retrieval
 * step actually returned, and only text that really occurs in them - this class decides, never
 * the model, which is why it contains no scoring, no thresholds beyond the two constants below,
 * and no calls out to anything.
 *
 * <p>An exact normalized substring match is the primary test. Models often paraphrase whitespace,
 * ellipses or an inflected word inside an otherwise genuine quote, so a word-overlap fallback
 * accepts a quote whose significant words are all but 10% present in the decision; such a citation
 * is still marked verified, but the reason says "fuzzy match" so the UI can show it differently.
 */
@Component
public class DefaultCitationVerifier implements CitationVerifier {

    /** Shorter quotes carry no evidential value and match almost any text by accident. */
    static final int MIN_QUOTE_LENGTH = 20;
    /** Share of the quote's significant words that must occur in the decision for the fuzzy fallback. */
    static final double FUZZY_THRESHOLD = 0.9;
    private static final int SIGNIFICANT_WORD_LENGTH = 4;

    static final String REASON_OK = "ověřeno v textu rozhodnutí";
    static final String REASON_FUZZY = "fuzzy match";
    static final String REASON_TOO_SHORT = "quote too short";
    static final String REASON_NOT_IN_HITS = "decision not among retrieved results";
    static final String REASON_NOT_FOUND = "quote not found in decision text";

    @Override
    public List<Citation> verify(List<Citation> citations, List<Hit> hits) {
        if (citations == null || citations.isEmpty()) return List.of();

        Map<UUID, String> textByDecision = new LinkedHashMap<>();
        if (hits != null) {
            for (Hit hit : hits) {
                Decision d = hit == null ? null : hit.decision();
                if (d != null) textByDecision.put(d.id(), TextNormalizer.normalize(d.fullText()));
            }
        }

        return citations.stream().map(citation -> verifyOne(citation, textByDecision)).toList();
    }

    private static Citation verifyOne(Citation citation, Map<UUID, String> textByDecision) {
        if (citation == null) return null;
        String quote = citation.quote() == null ? "" : citation.quote().trim();
        if (quote.length() < MIN_QUOTE_LENGTH) return citation.withVerification(false, REASON_TOO_SHORT);

        String text = citation.decisionId() == null ? null : textByDecision.get(citation.decisionId());
        if (text == null) return citation.withVerification(false, REASON_NOT_IN_HITS);

        String normalizedQuote = TextNormalizer.normalize(quote);
        if (!normalizedQuote.isEmpty() && text.contains(normalizedQuote)) {
            return citation.withVerification(true, REASON_OK);
        }
        if (fuzzyMatches(normalizedQuote, text)) {
            return citation.withVerification(true, REASON_FUZZY);
        }
        return citation.withVerification(false, REASON_NOT_FOUND);
    }

    /** Order-insensitive overlap of the quote's significant (>= 4 chars) words with the decision text. */
    private static boolean fuzzyMatches(String normalizedQuote, String normalizedText) {
        Set<String> quoteWords = significantWords(normalizedQuote);
        if (quoteWords.isEmpty()) return false;
        Set<String> textWords = significantWords(normalizedText);
        long present = quoteWords.stream().filter(textWords::contains).count();
        return (double) present / quoteWords.size() >= FUZZY_THRESHOLD;
    }

    private static Set<String> significantWords(String normalized) {
        return Arrays.stream(normalized.split("[^\\p{L}\\p{Nd}]+"))
                .filter(w -> w.length() >= SIGNIFICANT_WORD_LENGTH)
                .collect(HashSet::new, Set::add, Set::addAll);
    }
}
