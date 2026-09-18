package cz.demo.caselaw.domain;

import java.util.UUID;

/** A piece of a decision text that gets its own embedding. section = verdict | justification */
public record Chunk(Long id, UUID decisionId, int seq, String section, String content) {}
