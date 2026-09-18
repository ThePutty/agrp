package cz.demo.caselaw.graphql;

import cz.demo.caselaw.domain.Ingest;
import cz.demo.caselaw.domain.JobStatus;
import cz.demo.caselaw.domain.Step;
import cz.demo.caselaw.domain.StepStatus;
import cz.demo.caselaw.temporal.IngestRequest;
import cz.demo.caselaw.temporal.IngestWorkflow;
import cz.demo.caselaw.temporal.TaskQueues;
import cz.demo.caselaw.temporal.TemporalIds;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Controller
public class IngestController {

    private static final List<String> STEP_NAMES =
            List.of("Výpis dnů", "Stahování rozhodnutí", "Chunking a embeddingy", "Uložení");

    private final WorkflowClient client;
    private final int defaultLimit;

    public IngestController(WorkflowClient client, @Value("${app.ingest.default-limit}") int defaultLimit) {
        this.client = client;
        this.defaultLimit = defaultLimit;
    }

    @MutationMapping
    public Ingest startIngest(@Argument String from, @Argument String to, @Argument Integer limit) {
        LocalDate fromDate = parse(from, "from");
        LocalDate toDate = parse(to, "to");
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("Datum 'to' nesmí být před 'from'.");
        }
        int effectiveLimit = limit == null || limit <= 0 ? defaultLimit : limit;

        String ingestId = TemporalIds.ingest();
        IngestWorkflow workflow = client.newWorkflowStub(IngestWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(ingestId)
                .setTaskQueue(TaskQueues.CASELAW)
                .build());
        WorkflowClient.start(workflow::run, new IngestRequest(fromDate, toDate, effectiveLimit));

        return initial(ingestId);
    }

    @QueryMapping
    public Ingest ingest(@Argument String id) {
        try {
            WorkflowStub stub = client.newUntypedWorkflowStub(id);
            var execution = stub.describe().getStatus();
            Ingest state = stub.query("getState", Ingest.class);
            return new Ingest(state.id(), WorkflowStatusMapper.overlay(state.status(), execution), state.requested(),
                    state.fetched(), state.embedded(), state.failed(), state.steps(), state.trace());
        } catch (WorkflowNotFoundException e) {
            return null;
        }
    }

    private static Ingest initial(String ingestId) {
        List<Step> steps = STEP_NAMES.stream()
                .map(name -> new Step(name, StepStatus.PENDING, null, null, null))
                .toList();
        return new Ingest(ingestId, JobStatus.RUNNING, 0, 0, 0, 0, steps, List.of());
    }

    private static LocalDate parse(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Neplatné datum v poli '" + field + "': " + value + " (očekáváno YYYY-MM-DD).");
        }
    }
}
