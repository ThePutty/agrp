package cz.demo.caselaw.ai;

import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.store.DecisionRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * The prompt context is capped at ~1200 characters of each justification. When that is not enough
 * to find a literal quote, the model can pull more text itself - LangChain4j drives the tool loop.
 * The tool is read-only and bound to the argument services only (see LlmServices).
 */
@Component
public class CaseLawTools {

    private static final Logger log = LoggerFactory.getLogger(CaseLawTools.class);
    private static final int EXCERPT_CHARS = 3_000;

    private final DecisionRepository decisions;

    public CaseLawTools(DecisionRepository decisions) {
        this.decisions = decisions;
    }

    @Tool("Vrátí delší úryvek textu rozhodnutí podle decisionId. Použij, když potřebuješ najít doslovnou citaci.")
    public String decisionDetail(@P("decisionId") String decisionId) {
        try {
            Optional<Decision> found = decisions.findById(UUID.fromString(decisionId.trim()));
            if (found.isEmpty()) {
                return "Rozhodnutí s decisionId=" + decisionId + " v korpusu neexistuje.";
            }
            Decision d = found.get();
            return """
                    sp. zn. %s, %s, %s
                    %s
                    """.formatted(d.caseNumber(), d.court(), d.decidedOn(),
                    Prompts.cut(d.fullText(), EXCERPT_CHARS));
        } catch (IllegalArgumentException e) {
            return "decisionId není platné UUID: " + decisionId;
        } catch (RuntimeException e) {
            log.debug("decisionDetail failed", e);
            return "Text rozhodnutí se nepodařilo načíst.";
        }
    }
}
