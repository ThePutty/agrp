package cz.demo.caselaw.temporal;

import java.time.LocalDate;

/** Input of the IngestWorkflow: publication date range (inclusive) and a cap on decisions. */
public record IngestRequest(LocalDate from, LocalDate to, int limit) {}
