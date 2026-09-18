package cz.demo.caselaw.domain;

/** One row of the Technical Trace. layer = technology (Temporal, pgvector, LangGraph4j, LiteLLM ...). */
public record TraceEntry(String layer, String name, StepStatus status, Integer durationMs, String detail) {}
