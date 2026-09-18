package cz.demo.caselaw.graphql;

import cz.demo.caselaw.ai.ResearchGraphRunner;
import cz.demo.caselaw.domain.GraphView;
import cz.demo.caselaw.domain.JobStatus;
import cz.demo.caselaw.domain.Research;
import cz.demo.caselaw.temporal.ResearchStateMerger;
import cz.demo.caselaw.temporal.ResearchWorkflow;
import cz.demo.caselaw.temporal.TaskQueues;
import cz.demo.caselaw.temporal.TemporalIds;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class ResearchController {

    private static final int MAX_QUESTION_LENGTH = 2000;

    private final WorkflowClient client;
    private final ResearchGraphRunner runner;

    public ResearchController(WorkflowClient client, ResearchGraphRunner runner) {
        this.client = client;
        this.runner = runner;
    }

    /** Starts the workflow and returns immediately; the UI then polls research(id). */
    @MutationMapping
    public Research ask(@Argument String question) {
        String q = question == null ? "" : question.trim();
        if (q.isBlank()) {
            throw new IllegalArgumentException("Dotaz nesmí být prázdný.");
        }
        if (q.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("Dotaz je delší než " + MAX_QUESTION_LENGTH + " znaků.");
        }

        String researchId = TemporalIds.research();
        ResearchWorkflow workflow = client.newWorkflowStub(ResearchWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(researchId)
                .setTaskQueue(TaskQueues.CASELAW)
                .build());
        WorkflowClient.start(workflow::run, researchId, q);

        return ResearchStateMerger.initial(researchId, q, runner.mermaid());
    }

    @QueryMapping
    public Research research(@Argument String id) {
        try {
            WorkflowStub stub = client.newUntypedWorkflowStub(id);
            var execution = stub.describe().getStatus();
            // Querying a finished workflow is fine: Temporal replays its history to answer.
            Research state = stub.query("getState", Research.class);
            return withMermaid(state, WorkflowStatusMapper.overlay(state.status(), execution));
        } catch (WorkflowNotFoundException e) {
            return null;
        }
    }

    /** The workflow cannot call beans, so the static graph diagram is attached here. */
    private Research withMermaid(Research state, JobStatus status) {
        GraphView graph = state.graph();
        if (graph == null || graph.mermaid() == null || graph.mermaid().isBlank()) {
            graph = new GraphView(runner.mermaid(), graph == null ? java.util.List.of() : graph.nodes());
        }
        return new Research(state.id(), state.question(), status, state.steps(), state.analysis(), state.hits(),
                state.answer(), state.outcome(), graph, state.trace());
    }
}
