package cz.demo.caselaw.domain;

import java.util.List;

/** Full state of one research job, as exposed by GraphQL `research(id)`. */
public record Research(
        String id,
        String question,
        JobStatus status,
        List<Step> steps,
        QueryAnalysis analysis,
        List<Hit> hits,
        Answer answer,
        OutcomeStats outcome,
        GraphView graph,
        List<TraceEntry> trace
) {}
