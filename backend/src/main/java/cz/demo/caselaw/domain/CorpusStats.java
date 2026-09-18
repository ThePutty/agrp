package cz.demo.caselaw.domain;

import java.time.LocalDate;
import java.util.List;

public record CorpusStats(int decisions, int chunks, List<String> courts, LocalDate from, LocalDate to) {}
