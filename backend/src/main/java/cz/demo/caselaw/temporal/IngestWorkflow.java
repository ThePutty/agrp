package cz.demo.caselaw.temporal;

import cz.demo.caselaw.domain.Ingest;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface IngestWorkflow {

    @WorkflowMethod
    Ingest run(IngestRequest req);

    /** The UI polls this while the job runs; Temporal replays state for finished workflows too. */
    @QueryMethod
    Ingest getState();
}
