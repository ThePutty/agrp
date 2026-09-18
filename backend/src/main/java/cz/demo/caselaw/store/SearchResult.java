package cz.demo.caselaw.store;

import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.domain.Hit;

import java.util.List;

/**
 * @param hits    top-K decisions after RRF fusion, used as LLM context and citation universe
 * @param similar wider set (~30) of similar decisions, used only for deterministic outcome statistics
 */
public record SearchResult(List<Hit> hits, List<Decision> similar) {}
