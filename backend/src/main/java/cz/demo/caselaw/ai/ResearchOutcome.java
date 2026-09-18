package cz.demo.caselaw.ai;

import cz.demo.caselaw.domain.Answer;
import cz.demo.caselaw.domain.GraphNode;
import cz.demo.caselaw.domain.Hit;
import cz.demo.caselaw.domain.OutcomeStats;
import cz.demo.caselaw.domain.QueryAnalysis;
import cz.demo.caselaw.domain.TraceEntry;

import java.util.List;

/** Everything the graph produced for one question. */
public record ResearchOutcome(QueryAnalysis analysis, List<Hit> hits, OutcomeStats outcome, Answer answer,
                              List<GraphNode> nodes, List<TraceEntry> trace) {}
