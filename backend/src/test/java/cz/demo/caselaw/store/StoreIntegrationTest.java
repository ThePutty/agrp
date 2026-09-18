package cz.demo.caselaw.store;

import cz.demo.caselaw.domain.Chunk;
import cz.demo.caselaw.domain.CorpusStats;
import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.domain.Hit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real SQL (text[] binding, the pgvector cast, the hybrid queries) against the
 * local Postgres from docker-compose. Skipped when the database is not running, so `mvn test`
 * stays green offline. Everything lives in its own schema, the app data is never touched.
 */
class StoreIntegrationTest {

    private static final String URL = System.getProperty("test.db.url", "jdbc:postgresql://localhost:5432/caselaw");
    private static final String USER = System.getProperty("test.db.user", "caselaw");
    private static final String PASSWORD = System.getProperty("test.db.password", "caselaw");
    private static final String SCHEMA = "store_it";
    private static final int DIM = 1024;

    private static JdbcClient jdbc;
    private static JdbcDecisionRepository decisions;
    private static JdbcChunkRepository chunks;
    private static PgHybridSearch search;

    @BeforeAll
    static void setUp() throws Exception {
        Assumptions.assumeTrue(reachable(), "Postgres na " + URL + " neběží - integrační test přeskočen");

        JdbcTemplate bootstrap = new JdbcTemplate(
                new SimpleDriverDataSource(new org.postgresql.Driver(), URL, USER, PASSWORD));
        // Extensions always belong to public, never to the throw-away test schema.
        bootstrap.execute("CREATE EXTENSION IF NOT EXISTS vector SCHEMA public");
        bootstrap.execute("CREATE EXTENSION IF NOT EXISTS unaccent SCHEMA public");
        bootstrap.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        bootstrap.execute("CREATE SCHEMA " + SCHEMA);

        String schemaUrl = URL + (URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA + ",public";
        JdbcTemplate template = new JdbcTemplate(
                new SimpleDriverDataSource(new org.postgresql.Driver(), schemaUrl, USER, PASSWORD));
        for (String statement : migration().split("(?m);\\s*$")) {
            String sql = statement.strip();
            if (sql.isBlank() || sql.toUpperCase(java.util.Locale.ROOT).contains("CREATE EXTENSION")) continue;
            template.execute(sql);
        }

        jdbc = JdbcClient.create(template);
        decisions = new JdbcDecisionRepository(jdbc);
        chunks = new JdbcChunkRepository(jdbc);
        search = new PgHybridSearch(jdbc, decisions);
    }

    @Test
    void storesAndSearchesDecisions() {
        UUID uverId = UUID.randomUUID();
        UUID vyzivneId = UUID.randomUUID();
        decisions.upsert(decision(uverId, "Okresní soud ve Znojmě", "21 C 250/2023-64",
                List.of("smlouva o úvěru", "bezdůvodné obohacení"), List.of("§ 2991 z. č. 89/2012 Sb."), List.of("ZAMITNUTI"),
                "I. Žaloba se zamítá.",
                "Žalobkyně se domáhá zaplacení dlužné částky ze smlouvy o spotřebitelském úvěru."));
        decisions.upsert(decision(vyzivneId, "Okresní soud v Náchodě", "9 P 12/2023-20",
                List.of("výživné"), List.of("§ 915 z. č. 89/2012 Sb."), List.of("VYHOVENI"),
                "I. Výživné se zvyšuje.",
                "Otec je povinen přispívat na výživu nezletilého dítěte zvýšenou částkou."));

        // upsert is idempotent and updates in place
        decisions.upsert(decision(uverId, "Okresní soud ve Znojmě", "21 C 250/2023-64",
                List.of("smlouva o úvěru"), List.of("§ 2991 z. č. 89/2012 Sb."), List.of("ZAMITNUTI"),
                "I. Žaloba se zamítá.", "Žalobkyně se domáhá zaplacení dlužné částky ze smlouvy o spotřebitelském úvěru."));

        assertThat(decisions.exists(uverId)).isTrue();
        assertThat(decisions.exists(UUID.randomUUID())).isFalse();

        Optional<Decision> loaded = decisions.findById(uverId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().keywords()).containsExactly("smlouva o úvěru");
        assertThat(loaded.get().provisions()).containsExactly("§ 2991 z. č. 89/2012 Sb.");
        assertThat(loaded.get().resultTypes()).containsExactly("ZAMITNUTI");
        assertThat(loaded.get().court()).isEqualTo("Okresní soud ve Znojmě");

        assertThat(decisions.findAllById(List.of(vyzivneId, uverId)))
                .extracting(Decision::id).containsExactly(vyzivneId, uverId);   // caller's order preserved
        assertThat(decisions.findAllById(List.of())).isEmpty();

        float[] uverVector = vector(1);
        float[] vyzivneVector = vector(2);
        chunks.replaceChunks(uverId, chunksOf(uverId,
                "I. Žaloba se zamítá.", "Žalobkyně se domáhá zaplacení dlužné částky ze smlouvy o spotřebitelském úvěru."),
                List.of(uverVector, uverVector));
        chunks.replaceChunks(vyzivneId, chunksOf(vyzivneId,
                "I. Výživné se zvyšuje.", "Otec je povinen přispívat na výživu nezletilého dítěte zvýšenou částkou."),
                List.of(vyzivneVector, vyzivneVector));
        // replace really replaces
        chunks.replaceChunks(vyzivneId, chunksOf(vyzivneId,
                "I. Výživné se zvyšuje.", "Otec je povinen přispívat na výživu nezletilého dítěte zvýšenou částkou."),
                List.of(vyzivneVector, vyzivneVector));

        CorpusStats stats = decisions.stats();
        assertThat(stats.decisions()).isEqualTo(2);
        assertThat(stats.chunks()).isEqualTo(4);
        assertThat(stats.courts()).containsExactlyInAnyOrder("Okresní soud v Náchodě", "Okresní soud ve Znojmě");
        assertThat(stats.from()).isEqualTo(LocalDate.of(2024, 3, 1));

        // fulltext half: an unaccented query still matches the accented text
        // (the index uses the .simple. config, i.e. no Czech stemming - word forms must match)
        SearchResult byText = search.search(List.of("spotrebitelskem uveru"), List.of(), List.of(), 8, 30);
        assertThat(byText.hits()).extracting(h -> h.decision().id()).containsExactly(uverId);
        assertThat(byText.hits().get(0).textScore()).isNotNull();
        assertThat(byText.hits().get(0).snippet()).contains("spotřebitelském úvěru");

        // vector half: the closest embedding wins
        SearchResult byVector = search.search(List.of(), List.of(vyzivneVector), List.of(), 8, 30);
        assertThat(byVector.hits().get(0).decision().id()).isEqualTo(vyzivneId);
        assertThat(byVector.hits().get(0).vectorScore()).isNotNull();

        // hybrid: both decisions come back, ranked, with the whole set available for statistics
        SearchResult hybrid = search.search(List.of("výživné", "úvěr"), List.of(uverVector, vyzivneVector),
                List.of("§ 2991"), 8, 30);
        assertThat(hybrid.hits()).hasSize(2);
        assertThat(hybrid.hits()).extracting(Hit::rank).containsExactly(1, 2);
        assertThat(hybrid.similar()).hasSize(2);
        assertThat(hybrid.hits().get(0).fusedScore()).isGreaterThanOrEqualTo(hybrid.hits().get(1).fusedScore());

        assertThat(search.search(List.of(), List.of(), List.of(), 8, 30).hits()).isEmpty();
    }

    private static List<Chunk> chunksOf(UUID decisionId, String verdict, String justification) {
        return List.of(new Chunk(null, decisionId, 0, "verdict", verdict),
                new Chunk(null, decisionId, 1, "justification", justification));
    }

    private static Decision decision(UUID id, String court, String caseNumber, List<String> keywords,
                                     List<String> provisions, List<String> resultTypes,
                                     String verdict, String justification) {
        return new Decision(id, "ECLI:CZ:TEST", caseNumber, court, "OSZN",
                LocalDate.of(2023, 11, 30), LocalDate.of(2024, 3, 1), "předmět řízení",
                keywords, provisions, resultTypes, verdict, justification, "https://x/" + id);
    }

    /** Deterministic pseudo-embedding, so vector distances in the assertions are reproducible. */
    private static float[] vector(long seed) {
        Random random = new Random(seed);
        float[] v = new float[DIM];
        for (int i = 0; i < DIM; i++) v[i] = random.nextFloat() - 0.5f;
        return v;
    }

    private static String migration() throws IOException {
        try (InputStream in = StoreIntegrationTest.class.getResourceAsStream("/db/migration/V1__init.sql")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean reachable() {
        try (Connection ignored = DriverManager.getConnection(URL, USER, PASSWORD)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
