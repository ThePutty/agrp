package cz.demo.caselaw.temporal;

import cz.demo.caselaw.ai.GraphEvent;
import cz.demo.caselaw.ai.ResearchGraphRunner;
import cz.demo.caselaw.ai.ResearchOutcome;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import io.temporal.client.WorkflowClient;
import io.temporal.spring.boot.ActivityImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Runs the whole LangGraph4j graph in one activity and streams node updates back into the
 * workflow as signals. The workflow id equals the researchId, so the signal target is known here.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.CASELAW)
public class ResearchActivitiesImpl implements ResearchActivities {

    private static final Logger log = LoggerFactory.getLogger(ResearchActivitiesImpl.class);

    private final ResearchGraphRunner runner;
    private final WorkflowClient client;

    public ResearchActivitiesImpl(ResearchGraphRunner runner, WorkflowClient client) {
        this.runner = runner;
        this.client = client;
    }

    @Override
    public ResearchOutcome runResearchGraph(String researchId, String question) {
        ActivityExecutionContext ctx = Activity.getExecutionContext();
        // A single LLM call can take over a minute and emits no graph events meanwhile,
        // so heartbeat on a timer as well - otherwise Temporal would assume the worker died.
        ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor();
        heartbeats.scheduleAtFixedRate(() -> safeHeartbeat(ctx, "alive"), 20, 20, TimeUnit.SECONDS);
        try {
            return runner.run(researchId, question, event -> publish(ctx, researchId, event));
        } finally {
            heartbeats.shutdownNow();
        }
    }

    private static void safeHeartbeat(ActivityExecutionContext ctx, String details) {
        try {
            ctx.heartbeat(details);
        } catch (Exception e) {
            log.debug("Heartbeat selhal: {}", e.toString());
        }
    }

    private void publish(ActivityExecutionContext ctx, String workflowId, GraphEvent event) {
        try {
            safeHeartbeat(ctx, event.node() == null ? "progress" : event.node().name());
            client.newWorkflowStub(ResearchWorkflow.class, workflowId).nodeUpdate(event);
        } catch (Exception e) {
            // Progress streaming is best effort; the final ResearchOutcome carries the truth.
            log.warn("Nepodařilo se odeslat signál nodeUpdate pro {}: {}", workflowId, e.toString());
        }
    }
}
