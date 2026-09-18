package cz.demo.caselaw.store;

import cz.demo.caselaw.domain.CorpusStats;
import cz.demo.caselaw.domain.Decision;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Decisions in PostgreSQL. Plain JdbcClient - the schema is small and the SQL is part of the demo. */
@Repository
public class JdbcDecisionRepository implements DecisionRepository {

    private static final String COLUMNS = """
            id, ecli, case_number, court, court_code, decided_on, published_on, subject,
            keywords, provisions, result_types, verdict_text, justification_text, source_url
            """;

    private final JdbcClient jdbc;

    public JdbcDecisionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(Decision d) {
        jdbc.sql("""
                        INSERT INTO decision (id, ecli, case_number, court, court_code, decided_on, published_on, subject,
                                              keywords, provisions, result_types, verdict_text, justification_text, source_url)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO UPDATE SET
                            ecli = EXCLUDED.ecli, case_number = EXCLUDED.case_number, court = EXCLUDED.court,
                            court_code = EXCLUDED.court_code, decided_on = EXCLUDED.decided_on,
                            published_on = EXCLUDED.published_on, subject = EXCLUDED.subject,
                            keywords = EXCLUDED.keywords, provisions = EXCLUDED.provisions,
                            result_types = EXCLUDED.result_types, verdict_text = EXCLUDED.verdict_text,
                            justification_text = EXCLUDED.justification_text, source_url = EXCLUDED.source_url,
                            ingested_at = now()
                        """)
                .param(d.id())
                .param(d.ecli())
                .param(d.caseNumber())
                .param(d.court())
                .param(d.courtCode())
                .param(d.decidedOn() == null ? null : Date.valueOf(d.decidedOn()))
                .param(d.publishedOn() == null ? null : Date.valueOf(d.publishedOn()))
                .param(d.subject())
                // pgjdbc maps String[] onto text[] directly, no connection.createArrayOf needed.
                .param(textArray(d.keywords()))
                .param(textArray(d.provisions()))
                .param(textArray(d.resultTypes()))
                .param(d.verdictText())
                .param(d.justificationText())
                .param(d.sourceUrl())
                .update();
    }

    @Override
    public Optional<Decision> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM decision WHERE id = ?")
                .param(id)
                .query(JdbcDecisionRepository::mapDecision)
                .optional();
    }

    @Override
    public List<Decision> findAllById(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        // One round trip for the whole hit list; the caller's ranking order is restored afterwards.
        List<Decision> loaded = jdbc.sql("SELECT " + COLUMNS + " FROM decision WHERE id = ANY (?)")
                .param(ids.toArray(UUID[]::new))
                .query(JdbcDecisionRepository::mapDecision)
                .list();
        Map<UUID, Decision> byId = new LinkedHashMap<>();
        loaded.forEach(d -> byId.put(d.id(), d));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    @Override
    public boolean exists(UUID id) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM decision WHERE id = ?)")
                .param(id).query(Boolean.class).single());
    }

    @Override
    public boolean needsRepair(UUID id) {
        // '~' is the POSIX regex operator; coalesce keeps a NULL court/keywords on the broken side.
        return Boolean.TRUE.equals(jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM decision
                            WHERE id = ?
                              AND (coalesce(court, '') = ''
                                   OR court ~ ?
                                   OR coalesce(array_length(keywords, 1), 0) = 0))
                        """)
                .param(id).param(DecisionRepository.BARE_COURT_CODE)
                .query(Boolean.class).single());
    }

    @Override
    public CorpusStats stats() {
        return jdbc.sql("""
                        SELECT (SELECT count(*) FROM decision)          AS decisions,
                               (SELECT count(*) FROM chunk)             AS chunks,
                               (SELECT min(published_on) FROM decision) AS from_date,
                               (SELECT max(published_on) FROM decision) AS to_date,
                               (SELECT coalesce(array_agg(DISTINCT court ORDER BY court), ARRAY[]::text[]) FROM decision) AS courts
                        """)
                .query((rs, n) -> new CorpusStats(
                        rs.getInt("decisions"),
                        rs.getInt("chunks"),
                        stringList(rs.getArray("courts")),
                        localDate(rs.getDate("from_date")),
                        localDate(rs.getDate("to_date"))))
                .single();
    }

    private static Decision mapDecision(ResultSet rs, int rowNum) throws SQLException {
        return new Decision(
                rs.getObject("id", UUID.class),
                rs.getString("ecli"),
                rs.getString("case_number"),
                rs.getString("court"),
                rs.getString("court_code"),
                localDate(rs.getDate("decided_on")),
                localDate(rs.getDate("published_on")),
                rs.getString("subject"),
                stringList(rs.getArray("keywords")),
                stringList(rs.getArray("provisions")),
                stringList(rs.getArray("result_types")),
                rs.getString("verdict_text"),
                rs.getString("justification_text"),
                rs.getString("source_url"));
    }

    private static String[] textArray(List<String> values) {
        return values == null ? new String[0] : values.toArray(String[]::new);
    }

    private static List<String> stringList(Array array) throws SQLException {
        if (array == null) return List.of();
        Object raw = array.getArray();
        if (!(raw instanceof Object[] values)) return List.of();
        return Arrays.stream(values).filter(Objects::nonNull).map(String::valueOf).toList();
    }

    private static LocalDate localDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
