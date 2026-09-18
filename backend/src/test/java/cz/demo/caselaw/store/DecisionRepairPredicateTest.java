package cz.demo.caselaw.store;

import cz.demo.caselaw.domain.Decision;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Which stored rows a re-ingest must re-fetch (see DecisionRepository#needsRepair). */
class DecisionRepairPredicateTest {

    private static Decision with(String court, List<String> keywords) {
        return new Decision(UUID.randomUUID(), "ECLI", "1 C 1/2023", court, "OSTR",
                LocalDate.of(2023, 11, 30), LocalDate.of(2024, 3, 1), "předmět",
                keywords, List.of("§ 2910 z. č. 89/2012 Sb."), List.of("ZAMITNUTI"),
                "I. Žaloba se zamítá.", "1. Odůvodnění.", "https://x");
    }

    @Test
    void bareCourtCodesAreBroken() {
        for (String code : List.of("OSTR", "MSBR", "OSPH04", "NS", "KSOS")) {
            assertThat(code).matches(DecisionRepository.BARE_COURT_CODE);
            assertThat(DecisionRepository.looksIncomplete(with(code, List.of("smlouva")))).isTrue();
        }
    }

    @Test
    void emptyKeywordsAreBroken() {
        assertThat(DecisionRepository.looksIncomplete(with("Okresní soud v Ostravě", List.of()))).isTrue();
        assertThat(DecisionRepository.looksIncomplete(with("", List.of("smlouva")))).isTrue();
        assertThat(DecisionRepository.looksIncomplete(null)).isTrue();
    }

    @Test
    void completeRowsAreKept() {
        assertThat(DecisionRepository.looksIncomplete(with("Okresní soud v Ostravě", List.of("smlouva")))).isFalse();
        assertThat(DecisionRepository.looksIncomplete(with("Nejvyšší soud", List.of("dovolání")))).isFalse();
    }

    @Test
    void czechCourtNamesAreNotMistakenForCodes() {
        assertThat("Okresní soud v Ostravě").doesNotMatch(DecisionRepository.BARE_COURT_CODE);
        assertThat("Nejvyšší soud").doesNotMatch(DecisionRepository.BARE_COURT_CODE);
    }
}
