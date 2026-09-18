package cz.demo.caselaw.justice;

import com.fasterxml.jackson.databind.JsonNode;
import cz.demo.caselaw.domain.Decision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live open-data client for rozhodnuti.justice.cz (CC BY 4.0, no API key).
 *
 * <p>The list endpoint is the only place that carries the Czech court name, keywords and the
 * printed case number, therefore {@link #listDecisionIds} caches those rows per uuid and
 * {@link #fetch} merges them with the finaldoc payload. The cache is in-memory only, so after a
 * restart it is empty - {@link #fetch(UUID, LocalDate)} therefore re-lists the day the id came from
 * (cheap, ~3 pages) to refill it. Only when the day is unknown or the row is really missing does
 * {@code fetch} degrade to whatever the finaldoc itself provides.
 */
@Component
@ConditionalOnProperty(name = "app.ingest.source", havingValue = "api", matchIfMissing = true)
public class JusticeOpenDataClient implements DecisionSource {

    private static final Logger log = LoggerFactory.getLogger(JusticeOpenDataClient.class);
    private static final String USER_AGENT = "caselaw-ai-demo (hackathon)";
    private static final int PAGE_SIZE_GUARD = 50;   // safety stop for the pagination loop

    private final RestClient http;
    private final DecisionParser parser;
    private final String baseUrl;
    private final RateLimiter rateLimiter;
    private final Map<UUID, ListEntry> listMetadata = new ConcurrentHashMap<>();

    public JusticeOpenDataClient(RestClient.Builder builder, DecisionParser parser, JusticeProperties props) {
        this.baseUrl = props.baseUrl();
        this.parser = parser;
        this.rateLimiter = new RateLimiter(props.requestsPerSecond());
        this.http = builder.baseUrl(props.baseUrl()).defaultHeader("User-Agent", USER_AGENT).build();
    }

    @Override
    public List<UUID> listDecisionIds(LocalDate publishedOn) {
        List<UUID> ids = new ArrayList<>();
        for (int page = 0; page < PAGE_SIZE_GUARD; page++) {
            JsonNode body = get("/api/opendata/%d/%d/%d?page=%d"
                    .formatted(publishedOn.getYear(), publishedOn.getMonthValue(), publishedOn.getDayOfMonth(), page));
            // The endpoint currently answers {items, totalPages, totalElements, ...}; older shapes
            // returned a bare array, so both are handled.
            JsonNode items = body.isArray() ? body : body.path("items");
            if (!items.isArray() || items.isEmpty()) break;
            for (JsonNode item : items) {
                ListEntry entry = parser.parseListEntry(item);
                listMetadata.put(entry.id(), entry);
                ids.add(entry.id());
            }
            int totalPages = body.path("totalPages").asInt(page + 1);
            if (page + 1 >= totalPages) break;
        }
        log.debug("justice.cz: {} rozhodnutí zveřejněných {}", ids.size(), publishedOn);
        return List.copyOf(ids);
    }

    @Override
    public Decision fetch(UUID id, LocalDate publishedOn) {
        ListEntry entry = listMetadata.get(id);
        if (entry == null && publishedOn != null) {
            // Cold cache (typically after a restart mid-ingest): re-list the publication day,
            // which fills listMetadata for every id of that day, and try again.
            listDecisionIds(publishedOn);
            entry = listMetadata.get(id);
        }
        if (entry == null) {
            log.warn("Metadata ze seznamu chybí pro {} (den zveřejnění: {}) - soud a klíčová slova "
                    + "budou jen z finaldocu", id, publishedOn);
        }
        String path = "/api/finaldoc/" + id;
        JsonNode doc = get(path);
        return parser.parseFinalDoc(id, doc, entry, baseUrl + path);
    }

    /** Test seam: how many list rows are currently cached. */
    int cachedListEntries() {
        return listMetadata.size();
    }

    private JsonNode get(String path) {
        rateLimiter.acquire();
        try {
            JsonNode body = http.get().uri(path)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        if (res.getStatusCode().value() == 404) throw new DecisionNotFoundException(idOf(path));
                        throw new JusticeApiException("justice.cz vrátilo HTTP %d pro %s".formatted(res.getStatusCode().value(), path));
                    })
                    .body(JsonNode.class);
            if (body == null) throw new JusticeApiException("Prázdná odpověď justice.cz pro " + path);
            return body;
        } catch (DecisionNotFoundException | JusticeApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new JusticeApiException("Volání justice.cz selhalo: " + path, e);
        }
    }

    private static UUID idOf(String path) {
        try {
            return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
        } catch (IllegalArgumentException e) {
            return new UUID(0, 0);
        }
    }

    /** Minimal token bucket - the API has no key and no published quota, so we simply stay polite. */
    static final class RateLimiter {
        private final long intervalNanos;
        private long next;

        RateLimiter(double permitsPerSecond) {
            this.intervalNanos = (long) (1_000_000_000L / Math.max(permitsPerSecond, 0.1));
        }

        synchronized void acquire() {
            long now = System.nanoTime();
            long wait = next - now;
            if (wait > 0) {
                try {
                    Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new JusticeApiException("Stahování přerušeno", e);
                }
                now = System.nanoTime();
            }
            next = now + intervalNanos;
        }
    }
}
