package cz.demo.caselaw.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** One court decision from rozhodnuti.justice.cz (metadata + full text). */
public record Decision(
        UUID id,
        String ecli,
        String caseNumber,
        String court,
        String courtCode,
        LocalDate decidedOn,
        LocalDate publishedOn,
        String subject,
        List<String> keywords,
        List<String> provisions,
        List<String> resultTypes,
        String verdictText,
        String justificationText,
        String sourceUrl
) {
    /** Text used for citation verification: verdict + justification. */
    public String fullText() {
        return (verdictText == null ? "" : verdictText) + "\n" + (justificationText == null ? "" : justificationText);
    }
}
