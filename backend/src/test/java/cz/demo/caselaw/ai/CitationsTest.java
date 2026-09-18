package cz.demo.caselaw.ai;

import cz.demo.caselaw.domain.Argument;
import cz.demo.caselaw.domain.Citation;
import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.domain.Hit;
import cz.demo.caselaw.domain.Side;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CitationsTest {

    @Test
    void mergeRenumbersCitationsGloballyAndRemapsArguments() {
        List<Citation> forCitations = List.of(
                citation(1, Side.FOR, "A"),
                citation(2, Side.FOR, "B"));
        List<Citation> againstCitations = List.of(
                citation(1, Side.AGAINST, "C"));
        List<Argument> forArgs = List.of(new Argument("pro", "r", List.of(1, 2)));
        List<Argument> againstArgs = List.of(new Argument("proti", "r", List.of(1)));

        Citations.Merged merged = Citations.merge(forArgs, forCitations, againstArgs, againstCitations);

        assertThat(merged.citations()).extracting(Citation::index).containsExactly(1, 2, 3);
        assertThat(merged.citations()).extracting(Citation::quote).containsExactly("A", "B", "C");
        assertThat(merged.citations()).extracting(Citation::side)
                .containsExactly(Side.FOR, Side.FOR, Side.AGAINST);
        assertThat(merged.forArguments().get(0).citationIndexes()).containsExactly(1, 2);
        assertThat(merged.againstArguments().get(0).citationIndexes()).containsExactly(3);
    }

    @Test
    void mergeDropsDanglingCitationIndexes() {
        Citations.Merged merged = Citations.merge(
                List.of(new Argument("pro", "r", List.of(1, 9))), List.of(citation(1, Side.FOR, "A")),
                List.of(), List.of());
        assertThat(merged.forArguments().get(0).citationIndexes()).containsExactly(1);
    }

    @Test
    void resolvesDecisionIdByCaseNumberWhenModelInventsUuid() {
        UUID real = UUID.randomUUID();
        List<Hit> hits = List.of(new Hit(1, decision(real, "25 Cdo 1/2020"), "úryvek", 1.0, 1.0, 1.0));

        List<Citation> citations = Citations.fromDto(
                List.of(new Dtos.CitationDto(1, "not-a-uuid", "25 Cdo 1/2020", "doslovný úryvek z rozhodnutí")),
                Side.FOR, hits);

        assertThat(citations).hasSize(1);
        assertThat(citations.get(0).decisionId()).isEqualTo(real);
        assertThat(citations.get(0).verified()).isFalse();
    }

    @Test
    void skipsCitationsWithoutQuote() {
        List<Citation> citations = Citations.fromDto(
                List.of(new Dtos.CitationDto(1, null, "sp", "  "),
                        new Dtos.CitationDto(2, null, "sp", "platný úryvek")),
                Side.AGAINST, List.of());
        assertThat(citations).hasSize(1);
        assertThat(citations.get(0).index()).isEqualTo(1);
    }

    @Test
    void extractsJsonFromMarkdownFencesAndChatter() {
        assertThat(JsonText.extractObject("```json\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}");
        assertThat(JsonText.extractObject("Jistě, tady je výsledek:\n{\"a\":{\"b\":2}}\nDoufám, že pomohlo."))
                .isEqualTo("{\"a\":{\"b\":2}}");
        assertThat(JsonText.extractObject("{\"q\":\"text s } uvnitř\"}")).isEqualTo("{\"q\":\"text s } uvnitř\"}");
        assertThat(JsonText.extractObject("bez jsonu")).isNull();
        assertThat(JsonText.extractObject(null)).isNull();
    }

    private static Citation citation(int index, Side side, String quote) {
        return new Citation(index, UUID.randomUUID(), "sp. zn.", quote, false, null, side);
    }

    private static Decision decision(UUID id, String caseNumber) {
        return new Decision(id, "ECLI", caseNumber, "Nejvyšší soud", "NS", LocalDate.now(), LocalDate.now(),
                "subject", List.of(), List.of(), List.of(), "verdikt", "odůvodnění", "url");
    }
}
