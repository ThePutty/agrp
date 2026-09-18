package cz.demo.caselaw.temporal;

import java.time.LocalDate;
import java.util.UUID;

/**
 * An id together with the day it was listed under. The day travels with the id through the whole
 * workflow so that {@code DecisionSource.fetch} can refill its list-metadata cache after a restart
 * instead of falling back to the bare court code.
 */
public record DecisionRef(UUID id, LocalDate publishedOn) {}
