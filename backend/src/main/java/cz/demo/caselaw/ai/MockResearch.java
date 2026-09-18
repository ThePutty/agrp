package cz.demo.caselaw.ai;

import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.domain.Hit;
import cz.demo.caselaw.domain.OutcomeBucket;
import cz.demo.caselaw.domain.OutcomeCategory;
import cz.demo.caselaw.domain.OutcomeStats;
import cz.demo.caselaw.domain.Side;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic stand-in for the LLM. It produces the same DTO shapes as the real services, so the
 * graph, the citation verification and the UI are exercised identically without any network call -
 * essential for a demo on a conference wifi and for the unit tests.
 */
@Component
public class MockResearch {

    private static final Pattern PROVISION = Pattern.compile("§\\s*\\d+[a-z]?(\\s*odst\\.\\s*\\d+)?");
    private static final Pattern ACT = Pattern.compile("\\d+/\\d{4}\\s*Sb\\.");
    private static final Pattern WORD = Pattern.compile("[\\p{IsAlphabetic}]{5,}");
    private static final int QUOTE_CHARS = 120;

    public Dtos.QueryAnalysisDto analyze(String question) {
        String q = question == null ? "" : question;
        Set<String> provisions = new LinkedHashSet<>();
        for (Matcher m = PROVISION.matcher(q); m.find(); ) {
            provisions.add(m.group().replaceAll("\\s+", " ").trim());
        }
        for (Matcher m = ACT.matcher(q); m.find(); ) {
            provisions.add(m.group().replaceAll("\\s+", " ").trim());
        }
        List<String> concepts = new ArrayList<>();
        for (Matcher m = WORD.matcher(q); m.find(); ) {
            concepts.add(m.group().toLowerCase(Locale.ROOT));
        }
        concepts = concepts.stream()
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .limit(6)
                .toList();
        String keywords = String.join(" ", concepts);
        List<String> queries = keywords.isBlank() ? List.of(q) : List.of(q, keywords);
        return new Dtos.QueryAnalysisDto(concepts, List.copyOf(provisions), queries);
    }

    /**
     * Splits the hits by result type: decisions that look granted support the client, the rest
     * support the other side. Quotes are copied verbatim from the snippet, so the deterministic
     * verifier passes - the demo shows verified citations even offline.
     */
    public Dtos.ArgumentsDto argue(Side side, List<Hit> hits) {
        List<Hit> selected = selectFor(side, hits);
        List<Dtos.ArgumentDto> arguments = new ArrayList<>();
        List<Dtos.CitationDto> citations = new ArrayList<>();
        int index = 0;
        for (Hit hit : selected) {
            Decision d = hit.decision();
            if (d == null) {
                continue;
            }
            index++;
            citations.add(new Dtos.CitationDto(index, String.valueOf(d.id()), d.caseNumber(),
                    quoteOf(hit)));
            String claim = side == Side.FOR
                    ? "Rozhodnutí %s podporuje nárok klienta.".formatted(d.caseNumber())
                    : "Rozhodnutí %s svědčí proti nároku klienta.".formatted(d.caseNumber());
            arguments.add(new Dtos.ArgumentDto(claim,
                    "Soud %s se zabýval obdobnou otázkou a jeho závěry lze na případ klienta vztáhnout."
                            .formatted(d.court() == null ? "" : d.court()).trim(),
                    List.of(index)));
        }
        return new Dtos.ArgumentsDto(arguments, citations);
    }

    public Dtos.JudgmentDto judge(int forCount, int againstCount, OutcomeStats stats) {
        String stronger = forCount > againstCount ? "FOR" : forCount < againstCount ? "AGAINST" : "BALANCED";
        String verdict = """
                Obě strany se opírají o judikaturu z vyhledaného korpusu. Argumentace ve prospěch klienta \
                stojí na %d rozhodnutích, protistrana na %d. Deterministická statistika ukazuje %s. \
                Rozhodující bude, nakolik skutkový stav odpovídá citovaným rozhodnutím. \
                Tento verdikt byl vytvořen v režimu mock (bez volání jazykového modelu).\
                """.formatted(forCount, againstCount, grantedNote(stats));
        return new Dtos.JudgmentDto(verdict, stronger);
    }

    private static String grantedNote(OutcomeStats stats) {
        if (stats == null || stats.buckets() == null || stats.buckets().isEmpty()) {
            return "nedostatek dat";
        }
        OutcomeBucket granted = stats.buckets().stream()
                .filter(b -> b.category() == OutcomeCategory.GRANTED)
                .findFirst()
                .orElse(stats.buckets().get(0));
        return "podíl kategorie %s na úrovni %.0f %% ze vzorku %d rozhodnutí"
                .formatted(granted.category(), granted.share() * 100, stats.sampleSize());
    }

    private static List<Hit> selectFor(Side side, List<Hit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<Hit> granted = hits.stream().filter(h -> looksGranted(h.decision())).toList();
        List<Hit> dismissed = hits.stream().filter(h -> !looksGranted(h.decision())).toList();
        if (granted.isEmpty() || dismissed.isEmpty()) {
            // No usable signal in resultTypes: split the ranking in half so both sides get material.
            int half = Math.max(1, hits.size() / 2);
            return side == Side.FOR ? hits.subList(0, half) : hits.subList(half, hits.size());
        }
        List<Hit> chosen = side == Side.FOR ? granted : dismissed;
        return chosen.subList(0, Math.min(3, chosen.size()));
    }

    private static boolean looksGranted(Decision d) {
        if (d == null || d.resultTypes() == null) {
            return false;
        }
        return d.resultTypes().stream()
                .filter(java.util.Objects::nonNull)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .anyMatch(s -> Arrays.stream(new String[]{"vyhov", "zrušen", "zrušeno", "změněn"}).anyMatch(s::contains));
    }

    private static String quoteOf(Hit hit) {
        String snippet = hit.snippet() != null && !hit.snippet().isBlank()
                ? hit.snippet()
                : hit.decision() == null ? "" : hit.decision().fullText();
        String cleaned = snippet == null ? "" : snippet.strip();
        return cleaned.length() <= QUOTE_CHARS ? cleaned : cleaned.substring(0, QUOTE_CHARS);
    }
}
