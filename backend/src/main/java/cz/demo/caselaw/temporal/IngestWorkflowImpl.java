package cz.demo.caselaw.temporal;

import cz.demo.caselaw.domain.Ingest;
import cz.demo.caselaw.domain.JobStatus;
import cz.demo.caselaw.domain.Step;
import cz.demo.caselaw.domain.StepStatus;
import cz.demo.caselaw.domain.TraceEntry;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Crawls justice.cz day by day and embeds every decision. Temporal owns the retries and the
 * progress state, so a rate-limited or restarted ingest continues instead of starting over.
 */
@WorkflowImpl(taskQueues = TaskQueues.CASELAW)
public class IngestWorkflowImpl implements IngestWorkflow {

    private static final int MAX_DAYS = 31;
    private static final int LIST_PARALLELISM = 3;
    private static final int INGEST_BATCH = 5;

    private static final String STEP_LIST = "Výpis dnů";
    private static final String STEP_FETCH = "Stahování rozhodnutí";
    private static final String STEP_EMBED = "Chunking a embeddingy";
    private static final String STEP_STORE = "Uložení";

    private static final ActivityOptions LIST_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(60))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(10))
                    .setBackoffCoefficient(2.0)
                    .setMaximumAttempts(5)
                    .build())
            .build();

    private static final ActivityOptions CHECK_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setBackoffCoefficient(2.0)
                    .setMaximumAttempts(3)
                    .build())
            .build();

    private static final ActivityOptions INGEST_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(180))
            .setHeartbeatTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(2))
                    .setBackoffCoefficient(2.0)
                    .setMaximumAttempts(5)
                    // A decision that 404s will never appear; retrying it only burns the rate limit.
                    .setDoNotRetry("DecisionNotFound")
                    .build())
            .build();

    private final IngestActivities listing = Workflow.newActivityStub(IngestActivities.class, LIST_OPTIONS);
    private final IngestActivities checking = Workflow.newActivityStub(IngestActivities.class, CHECK_OPTIONS);
    private final IngestActivities ingesting = Workflow.newActivityStub(IngestActivities.class, INGEST_OPTIONS);

    private String id = "";
    private JobStatus status = JobStatus.RUNNING;
    private int requested;
    private int fetched;
    private int embedded;
    private int failed;
    private int skipped;
    private final List<Step> steps = new ArrayList<>(List.of(
            pending(STEP_LIST), pending(STEP_FETCH), pending(STEP_EMBED), pending(STEP_STORE)));
    private final List<TraceEntry> trace = new ArrayList<>();

    @Override
    public Ingest run(IngestRequest req) {
        id = Workflow.getInfo().getWorkflowId();
        List<LocalDate> days = days(req.from(), req.to());
        int limit = req.limit() > 0 ? req.limit() : Integer.MAX_VALUE;

        List<DecisionRef> refs = listIds(days, limit);
        requested = refs.size();

        ingestAll(refs);

        status = JobStatus.COMPLETED;
        return getState();
    }

    @Override
    public Ingest getState() {
        return new Ingest(id, status, requested, fetched, embedded, failed, List.copyOf(steps), List.copyOf(trace));
    }

    /** Lists ids day by day (3 days in flight) and stops as soon as the requested limit is covered. */
    private List<DecisionRef> listIds(List<LocalDate> days, int limit) {
        long start = Workflow.currentTimeMillis();
        setStep(0, StepStatus.RUNNING, null, null);
        Set<UUID> seen = new LinkedHashSet<>();
        List<DecisionRef> refs = new ArrayList<>();
        int listedDays = 0;

        for (int i = 0; i < days.size() && refs.size() < limit; i += LIST_PARALLELISM) {
            List<LocalDate> batch = days.subList(i, Math.min(i + LIST_PARALLELISM, days.size()));
            List<Promise<List<UUID>>> promises = new ArrayList<>();
            for (LocalDate day : batch) {
                promises.add(Async.function(listing::listDecisionIds, day));
            }
            Promise.allOf(promises).get();
            for (int j = 0; j < promises.size(); j++) {
                LocalDate day = batch.get(j);
                listedDays++;
                for (UUID uuid : promises.get(j).get()) {
                    if (refs.size() >= limit) {
                        break;
                    }
                    // The day the id was listed under travels with it to the ingest activity.
                    if (seen.add(uuid)) {
                        refs.add(new DecisionRef(uuid, day));
                    }
                }
            }
        }

        int ms = (int) (Workflow.currentTimeMillis() - start);
        setStep(0, StepStatus.DONE, ms, listedDays + " dnů, " + refs.size() + " rozhodnutí");
        trace.add(new TraceEntry("justice.cz", "Výpis rozhodnutí podle dne", StepStatus.DONE, ms,
                listedDays + " dnů, " + refs.size() + " ID"));
        return refs;
    }

    private void ingestAll(List<DecisionRef> refs) {
        long start = Workflow.currentTimeMillis();
        setStep(1, StepStatus.RUNNING, null, null);
        setStep(2, StepStatus.RUNNING, null, null);
        setStep(3, StepStatus.RUNNING, null, null);
        int chunks = 0;

        for (int i = 0; i < refs.size(); i += INGEST_BATCH) {
            List<DecisionRef> batch = refs.subList(i, Math.min(i + INGEST_BATCH, refs.size()));
            // Up to INGEST_BATCH decisions in flight: enough to hide latency, gentle on the rate limit.
            List<Promise<IngestOneResult>> promises = new ArrayList<>();
            for (DecisionRef ref : notYetIngested(batch)) {
                promises.add(Async.function(ingesting::ingestDecision, ref.id(), ref.publishedOn()));
            }
            for (Promise<IngestOneResult> promise : promises) {
                chunks += collect(promise);
            }
        }

        int ms = (int) (Workflow.currentTimeMillis() - start);
        StepStatus outcome = failed > 0 && fetched == 0 ? StepStatus.FAILED : StepStatus.DONE;
        setStep(1, outcome, ms, fetched + " staženo, " + skipped + " přeskočeno, " + failed + " chyb");
        setStep(2, outcome, ms, chunks + " chunků, " + embedded + " embeddingů");
        setStep(3, outcome, ms, fetched + " rozhodnutí uloženo");
        trace.add(new TraceEntry("Temporal", "Paralelní ingest (dávky po " + INGEST_BATCH + ")", outcome, ms,
                requested + " požadováno, " + failed + " selhalo"));
        trace.add(new TraceEntry("justice.cz", "Stažení detailu rozhodnutí", outcome, null, fetched + " dokumentů"));
        trace.add(new TraceEntry("Ollama/LiteLLM", "Embeddingy chunků", outcome, null, embedded + " vektorů"));
        trace.add(new TraceEntry("PostgreSQL", "Uložení rozhodnutí a chunků", outcome, null,
                fetched + " rozhodnutí, " + chunks + " chunků"));
    }

    /**
     * One cheap existence check per id, run in parallel, keeps re-runs of the same range fast.
     * A stored-but-incomplete decision reports false and is therefore re-fetched (repair path).
     */
    private List<DecisionRef> notYetIngested(List<DecisionRef> batch) {
        List<Promise<Boolean>> promises = new ArrayList<>();
        for (DecisionRef ref : batch) {
            promises.add(Async.function(checking::isAlreadyIngested, ref.id()));
        }
        Promise.allOf(promises).get();
        List<DecisionRef> todo = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            if (Boolean.TRUE.equals(promises.get(i).get())) {
                skipped++;
            } else {
                todo.add(batch.get(i));
            }
        }
        return todo;
    }

    /** @return number of chunks produced; a single broken decision must not fail the whole job. */
    private int collect(Promise<IngestOneResult> promise) {
        try {
            IngestOneResult result = promise.get();
            fetched++;
            embedded += result.embeddings();
            return result.chunks();
        } catch (Exception e) {
            failed++;
            return 0;
        }
    }

    private List<LocalDate> days(LocalDate from, LocalDate to) {
        LocalDate start = from;
        List<LocalDate> days = new ArrayList<>();
        while (!start.isAfter(to) && days.size() < MAX_DAYS) {
            days.add(start);
            start = start.plusDays(1);
        }
        return days;
    }

    private void setStep(int index, StepStatus status, Integer durationMs, String detail) {
        Step current = steps.get(index);
        steps.set(index, new Step(current.name(), status, durationMs, current.attempts(), detail));
    }

    private static Step pending(String name) {
        return new Step(name, StepStatus.PENDING, null, null, null);
    }
}
