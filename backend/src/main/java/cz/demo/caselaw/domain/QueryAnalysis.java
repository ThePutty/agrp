package cz.demo.caselaw.domain;

import java.util.List;

/** Output of the analyzeQuestion node: what to search for. */
public record QueryAnalysis(List<String> legalConcepts, List<String> provisions, List<String> searchQueries) {}
