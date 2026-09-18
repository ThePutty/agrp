package cz.demo.caselaw.domain;

/** A coarse UI step (e.g. "Hybridní hledání"). */
public record Step(String name, StepStatus status, Integer durationMs, Integer attempts, String detail) {}
