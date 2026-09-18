package cz.demo.caselaw.temporal;

import cz.demo.caselaw.ai.GraphEvent;
import cz.demo.caselaw.ai.ResearchOutcome;
import cz.demo.caselaw.domain.Research;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.Map;

/**
 * Durable wrapper around the LangGraph4j run. Not a Spring bean: Temporal instantiates one instance
 * per execution, so the only dependency allowed here is an activity stub.
 */
@WorkflowImpl(taskQueues = TaskQueues.CASELAW)
public class ResearchWorkflowImpl implements ResearchWorkflow {

    private static final ActivityOptions GRAPH_OPTIONS = ActivityOptions.newBuilder()
            // The whole AI graph (5 LLM calls on a free model, possibly a retry loop) runs in one activity.
            .setStartToCloseTimeout(Duration.ofMinutes(10))
            .setHeartbeatTimeout(Duration.ofSeconds(120))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(2))
                    .setBackoffCoefficient(2.0)
                    .setMaximumAttempts(2)
                    .build())
            .build();

    private final ResearchActivities activities = Workflow.newActivityStub(ResearchActivities.class, GRAPH_OPTIONS);

    private Research state = ResearchStateMerger.initial("", "", "");
    private Map<String, Long> stepStarts = Map.of();

    @Override
    public Research run(String researchId, String question) {
        // mermaid is filled by the GraphQL layer: the workflow cannot call ResearchGraphRunner.
        state = ResearchStateMerger.initial(researchId, question, "");
        try {
            ResearchOutcome outcome = activities.runResearchGraph(researchId, question);
            state = ResearchStateMerger.complete(state, outcome);
        } catch (Exception e) {
            // A failed AI run is a business result, not a workflow crash: the UI shows what got done.
            state = ResearchStateMerger.fail(state, rootMessage(e));
        }
        return state;
    }

    @Override
    public Research getState() {
        return state;
    }

    @Override
    public void nodeUpdate(GraphEvent event) {
        ResearchStateMerger.MergeResult result =
                ResearchStateMerger.merge(state, event, Workflow.currentTimeMillis(), stepStarts);
        state = result.state();
        stepStarts = result.stepStarts();
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage();
        return msg == null || msg.isBlank() ? cause.getClass().getSimpleName() : msg;
    }
}
