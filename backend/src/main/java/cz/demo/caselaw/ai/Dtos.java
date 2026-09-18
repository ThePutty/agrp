package cz.demo.caselaw.ai;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

/**
 * What the LLM is asked to produce. These are deliberately separate from the domain records:
 * the model returns free-form strings (a decisionId may be garbage), the mapping to domain types
 * is where we validate and re-index.
 */
public final class Dtos {

    private Dtos() {
    }

    public record QueryAnalysisDto(
            @Description("3-6 právních pojmů z otázky, česky") List<String> legalConcepts,
            @Description("ustanovení ve tvaru '§ 2913' nebo '§ 11 odst. 1'") List<String> provisions,
            @Description("2-4 vyhledávací dotazy pro fulltext a vektorové hledání") List<String> searchQueries) {
    }

    public record ArgumentDto(
            @Description("stručné tvrzení, jedna věta") String claim,
            @Description("odůvodnění tvrzení, 1-3 věty") String reasoning,
            @Description("indexy citací z pole citations, které tvrzení dokládají") List<Integer> citationIndexes) {
    }

    public record CitationDto(
            @Description("pořadové číslo citace, od 1") int index,
            @Description("decisionId (UUID) přesně tak, jak je uvedeno u rozhodnutí") String decisionId,
            @Description("spisová značka přesně tak, jak je uvedena") String caseNumber,
            @Description("doslovný úryvek z textu rozhodnutí, 25-300 znaků") String quote) {
    }

    public record ArgumentsDto(
            @Description("2-4 argumenty") List<ArgumentDto> arguments,
            @Description("citace dokládající argumenty") List<CitationDto> citations) {
    }

    public record JudgmentDto(
            @Description("verdikt česky, 3-6 vět") String verdict,
            @Description("silnější strana: FOR, AGAINST nebo BALANCED") String strongerSide) {
    }
}
