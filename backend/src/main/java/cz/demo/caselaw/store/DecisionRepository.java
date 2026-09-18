package cz.demo.caselaw.store;

import cz.demo.caselaw.domain.CorpusStats;
import cz.demo.caselaw.domain.Decision;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence of decisions (table `decision`). Implemented with JdbcClient. */
public interface DecisionRepository {
    void upsert(Decision decision);
    Optional<Decision> findById(UUID id);
    List<Decision> findAllById(List<UUID> ids);
    /** True when a row with this id exists, regardless of how complete it is. */
    boolean exists(UUID id);

    /**
     * True when the stored row exists but its list metadata is missing: the court is still the bare
     * code from the finaldoc ({@link #BARE_COURT_CODE}, e.g. "OSTR") or there are no keywords.
     * Such rows were written while the in-memory list cache was cold and are re-fetched by a plain
     * re-ingest of the same date range. A missing row returns false - it is not "broken", just absent.
     */
    boolean needsRepair(UUID id);

    /** A court stored as a raw justice.cz code instead of the Czech court name. */
    String BARE_COURT_CODE = "^[A-Z]{2,6}[0-9]{0,2}$";

    /** Same predicate as {@link #needsRepair}, applied to an in-memory decision. */
    static boolean looksIncomplete(Decision d) {
        return d == null
                || d.court() == null || d.court().isBlank() || d.court().matches(BARE_COURT_CODE)
                || d.keywords() == null || d.keywords().isEmpty();
    }
    CorpusStats stats();
}
