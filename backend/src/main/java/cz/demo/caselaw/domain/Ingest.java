package cz.demo.caselaw.domain;

import java.util.List;

/** Progress of one ingest job (IngestWorkflow). */
public record Ingest(String id, JobStatus status, int requested, int fetched, int embedded, int failed,
                     List<Step> steps, List<TraceEntry> trace) {}
