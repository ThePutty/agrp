package cz.demo.caselaw.justice;

import cz.demo.caselaw.domain.Decision;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Where decisions come from. Two implementations selected by app.ingest.source:
 *  api      -> rozhodnuti.justice.cz open data REST API
 *  snapshot -> bundled data/snapshot/decisions.jsonl.gz (offline demo)
 */
public interface DecisionSource {
    /** IDs of decisions published on the given day (list endpoint, paginated). */
    List<UUID> listDecisionIds(LocalDate publishedOn);
    /** Full decision (finaldoc endpoint) parsed into the domain record. */
    default Decision fetch(UUID id) {
        return fetch(id, null);
    }

    /**
     * Full decision, with the day of publication the id came from. The day lets an implementation
     * refill its list-metadata cache deterministically after a restart (see JusticeOpenDataClient);
     * {@code null} means "unknown day" and behaves exactly like the old single-argument fetch.
     */
    Decision fetch(UUID id, LocalDate publishedOn);
}
