package cz.demo.caselaw.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Side effects of the ingest job: everything that talks to justice.cz, the LLM or PostgreSQL. */
@ActivityInterface
public interface IngestActivities {

    @ActivityMethod
    List<UUID> listDecisionIds(LocalDate day);

    @ActivityMethod
    boolean isAlreadyIngested(UUID id);

    /**
     * fetch -> upsert -> chunk -> embed -> store chunks. Heartbeats between steps.
     * {@code publishedOn} is the day the id was listed under; it is handed to the source so a cold
     * list-metadata cache can be refilled instead of degrading to the raw court code.
     */
    @ActivityMethod
    IngestOneResult ingestDecision(UUID id, LocalDate publishedOn);
}
