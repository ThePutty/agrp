package cz.demo.caselaw.domain;

import java.util.List;

/** One argument line (for or against the client), pointing at citation indexes. */
public record Argument(String claim, String reasoning, List<Integer> citationIndexes) {}
