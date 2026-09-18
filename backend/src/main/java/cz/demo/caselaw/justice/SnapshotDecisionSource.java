package cz.demo.caselaw.justice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cz.demo.caselaw.domain.Decision;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

/**
 * Offline demo source: a gzip JSONL snapshot of the open data (one {@link Decision} per line),
 * so the whole demo runs without network access. Loaded once into memory (~300 decisions).
 */
@Component
@ConditionalOnProperty(name = "app.ingest.source", havingValue = "snapshot")
public class SnapshotDecisionSource implements DecisionSource {

    private static final Logger log = LoggerFactory.getLogger(SnapshotDecisionSource.class);

    private final IngestProperties props;
    private final Map<UUID, Decision> byId = new LinkedHashMap<>();

    public SnapshotDecisionSource(IngestProperties props) {
        this.props = props;
    }

    @PostConstruct
    void load() {
        Path path = Path.of(props.snapshotPath());
        if (!Files.isReadable(path)) {
            log.warn("Snapshot {} není dostupný - zdroj dat je prázdný", path.toAbsolutePath());
            return;
        }
        ObjectMapper mapper = mapper();
        try (InputStream in = Files.newInputStream(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                Decision decision = mapper.readValue(line, Decision.class);
                byId.put(decision.id(), decision);
            }
        } catch (IOException e) {
            throw new JusticeApiException("Nepodařilo se načíst snapshot " + path, e);
        }
        log.info("Snapshot načten: {} rozhodnutí z {}", byId.size(), path);
    }

    /** Jackson mapper matching the snapshot shape (ISO dates, tolerant to extra fields). */
    static ObjectMapper mapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(FAIL_ON_UNKNOWN_PROPERTIES)
                .build()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public List<UUID> listDecisionIds(LocalDate publishedOn) {
        return byId.values().stream()
                .filter(d -> publishedOn.equals(d.publishedOn()))
                .map(Decision::id)
                .toList();
    }

    @Override
    public Decision fetch(UUID id, LocalDate publishedOn) {
        // The snapshot carries the full record already; the day is irrelevant here.
        Decision decision = byId.get(id);
        if (decision == null) throw new DecisionNotFoundException(id);
        return decision;
    }

    /** All snapshot decisions in file order - handy for a bulk ingest that ignores days. */
    public List<Decision> all() {
        return List.copyOf(byId.values());
    }
}
