package cz.demo.caselaw.store;

import cz.demo.caselaw.domain.Chunk;

import java.util.List;
import java.util.UUID;

/** Persistence of text chunks + embeddings (table `chunk`, pgvector column). */
public interface ChunkRepository {
    /** Deletes existing chunks of the decision and inserts the new ones. embeddings.get(i) belongs to chunks.get(i). */
    void replaceChunks(UUID decisionId, List<Chunk> chunks, List<float[]> embeddings);
}
