package cz.demo.caselaw.justice;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the rozhodnuti.justice.cz open-data client (`app.justice.*`).
 *
 * @param baseUrl           API root, e.g. https://rozhodnuti.justice.cz
 * @param requestsPerSecond politeness limit for the public API (no API key, CC BY 4.0)
 */
@ConfigurationProperties("app.justice")
public record JusticeProperties(String baseUrl, double requestsPerSecond) {
    public JusticeProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://rozhodnuti.justice.cz";
        if (requestsPerSecond <= 0) requestsPerSecond = 3;
    }
}
