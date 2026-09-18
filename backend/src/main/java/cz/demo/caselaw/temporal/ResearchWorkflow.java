package cz.demo.caselaw.temporal;

import cz.demo.caselaw.ai.GraphEvent;
import cz.demo.caselaw.domain.Research;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Signal + query instead of a websocket: the activity signals every finished graph node into the
 * workflow, the workflow keeps the merged state, and the GraphQL layer queries it. Nothing is lost
 * if the API pod restarts mid-run.
 */
@WorkflowInterface
public interface ResearchWorkflow {

    @WorkflowMethod
    Research run(String researchId, String question);

    @QueryMethod
    Research getState();

    @SignalMethod
    void nodeUpdate(GraphEvent event);
}
