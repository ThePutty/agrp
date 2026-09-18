package cz.demo.caselaw.domain;

import java.util.List;

/** Deterministic statistics of outcomes in similar decisions. Computed by SQL, never by the LLM. */
public record OutcomeStats(int sampleSize, List<OutcomeBucket> buckets, List<OutcomeBreakdown> byCourtLevel,
                           List<OutcomeBreakdown> byYear, String note) {}
