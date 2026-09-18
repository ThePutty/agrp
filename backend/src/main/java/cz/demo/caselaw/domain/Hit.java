package cz.demo.caselaw.domain;

/** A ranked retrieval result. Scores are deterministic (vector, fulltext, RRF fusion). */
public record Hit(int rank, Decision decision, String snippet, Double vectorScore, Double textScore, double fusedScore) {}
