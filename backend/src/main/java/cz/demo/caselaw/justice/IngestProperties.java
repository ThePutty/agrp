package cz.demo.caselaw.justice;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the ingest pipeline (`app.ingest.*`).
 *
 * @param source               "api" (live REST) or "snapshot" (bundled gzip JSONL) - selects the DecisionSource bean
 * @param snapshotPath         path to decisions.jsonl.gz when source=snapshot
 * @param defaultLimit         default number of decisions one ingest job downloads
 * @param maxChunksPerDecision cap on chunks per decision (keeps embedding cost bounded)
 */
@ConfigurationProperties("app.ingest")
public record IngestProperties(String source, String snapshotPath, int defaultLimit, int maxChunksPerDecision) {
    public IngestProperties {
        if (source == null || source.isBlank()) source = "api";
        if (defaultLimit <= 0) defaultLimit = 300;
        if (maxChunksPerDecision <= 0) maxChunksPerDecision = 8;
    }
}
