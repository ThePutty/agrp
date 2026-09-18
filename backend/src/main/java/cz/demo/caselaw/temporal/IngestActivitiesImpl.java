package cz.demo.caselaw.temporal;

import cz.demo.caselaw.domain.Chunk;
import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.justice.DecisionNotFoundException;
import cz.demo.caselaw.justice.DecisionSource;
import cz.demo.caselaw.store.ChunkRepository;
import cz.demo.caselaw.store.Chunker;
import cz.demo.caselaw.store.DecisionRepository;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** All ingest I/O lives here; the workflow stays deterministic and free of Spring beans. */
@Component
@ActivityImpl(taskQueues = TaskQueues.CASELAW)
public class IngestActivitiesImpl implements IngestActivities {

    /** Temporal error type matching RetryOptions.setDoNotRetry in IngestWorkflowImpl. */
    static final String DECISION_NOT_FOUND = "DecisionNotFound";

    private final DecisionSource source;
    private final DecisionRepository decisions;
    private final Chunker chunker;
    private final ChunkRepository chunks;
    private final EmbeddingModel embeddingModel;

    public IngestActivitiesImpl(DecisionSource source, DecisionRepository decisions, Chunker chunker,
                                ChunkRepository chunks, EmbeddingModel embeddingModel) {
        this.source = source;
        this.decisions = decisions;
        this.chunker = chunker;
        this.chunks = chunks;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<UUID> listDecisionIds(LocalDate day) {
        return source.listDecisionIds(day);
    }

    /**
     * True only when the stored row is complete. A row whose metadata came from a cold list cache
     * (bare court code, no keywords) counts as NOT ingested, so a plain re-run of the same range
     * repairs it: the upsert overwrites the row and replaceChunks re-embeds it.
     */
    @Override
    public boolean isAlreadyIngested(UUID id) {
        return decisions.exists(id) && !decisions.needsRepair(id);
    }

    @Override
    public IngestOneResult ingestDecision(UUID id, LocalDate publishedOn) {
        ActivityExecutionContext ctx = Activity.getExecutionContext();

        Decision decision = fetch(id, publishedOn);
        ctx.heartbeat("fetched " + decision.caseNumber());

        decisions.upsert(decision);
        ctx.heartbeat("stored " + decision.caseNumber());

        List<Chunk> parts = chunker.chunk(decision);
        if (parts.isEmpty()) {
            return new IngestOneResult(id, decision.caseNumber(), 0, 0);
        }
        ctx.heartbeat("chunked " + parts.size());

        List<float[]> vectors = embed(parts);
        ctx.heartbeat("embedded " + vectors.size());

        chunks.replaceChunks(id, parts, vectors);
        return new IngestOneResult(id, decision.caseNumber(), parts.size(), vectors.size());
    }

    private Decision fetch(UUID id, LocalDate publishedOn) {
        try {
            return source.fetch(id, publishedOn);
        } catch (DecisionNotFoundException e) {
            // HTTP 404 is permanent; retrying it only wastes the justice.cz rate limit.
            throw ApplicationFailure.newNonRetryableFailure(
                    "Rozhodnutí " + id + " nebylo nalezeno", DECISION_NOT_FOUND);
        }
    }

    private List<float[]> embed(List<Chunk> parts) {
        List<TextSegment> segments = new ArrayList<>(parts.size());
        for (Chunk part : parts) {
            segments.add(TextSegment.from(part.content()));
        }
        List<float[]> vectors = new ArrayList<>(segments.size());
        embeddingModel.embedAll(segments).content().forEach(e -> vectors.add(e.vector()));
        return vectors;
    }
}
