package cz.demo.caselaw.graphql;

import cz.demo.caselaw.ai.ResearchGraphRunner;
import cz.demo.caselaw.domain.CorpusStats;
import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.store.DecisionRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only corpus queries plus the LocalDate to String mappings: the schema exposes dates as
 * plain ISO strings, while the domain records keep LocalDate.
 */
@Controller
public class CorpusController {

    private final DecisionRepository decisions;
    private final ResearchGraphRunner runner;

    public CorpusController(DecisionRepository decisions, ResearchGraphRunner runner) {
        this.decisions = decisions;
        this.runner = runner;
    }

    @QueryMapping
    public CorpusStats corpusStats() {
        return decisions.stats();
    }

    @QueryMapping
    public Decision decision(@Argument String id) {
        try {
            return decisions.findById(UUID.fromString(id)).orElse(null);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Neplatné ID rozhodnutí: " + id);
        }
    }

    @QueryMapping
    public String graphDiagram() {
        return runner.mermaid();
    }

    @SchemaMapping(typeName = "Decision", field = "decidedOn")
    public String decidedOn(Decision decision) {
        return iso(decision.decidedOn());
    }

    @SchemaMapping(typeName = "Decision", field = "publishedOn")
    public String publishedOn(Decision decision) {
        return iso(decision.publishedOn());
    }

    @SchemaMapping(typeName = "CorpusStats", field = "from")
    public String corpusFrom(CorpusStats stats) {
        return iso(stats.from());
    }

    @SchemaMapping(typeName = "CorpusStats", field = "to")
    public String corpusTo(CorpusStats stats) {
        return iso(stats.to());
    }

    private static String iso(LocalDate date) {
        return date == null ? null : date.toString();
    }
}
