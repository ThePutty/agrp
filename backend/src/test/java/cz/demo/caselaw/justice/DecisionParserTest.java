package cz.demo.caselaw.justice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.demo.caselaw.domain.Decision;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Parses the real JSON captured from rozhodnuti.justice.cz. */
class DecisionParserTest {

    private static final UUID ID = UUID.fromString("74d5dab8-9066-4b00-bc39-820d12ba6af8");
    private final ObjectMapper mapper = new ObjectMapper();
    private final DecisionParser parser = new DecisionParser();

    private JsonNode load(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/justice/" + name)) {
            return mapper.readTree(in);
        }
    }

    @Test
    void parsesListEntry() throws IOException {
        JsonNode list = load("opendata-list-sample.json");
        assertThat(list.path("totalPages").asInt()).isEqualTo(3);

        ListEntry entry = parser.parseListEntry(list.path("items").get(0));

        assertThat(entry.id()).isEqualTo(ID);
        assertThat(entry.caseNumber()).isEqualTo("21 C 250/2023-64");
        assertThat(entry.court()).isEqualTo("Okresní soud ve Znojmě");
        assertThat(entry.publishedOn()).isEqualTo(LocalDate.of(2024, 3, 5));
        assertThat(entry.decidedOn()).isEqualTo(LocalDate.of(2023, 11, 30));
        assertThat(entry.keywords()).contains("bezdůvodné obohacení", "smlouva o úvěru");
        assertThat(entry.provisions()).contains("§ 2991 z. č. 89/2012 Sb.");
    }

    @Test
    void mergesFinalDocWithListMetadata() throws IOException {
        ListEntry entry = parser.parseListEntry(load("opendata-list-sample.json").path("items").get(0));

        Decision d = parser.parseFinalDoc(ID, load("finaldoc-sample.json"), entry, "https://x/api/finaldoc/" + ID);

        assertThat(d.id()).isEqualTo(ID);
        assertThat(d.ecli()).isEqualTo("ECLI:CZ:OSZN:2023:21.C.250.2023.1");
        assertThat(d.court()).isEqualTo("Okresní soud ve Znojmě");     // only the list knows the name
        assertThat(d.courtCode()).isEqualTo("OSZN");
        assertThat(d.caseNumber()).isEqualTo("21 C 250/2023-64");
        assertThat(d.decidedOn()).isEqualTo(LocalDate.of(2023, 11, 30));
        assertThat(d.publishedOn()).isEqualTo(LocalDate.of(2024, 3, 5));
        assertThat(d.resultTypes()).containsExactly("ZAMITNUTI");
        assertThat(d.keywords()).contains("bezdůvodné obohacení");
        assertThat(d.subject()).contains("696").contains("příslušenstvím");
        assertThat(d.verdictText()).startsWith("I. Žalovaný je povinen zaplatit");
        assertThat(d.justificationText()).contains("Návrhem doručeným soudu");
        assertThat(d.sourceUrl()).endsWith(ID.toString());
    }

    @Test
    void fallsBackToFinalDocWhenListMetadataIsMissing() throws IOException {
        Decision d = parser.parseFinalDoc(ID, load("finaldoc-sample.json"), null, "https://x");

        assertThat(d.court()).isEqualTo("OSZN");                        // court code as the last resort
        assertThat(d.caseNumber()).isEqualTo("21 C 250/2023-64");       // rebuilt from the structured metadata
        assertThat(d.keywords()).contains("BEZDUVODNE_OBOHACENI");      // flags instead of Czech keywords
        assertThat(d.provisions()).contains("§ 2991 z. č. 89/2012 Sb.", "§ 1970 nař. vl. č. 351/2013 Sb.");
    }

    @Test
    void extractsUuidFromLink() {
        assertThat(parser.uuidFromLink("https://rozhodnuti.justice.cz/api/finaldoc/" + ID)).isEqualTo(ID);
        org.junit.jupiter.api.Assertions.assertThrows(JusticeApiException.class, () -> parser.uuidFromLink("https://x/nope"));
    }

    @Test
    void toleratesEmptyDocument() {
        Decision d = parser.parseFinalDoc(ID, mapper.createObjectNode(), null, "https://x");

        assertThat(d.caseNumber()).isEqualTo(ID.toString());
        assertThat(d.court()).isEqualTo("neznámý soud");
        assertThat(d.keywords()).isEqualTo(List.of());
        assertThat(d.resultTypes()).isEmpty();
    }
}
