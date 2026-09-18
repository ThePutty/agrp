package cz.demo.caselaw.search;

import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.domain.OutcomeStats;

import java.util.List;

/** Maps caseResultType values to OutcomeCategory and aggregates shares by category, court level and year. */
public interface OutcomeStatsCalculator {
    OutcomeStats compute(List<Decision> similarDecisions);
}
