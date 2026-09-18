package cz.demo.caselaw.domain;

import java.util.List;

/** Snapshot of the PostgreSQL/pgvector storage for the "Technická stopa" panel. */
public record DbInspect(List<DbTable> tables, List<DbSection> sections, int embeddingDims, List<DbChunkRow> chunkRows) {

    public record DbTable(String name, int rows, String totalSize, List<DbIndex> indexes) {}

    public record DbIndex(String name, String kind, String size) {}

    public record DbSection(String section, int chunks) {}

    /** One row of table {@code chunk}; the embedding is trimmed to its first few dimensions. */
    public record DbChunkRow(long id, String decisionId, String caseNumber, int seq, String section,
                             String content, List<Double> embeddingPrefix, Double embeddingNorm, String tsvPrefix) {}
}
