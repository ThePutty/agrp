package cz.demo.caselaw.domain;

/** Live state of one LangGraph4j node, streamed to the UI via Temporal signal + query. */
public record GraphNode(String name, StepStatus status, String startedAt, Integer durationMs, Integer attempt, String detail) {}
