package cz.demo.caselaw.temporal;

import cz.demo.caselaw.ai.ResearchOutcome;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * The whole LangGraph4j graph runs inside one activity: the graph has its own control flow and
 * retries, and Temporal only needs to make the expensive LLM run durable and observable.
 * Progress is streamed back into the workflow as signals, not as activity results.
 */
@ActivityInterface
public interface ResearchActivities {

    @ActivityMethod
    ResearchOutcome runResearchGraph(String researchId, String question);
}
