package cz.demo.caselaw.ai;

import java.util.function.Consumer;

/**
 * Runs the LangGraph4j research graph for one question.
 * Called from the Temporal activity `runResearchGraph`.
 */
public interface ResearchGraphRunner {
    ResearchOutcome run(String researchId, String question, Consumer<GraphEvent> listener);

    /** Mermaid diagram of the compiled graph (LangGraph4j getGraph(MERMAID)). Static, used by the UI. */
    String mermaid();
}
