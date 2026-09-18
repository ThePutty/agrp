package cz.demo.caselaw.ai;

import cz.demo.caselaw.domain.Answer;
import cz.demo.caselaw.domain.GraphNode;
import cz.demo.caselaw.domain.Hit;
import cz.demo.caselaw.domain.OutcomeStats;
import cz.demo.caselaw.domain.QueryAnalysis;

import java.util.List;

/**
 * Partial update emitted after every LangGraph4j node. Null fields mean "unchanged".
 * The Temporal activity forwards these to the workflow as a signal so the UI can render progress live.
 */
public record GraphEvent(GraphNode node, QueryAnalysis analysis, List<Hit> hits, OutcomeStats outcome, Answer answer) {
    public static GraphEvent nodeOnly(GraphNode node) {
        return new GraphEvent(node, null, null, null, null);
    }
}
