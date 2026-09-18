package cz.demo.caselaw.search;

import cz.demo.caselaw.domain.Citation;
import cz.demo.caselaw.domain.Hit;

import java.util.List;

/**
 * Deterministic citation check. A citation is verified only if
 *  (1) its decisionId is one of the retrieved hits and
 *  (2) the normalized quote is a substring of that decision's normalized full text.
 * Returns the same citations with verified/reason filled in.
 */
public interface CitationVerifier {
    List<Citation> verify(List<Citation> citations, List<Hit> hits);
}
