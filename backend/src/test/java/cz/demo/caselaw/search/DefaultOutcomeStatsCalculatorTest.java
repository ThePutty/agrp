package cz.demo.caselaw.search;

import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.domain.OutcomeBucket;
import cz.demo.caselaw.domain.OutcomeCategory;
import cz.demo.caselaw.domain.OutcomeStats;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DefaultOutcomeStatsCalculatorTest {

    private final DefaultOutcomeStatsCalculator calculator = new DefaultOutcomeStatsCalculator();

    private static Decision decision(String court, int year, String... resultTypes) {
        return new Decision(UUID.randomUUID(), null, "1 C 1/2023", court, null,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 2, 1), null,
                List.of(), List.of(), List.of(resultTypes), "v", "j", "https://x");
    }

    private static double share(OutcomeStats stats, OutcomeCategory category) {
        return stats.buckets().stream().filter(b -> b.category() == category)
                .mapToDouble(OutcomeBucket::share).findFirst().orElseThrow();
    }

    @Test
    void mapsRealApiCodes() {
        assertThat(DefaultOutcomeStatsCalculator.categorize(List.of("VYHOVENI"))).isEqualTo(OutcomeCategory.GRANTED);
        assertThat(DefaultOutcomeStatsCalculator.categorize(List.of("ZAMITNUTI"))).isEqualTo(OutcomeCategory.DISMISSED);
        assertThat(DefaultOutcomeStatsCalculator.categorize(List.of("ZASTAVENI"))).isEqualTo(OutcomeCategory.OTHER);
        assertThat(DefaultOutcomeStatsCalculator.categorize(List.of("ZMENA"))).isEqualTo(OutcomeCategory.OTHER);
        assertThat(DefaultOutcomeStatsCalculator.categorize(List.of("POTVRZENI"))).isEqualTo(OutcomeCategory.OTHER);
        assertThat(DefaultOutcomeStatsCalculator.categorize(List.of())).isEqualTo(OutcomeCategory.OTHER);
        // A partly successful claim is published as both codes at once.
        assertThat(DefaultOutcomeStatsCalculator.categorize(List.of("VYHOVENI", "ZAMITNUTI")))
                .isEqualTo(OutcomeCategory.PARTIALLY_GRANTED);
    }

    @Test
    void mapsCzechWordingsToo() {
        assertThat(DefaultOutcomeStatsCalculator.categorize(List.of("vyhověno"))).isEqualTo(OutcomeCategory.GRANTED);
        assertThat(DefaultOutcomeStatsCalculator.categorize(List.of("částečně vyhověno"))).isEqualTo(OutcomeCategory.PARTIALLY_GRANTED);
        assertThat(DefaultOutcomeStatsCalculator.categorize(List.of("zamítnut"))).isEqualTo(OutcomeCategory.DISMISSED);
        assertThat(DefaultOutcomeStatsCalculator.categorize(List.of("odmítnuto"))).isEqualTo(OutcomeCategory.OTHER);
    }

    @Test
    void derivesCourtLevelFromName() {
        assertThat(DefaultOutcomeStatsCalculator.courtLevel("Okresní soud ve Znojmě")).isEqualTo("okresní");
        assertThat(DefaultOutcomeStatsCalculator.courtLevel("Obvodní soud pro Prahu 4")).isEqualTo("okresní");
        assertThat(DefaultOutcomeStatsCalculator.courtLevel("Městský soud v Brně")).isEqualTo("okresní");
        assertThat(DefaultOutcomeStatsCalculator.courtLevel("Městský soud v Praze")).isEqualTo("krajský");
        assertThat(DefaultOutcomeStatsCalculator.courtLevel("Krajský soud v Brně")).isEqualTo("krajský");
        assertThat(DefaultOutcomeStatsCalculator.courtLevel("Vrchní soud v Olomouci")).isEqualTo("vrchní");
        assertThat(DefaultOutcomeStatsCalculator.courtLevel("Nejvyšší soud")).isEqualTo("jiný");
        assertThat(DefaultOutcomeStatsCalculator.courtLevel(null)).isEqualTo("jiný");
    }

    @Test
    void aggregatesSharesByCategoryCourtLevelAndYear() {
        OutcomeStats stats = calculator.compute(List.of(
                decision("Okresní soud ve Znojmě", 2023, "VYHOVENI"),
                decision("Okresní soud ve Znojmě", 2023, "VYHOVENI", "ZAMITNUTI"),
                decision("Krajský soud v Brně", 2024, "ZAMITNUTI"),
                decision("Krajský soud v Brně", 2024, "ZASTAVENI")));

        assertThat(stats.sampleSize()).isEqualTo(4);
        assertThat(share(stats, OutcomeCategory.GRANTED)).isCloseTo(0.25, within(1e-9));
        assertThat(share(stats, OutcomeCategory.PARTIALLY_GRANTED)).isCloseTo(0.25, within(1e-9));
        assertThat(share(stats, OutcomeCategory.DISMISSED)).isCloseTo(0.25, within(1e-9));
        assertThat(share(stats, OutcomeCategory.OTHER)).isCloseTo(0.25, within(1e-9));

        assertThat(stats.byCourtLevel()).hasSize(2);
        assertThat(stats.byCourtLevel()).anySatisfy(b -> {
            assertThat(b.label()).isEqualTo("okresní");
            assertThat(b.sampleSize()).isEqualTo(2);
            assertThat(b.grantedShare()).isCloseTo(0.75, within(1e-9));   // (1 + 0.5) / 2
        });
        assertThat(stats.byYear()).extracting("label").containsExactly("2023", "2024");
        assertThat(stats.note()).isEqualTo(
                "Statistika z 4 podobných rozhodnutí krajských a okresních soudů; orientační, není právní rada.");
    }

    @Test
    void handlesEmptySample() {
        OutcomeStats stats = calculator.compute(List.of());

        assertThat(stats.sampleSize()).isZero();
        assertThat(stats.buckets()).isEmpty();
        assertThat(stats.note()).contains("Nenalezena");
    }
}
