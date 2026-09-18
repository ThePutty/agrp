package cz.demo.caselaw.search;

import cz.demo.caselaw.domain.Citation;
import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.domain.Hit;
import cz.demo.caselaw.domain.Side;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultCitationVerifierTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String JUSTIFICATION =
            "Soud dospěl k závěru, že žalovaný se na úkor žalobkyně bezdůvodně obohatil, "
                    + "neboť plnil bez právního důvodu podle § 2991 občanského zákoníku.";

    private final DefaultCitationVerifier verifier = new DefaultCitationVerifier();

    private static Decision decision() {
        return new Decision(ID, "ECLI", "21 C 250/2023-64", "Okresní soud ve Znojmě", "OSZN",
                null, null, null, List.of(), List.of(), List.of(),
                "<b>I. Žaloba se zamítá.</b>", JUSTIFICATION, "https://x");
    }

    private static List<Hit> hits() {
        return List.of(new Hit(1, decision(), "snippet", 0.9, 1.0, 0.5));
    }

    private static Citation citation(UUID decisionId, String quote) {
        return new Citation(1, decisionId, "21 C 250/2023-64", quote, false, null, Side.FOR);
    }

    @Test
    void verifiesExactQuote() {
        List<Citation> out = verifier.verify(List.of(citation(ID, "žalovaný se na úkor žalobkyně bezdůvodně obohatil")), hits());

        assertThat(out.get(0).verified()).isTrue();
        assertThat(out.get(0).reason()).isEqualTo(DefaultCitationVerifier.REASON_OK);
    }

    @Test
    void verifiesQuoteDifferingOnlyInDiacriticsCaseAndWhitespace() {
        List<Citation> out = verifier.verify(List.of(citation(ID, "ZALOVANY   se na ukor  zalobkyne bezduvodne obohatil")), hits());

        assertThat(out.get(0).verified()).isTrue();
        assertThat(out.get(0).reason()).isEqualTo(DefaultCitationVerifier.REASON_OK);
    }

    @Test
    void verifiesQuoteFromTheVerdictIncludingMarkup() {
        // The stored verdict is "<b>I. Žaloba se zamítá.</b>" - markup must not break the match.
        List<Citation> out = verifier.verify(List.of(citation(ID, "I. Žaloba se zamítá.")), hits());

        assertThat(out.get(0).verified()).isTrue();
        assertThat(out.get(0).reason()).isEqualTo(DefaultCitationVerifier.REASON_OK);
    }

    @Test
    void acceptsParaphrasedWordOrderAsFuzzyMatch() {
        // Same significant words, reordered and with an extra short filler word.
        List<Citation> out = verifier.verify(
                List.of(citation(ID, "bezdůvodně obohatil se žalovaný na úkor žalobkyně")), hits());

        assertThat(out.get(0).verified()).isTrue();
        assertThat(out.get(0).reason()).isEqualTo(DefaultCitationVerifier.REASON_FUZZY);
    }

    @Test
    void rejectsShortQuote() {
        List<Citation> out = verifier.verify(List.of(citation(ID, "bezdůvodné")), hits());

        assertThat(out.get(0).verified()).isFalse();
        assertThat(out.get(0).reason()).isEqualTo(DefaultCitationVerifier.REASON_TOO_SHORT);
    }

    @Test
    void rejectsDecisionThatWasNotRetrieved() {
        List<Citation> out = verifier.verify(List.of(citation(OTHER, "žalovaný se na úkor žalobkyně bezdůvodně obohatil")), hits());

        assertThat(out.get(0).verified()).isFalse();
        assertThat(out.get(0).reason()).isEqualTo(DefaultCitationVerifier.REASON_NOT_IN_HITS);
    }

    @Test
    void rejectsInventedQuote() {
        List<Citation> out = verifier.verify(
                List.of(citation(ID, "soud konstatoval promlčení nároku na náhradu nemajetkové újmy")), hits());

        assertThat(out.get(0).verified()).isFalse();
        assertThat(out.get(0).reason()).isEqualTo(DefaultCitationVerifier.REASON_NOT_FOUND);
    }

    @Test
    void handlesEmptyInputs() {
        assertThat(verifier.verify(List.of(), hits())).isEmpty();
        assertThat(verifier.verify(null, hits())).isEmpty();

        List<Citation> out = verifier.verify(List.of(citation(ID, "žalovaný se na úkor žalobkyně bezdůvodně obohatil")), List.of());
        assertThat(out.get(0).reason()).isEqualTo(DefaultCitationVerifier.REASON_NOT_IN_HITS);
    }
}
