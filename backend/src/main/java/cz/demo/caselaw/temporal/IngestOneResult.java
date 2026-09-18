package cz.demo.caselaw.temporal;

import java.util.UUID;

/** What ingesting one decision produced; aggregated into the Ingest counters by the workflow. */
public record IngestOneResult(UUID decisionId, String caseNumber, int chunks, int embeddings) {}
