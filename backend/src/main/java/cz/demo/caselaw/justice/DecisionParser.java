package cz.demo.caselaw.justice;

import com.fasterxml.jackson.databind.JsonNode;
import cz.demo.caselaw.domain.Decision;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure mapping of the justice.cz JSON into the domain records. No I/O, fully unit-testable.
 *
 * <p>Two endpoints have to be combined: the list endpoint carries the Czech court name,
 * keywords and the printed case number, while the finaldoc endpoint carries the texts,
 * the machine-readable regulations and the case result types.
 */
@Component
public class DecisionParser {

    /** lexType enum of the API -> the abbreviation used in the human-readable provision string. */
    private static final Map<String, String> LEX_TYPE = Map.of(
            "PREDPIS_ZAKON", "z. č.",
            "PREDPIS_NARIZENI_VLADY", "nař. vl. č.",
            "PREDPIS_VYHLASKA", "vyhl. č.",
            "PREDPIS_USNESENI", "usn. č.",
            "PREDPIS_SDELENI", "sděl. č.");

    /** Parses one item of `/api/opendata/{y}/{m}/{d}`. */
    public ListEntry parseListEntry(JsonNode item) {
        return new ListEntry(
                uuidFromLink(text(item, "odkaz")),
                text(item, "jednaciCislo"),
                text(item, "soud"),
                text(item, "ecli"),
                text(item, "predmetRizeni"),
                date(text(item, "datumVydani")),
                date(text(item, "datumZverejneni")),
                strings(item.path("klicovaSlova")),
                strings(item.path("zminenaUstanoveni")));
    }

    /** Parses `/api/finaldoc/{uuid}`, merging in the cached list metadata when available. */
    public Decision parseFinalDoc(UUID id, JsonNode doc, ListEntry listEntry, String sourceUrl) {
        JsonNode meta = doc.path("metadata");
        List<String> provisions = listEntry != null && !listEntry.provisions().isEmpty()
                ? listEntry.provisions()
                : regulations(meta.path("regulations"));
        List<String> keywords = listEntry != null && !listEntry.keywords().isEmpty()
                ? listEntry.keywords()
                : strings(meta.path("flags"));

        return new Decision(
                id,
                firstNonBlank(text(meta, "ecli"), listEntry == null ? null : listEntry.ecli()),
                firstNonBlank(listEntry == null ? null : listEntry.caseNumber(), caseNumber(meta.path("caseNumber")), id.toString()),
                firstNonBlank(listEntry == null ? null : listEntry.court(), text(meta, "courtCode"), "neznámý soud"),
                text(meta, "courtCode"),
                firstDate(date(text(meta, "decisionAt")), listEntry == null ? null : listEntry.decidedOn()),
                firstDate(date(text(meta, "publishedAt")), listEntry == null ? null : listEntry.publishedOn()),
                firstNonBlank(text(meta, "caseSubject"), listEntry == null ? null : listEntry.subject()),
                keywords,
                provisions,
                strings(meta.path("caseResultType")),
                text(doc, "verdictText"),
                text(doc, "justificationText"),
                sourceUrl);
    }

    /** "https://.../api/finaldoc/74d5dab8-..." -> UUID. */
    public UUID uuidFromLink(String link) {
        if (link == null || link.isBlank()) throw new JusticeApiException("Chybí odkaz na rozhodnutí");
        String tail = link.substring(link.lastIndexOf('/') + 1);
        try {
            return UUID.fromString(tail);
        } catch (IllegalArgumentException e) {
            throw new JusticeApiException("Neplatné UUID v odkazu: " + link, e);
        }
    }

    /** {senate:21, registry:"C", index:250, year:2023, pageNumber:64} -> "21 C 250/2023-64". */
    private static String caseNumber(JsonNode cn) {
        if (cn.isMissingNode() || cn.isNull()) return null;
        String base = "%s %s %s/%s".formatted(
                cn.path("senate").asText(""), cn.path("registry").asText(""),
                cn.path("index").asText(""), cn.path("year").asText("")).trim();
        String page = cn.path("pageNumber").asText("");
        return page.isBlank() || "0".equals(page) ? base : base + "-" + page;
    }

    /** {paragraphNumber:"2991", lexNumber:89, lexYear:2012, lexType:"PREDPIS_ZAKON"} -> "§ 2991 z. č. 89/2012 Sb.". */
    private static List<String> regulations(JsonNode node) {
        List<String> out = new ArrayList<>();
        for (JsonNode r : node) {
            String abbr = LEX_TYPE.getOrDefault(r.path("lexType").asText(""), "č.");
            out.add("§ %s %s %s/%s Sb.".formatted(
                    r.path("paragraphNumber").asText(""), abbr,
                    r.path("lexNumber").asText(""), r.path("lexYear").asText("")));
        }
        return List.copyOf(out);
    }

    private static List<String> strings(JsonNode array) {
        if (!array.isArray()) return List.of();
        List<String> out = new ArrayList<>(array.size());
        for (JsonNode n : array) {
            String v = n.asText("").trim();
            if (!v.isEmpty()) out.add(v);
        }
        return List.copyOf(out);
    }

    private static String text(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        String v = n.asText("").trim();
        return v.isEmpty() ? null : v;
    }

    private static LocalDate date(String value) {
        if (value == null) return null;
        try {
            return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static LocalDate firstDate(LocalDate... values) {
        for (LocalDate v : values) if (v != null) return v;
        return null;
    }
}
