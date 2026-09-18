package cz.demo.caselaw.search;

import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.domain.OutcomeBreakdown;
import cz.demo.caselaw.domain.OutcomeBucket;
import cz.demo.caselaw.domain.OutcomeCategory;
import cz.demo.caselaw.domain.OutcomeStats;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns the {@code caseResultType} codes of the retrieved decisions into shares. Computed from
 * stored metadata only - the LLM is never asked how cases like this usually end.
 *
 * <p>The API returns machine codes, not Czech sentences, and one decision may carry several of
 * them (a partly successful claim is published as {@code VYHOVENI} + {@code ZAMITNUTI}).
 * Observed values in a 300-decision sample published 2024-03-01..04 and their mapping:
 *
 * <table>
 *   <caption>caseResultType to OutcomeCategory</caption>
 *   <tr><th>code</th><th>meaning</th><th>occurrences</th><th>category</th></tr>
 *   <tr><td>VYHOVENI</td><td>vyhověno</td><td>249</td><td>GRANTED</td></tr>
 *   <tr><td>ZAMITNUTI</td><td>zamítnuto</td><td>71</td><td>DISMISSED</td></tr>
 *   <tr><td>ZASTAVENI</td><td>řízení zastaveno</td><td>3</td><td>OTHER</td></tr>
 *   <tr><td>ZMENA</td><td>rozhodnutí změněno</td><td>2</td><td>OTHER</td></tr>
 *   <tr><td>POTVRZENI</td><td>rozhodnutí potvrzeno</td><td>1</td><td>OTHER</td></tr>
 *   <tr><td>(none)</td><td>metadata missing</td><td>0</td><td>OTHER</td></tr>
 * </table>
 *
 * <p>Matching is case-insensitive and diacritics-insensitive on a <em>contains</em> basis, so the
 * Czech wordings that the same API uses elsewhere ("vyhověno", "částečně vyhověno", "zamítnut",
 * "odmítnuto") map to the same categories. A decision whose codes contain both a granting and a
 * dismissing outcome is counted once as {@link OutcomeCategory#PARTIALLY_GRANTED} - that is how
 * the 24 partly successful claims in the sample are recognised.
 */
@Component
public class DefaultOutcomeStatsCalculator implements OutcomeStatsCalculator {

    private static final Map<String, String> LEVEL_GENITIVE = Map.of(
            "okresní", "okresních",
            "krajský", "krajských",
            "vrchní", "vrchních",
            "jiný", "jiných");

    @Override
    public OutcomeStats compute(List<Decision> similarDecisions) {
        List<Decision> sample = similarDecisions == null ? List.of() : similarDecisions.stream().filter(d -> d != null).toList();
        int n = sample.size();
        if (n == 0) {
            return new OutcomeStats(0, List.of(), List.of(), List.of(),
                    "Nenalezena žádná podobná rozhodnutí, statistiku nelze spočítat.");
        }

        Map<OutcomeCategory, Integer> counts = new EnumMap<>(OutcomeCategory.class);
        Map<String, List<OutcomeCategory>> byLevel = new LinkedHashMap<>();
        Map<String, List<OutcomeCategory>> byYear = new TreeMap<>();

        for (Decision d : sample) {
            OutcomeCategory category = categorize(d.resultTypes());
            counts.merge(category, 1, Integer::sum);
            byLevel.computeIfAbsent(courtLevel(d.court()), k -> new ArrayList<>()).add(category);
            if (d.decidedOn() != null) {
                byYear.computeIfAbsent(String.valueOf(d.decidedOn().getYear()), k -> new ArrayList<>()).add(category);
            }
        }

        List<OutcomeBucket> buckets = new ArrayList<>();
        for (OutcomeCategory category : OutcomeCategory.values()) {
            int count = counts.getOrDefault(category, 0);
            buckets.add(new OutcomeBucket(category, count, (double) count / n));
        }

        List<OutcomeBreakdown> levels = byLevel.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new OutcomeBreakdown(e.getKey(), e.getValue().size(), grantedShare(e.getValue())))
                .toList();
        List<OutcomeBreakdown> years = byYear.entrySet().stream()
                .map(e -> new OutcomeBreakdown(e.getKey(), e.getValue().size(), grantedShare(e.getValue())))
                .toList();

        return new OutcomeStats(n, buckets, levels, years, note(n, levels));
    }

    /** A partial success counts as half a win - the demo shows one number, not a distribution. */
    static double grantedShare(List<OutcomeCategory> categories) {
        if (categories.isEmpty()) return 0;
        double granted = 0;
        for (OutcomeCategory c : categories) {
            if (c == OutcomeCategory.GRANTED) granted += 1;
            else if (c == OutcomeCategory.PARTIALLY_GRANTED) granted += 0.5;
        }
        return granted / categories.size();
    }

    static OutcomeCategory categorize(List<String> resultTypes) {
        if (resultTypes == null || resultTypes.isEmpty()) return OutcomeCategory.OTHER;
        boolean granted = false, dismissed = false, partial = false, other = false;
        for (String raw : resultTypes) {
            String code = TextNormalizer.normalize(raw);
            if (code.isEmpty()) continue;
            if (code.contains("castecn")) partial = true;
            else if (code.contains("vyhoven")) granted = true;
            else if (code.contains("zamitn")) dismissed = true;
            else other = true;                       // ZASTAVENI, ZMENA, POTVRZENI, ODMITNUTI, ...
        }
        if (partial || (granted && dismissed)) return OutcomeCategory.PARTIALLY_GRANTED;
        if (granted) return OutcomeCategory.GRANTED;
        if (dismissed) return OutcomeCategory.DISMISSED;
        return other ? OutcomeCategory.OTHER : OutcomeCategory.OTHER;
    }

    /** Court level derived from the Czech court name - the API exposes no level field. */
    static String courtLevel(String court) {
        if (court == null) return "jiný";
        String name = TextNormalizer.normalize(court);
        if (name.contains("vrchni")) return "vrchní";
        if (name.contains("mestsky soud v brne")) return "okresní";   // Brno's city court is a district court
        if (name.contains("krajsky") || name.contains("mestsky soud v praze")) return "krajský";
        if (name.contains("okresni") || name.contains("obvodni") || name.contains("mestsky")) return "okresní";
        return "jiný";
    }

    private static String note(int n, List<OutcomeBreakdown> levels) {
        String courts = levels.stream()
                .map(l -> LEVEL_GENITIVE.getOrDefault(l.label(), "jiných"))
                .distinct()
                .reduce((a, b) -> a + " a " + b)
                .orElse("různých");
        return "Statistika z %d podobných rozhodnutí %s soudů; orientační, není právní rada.".formatted(n, courts);
    }
}
