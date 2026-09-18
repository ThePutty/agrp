package cz.demo.caselaw.store;

import cz.demo.caselaw.domain.DbInspect;
import cz.demo.caselaw.domain.DbInspect.DbChunkRow;
import cz.demo.caselaw.domain.DbInspect.DbIndex;
import cz.demo.caselaw.domain.DbInspect.DbSection;
import cz.demo.caselaw.domain.DbInspect.DbTable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-only peek into the storage layer so the UI can show what the data physically looks like:
 * table and index sizes, chunk sections and a handful of {@code chunk} rows with a trimmed vector.
 */
@Repository
public class DbInspectRepository {

    private static final int PREFIX_DIMS = 8;
    private static final List<String> TABLES = List.of("decision", "chunk");

    private final JdbcClient jdbc;

    public DbInspectRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @param decisionIds when non-empty, sample rows come from these decisions (one chunk per decision). */
    public DbInspect inspect(List<UUID> decisionIds, int limit) {
        List<DbTable> tables = TABLES.stream().map(this::table).toList();
        List<DbSection> sections = jdbc.sql("SELECT section, count(*) AS n FROM chunk GROUP BY section ORDER BY n DESC")
                .query((rs, i) -> new DbSection(rs.getString("section"), rs.getInt("n")))
                .list();
        int dims = jdbc.sql("""
                        SELECT coalesce((SELECT atttypmod FROM pg_attribute
                                          WHERE attrelid = 'public.chunk'::regclass AND attname = 'embedding'), 0)
                        """)
                .query(Integer.class).single();
        return new DbInspect(tables, sections, dims, chunkRows(decisionIds, limit));
    }

    private DbTable table(String name) {
        int rows = jdbc.sql("SELECT count(*) FROM " + name).query(Integer.class).single();
        String size = jdbc.sql("SELECT pg_size_pretty(pg_total_relation_size(CAST('public.' || ? AS regclass)))").param(name)
                .query(String.class).single();
        List<DbIndex> indexes = jdbc.sql("""
                        SELECT i.indexrelname AS name, am.amname AS kind,
                               pg_size_pretty(pg_relation_size(i.indexrelid)) AS size
                        FROM pg_stat_user_indexes i
                        JOIN pg_class c ON c.oid = i.indexrelid
                        JOIN pg_am am ON am.oid = c.relam
                        WHERE i.schemaname = 'public' AND i.relname = ?
                        ORDER BY pg_relation_size(i.indexrelid) DESC
                        """)
                .param(name)
                .query((rs, n) -> new DbIndex(rs.getString("name"), rs.getString("kind"), rs.getString("size")))
                .list();
        return new DbTable(name, rows, size, indexes);
    }

    private List<DbChunkRow> chunkRows(List<UUID> decisionIds, int limit) {
        boolean filtered = decisionIds != null && !decisionIds.isEmpty();
        String idArray = filtered
                ? "{" + String.join(",", decisionIds.stream().map(UUID::toString).toList()) + "}"
                : null;
        // 1024 floats printed as text is ~12 KB per row; cut in SQL and parse only the prefix in Java.
        return jdbc.sql("""
                        SELECT * FROM (
                          SELECT DISTINCT ON (c.decision_id)
                                 c.id, c.decision_id, d.case_number, c.seq, c.section,
                                 left(c.content, 240)         AS content,
                                 left(c.embedding::text, 160) AS emb_prefix,
                                 vector_norm(c.embedding)     AS norm,
                                 left(c.tsv::text, 200)       AS tsv_prefix
                          FROM chunk c JOIN decision d ON d.id = c.decision_id
                          WHERE c.embedding IS NOT NULL
                            AND (CAST(? AS uuid[]) IS NULL OR c.decision_id = ANY(CAST(? AS uuid[])))
                          ORDER BY c.decision_id, c.seq
                        ) s
                        ORDER BY s.id
                        LIMIT ?
                        """)
                .param(idArray).param(idArray).param(limit)
                .query((rs, n) -> new DbChunkRow(
                        rs.getLong("id"),
                        rs.getString("decision_id"),
                        rs.getString("case_number"),
                        rs.getInt("seq"),
                        rs.getString("section"),
                        rs.getString("content"),
                        parsePrefix(rs.getString("emb_prefix")),
                        rs.getObject("norm") == null ? null : rs.getDouble("norm"),
                        rs.getString("tsv_prefix")))
                .list();
    }

    /** "[0.1,0.2,0.3,...cut" -> first PREFIX_DIMS complete numbers (the last, cut-off token is dropped). */
    static List<Double> parsePrefix(String literal) {
        List<Double> out = new ArrayList<>();
        if (literal == null) return out;
        String[] parts = literal.replace("[", "").replace("]", "").split(",");
        for (int i = 0; i < parts.length - 1 && out.size() < PREFIX_DIMS; i++) {
            try {
                out.add(Double.parseDouble(parts[i].trim()));
            } catch (NumberFormatException ignored) {
                break;
            }
        }
        return out;
    }
}
