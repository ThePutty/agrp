package cz.demo.caselaw.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic fusion of several ranked lists into one ranking.
 *
 * <p>Reciprocal Rank Fusion (RRF) is used instead of a weighted sum of the raw scores because a
 * cosine similarity and a {@code ts_rank_cd} value live on incomparable scales - RRF only looks at
 * the position in each list, so adding a query or a retrieval mode never needs re-tuning weights.
 * A small bonus is added when the decision cites a statutory provision that the question mentions;
 * this is the one piece of legal domain knowledge in the ranking, and it is a fixed constant so the
 * result stays reproducible.
 */
public final class Ranker {

    /** RRF constant; 60 is the value from the original Cormack et al. paper and the common default. */
    public static final int RRF_K = 60;
    /** Added once per matching statutory provision. */
    public static final double PROVISION_BONUS = 0.05;

    private Ranker() {
    }

    public enum Kind {VECTOR, TEXT}

    /** One decision as returned by a single retrieval query. */
    public record Candidate(UUID decisionId, String snippet, double score) {}

    /** Result of one retrieval query (one embedding for VECTOR, one query string for TEXT), best first. */
    public record RankedList(Kind kind, List<Candidate> candidates) {}

    /** One decision after fusion. vectorScore/textScore are the best raw scores seen, null when absent. */
    public record Fused(UUID decisionId, String snippet, Double vectorScore, Double textScore, double fusedScore) {}

    /**
     * @param lists              ranked lists to fuse (already sorted best first, one decision at most once per list)
     * @param queryProvisions    provisions detected in the question, e.g. "§ 2991" or "§ 2991 z. č. 89/2012 Sb."
     * @param decisionProvisions provisions cited by each candidate decision
     * @param limit              maximum number of fused entries to return
     */
    public static List<Fused> fuse(List<RankedList> lists,
                                   List<String> queryProvisions,
                                   Map<UUID, List<String>> decisionProvisions,
                                   int limit) {
        Map<UUID, Accumulator> acc = new LinkedHashMap<>();
        for (RankedList list : lists) {
            if (list == null || list.candidates() == null) continue;
            int rank = 0;
            Set<UUID> seenInList = new LinkedHashSet<>();
            for (Candidate c : list.candidates()) {
                if (c == null || !seenInList.add(c.decisionId())) continue;   // best chunk per decision per list
                rank++;
                acc.computeIfAbsent(c.decisionId(), Accumulator::new).add(list.kind(), rank, c);
            }
        }

        Set<ProvisionKey> wanted = provisionKeys(queryProvisions);
        List<Fused> fused = new ArrayList<>(acc.size());
        for (Accumulator a : acc.values()) {
            double bonus = wanted.isEmpty() ? 0
                    : PROVISION_BONUS * matchCount(wanted, decisionProvisions == null
                    ? List.of() : decisionProvisions.getOrDefault(a.decisionId, List.of()));
            fused.add(new Fused(a.decisionId, a.snippet, a.vectorScore, a.textScore, a.rrf + bonus));
        }
        fused.sort(Comparator.comparingDouble(Fused::fusedScore).reversed()
                .thenComparing(f -> f.decisionId().toString()));   // stable, reproducible ties
        return fused.size() <= limit ? List.copyOf(fused) : List.copyOf(fused.subList(0, limit));
    }

    private static final class Accumulator {
        private final UUID decisionId;
        private double rrf;
        private Double vectorScore;
        private Double textScore;
        private String snippet;
        private double bestScore = Double.NEGATIVE_INFINITY;

        Accumulator(UUID decisionId) {
            this.decisionId = decisionId;
        }

        void add(Kind kind, int rank, Candidate c) {
            rrf += 1.0 / (RRF_K + rank);
            if (kind == Kind.VECTOR) vectorScore = max(vectorScore, c.score());
            else textScore = max(textScore, c.score());
            if (c.snippet() != null && !c.snippet().isBlank() && c.score() > bestScore) {
                bestScore = c.score();
                snippet = c.snippet();
            }
        }

        private static Double max(Double current, double candidate) {
            return current == null ? candidate : Math.max(current, candidate);
        }
    }

    // --- provision matching -------------------------------------------------

    private static final Pattern PARAGRAPH = Pattern.compile("(?:§\\s*)?(\\d{1,4}[a-z]?)");
    private static final Pattern LAW = Pattern.compile("\\b(\\d{1,4})\\s*/\\s*(\\d{4})\\b");

    /** Paragraph plus the law it belongs to; lawNumber/lawYear are null when the reference is bare ("§ 2991"). */
    record ProvisionKey(String paragraph, String lawNumber, String lawYear) {
        boolean matches(ProvisionKey other) {
            if (!paragraph.equals(other.paragraph)) return false;
            if (lawNumber == null || other.lawNumber == null) return true;   // bare reference: paragraph is enough
            return lawNumber.equals(other.lawNumber) && lawYear.equals(other.lawYear);
        }
    }

    static Set<ProvisionKey> provisionKeys(List<String> provisions) {
        Set<ProvisionKey> keys = new LinkedHashSet<>();
        if (provisions == null) return keys;
        for (String raw : provisions) {
            ProvisionKey key = provisionKey(raw);
            if (key != null) keys.add(key);
        }
        return keys;
    }

    static ProvisionKey provisionKey(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String text = TextNormalizer.normalize(raw);
        Matcher law = LAW.matcher(text);
        String lawNumber = null, lawYear = null;
        String paragraphPart = text;
        if (law.find()) {
            lawNumber = law.group(1);
            lawYear = law.group(2);
            paragraphPart = text.substring(0, law.start());
        }
        Matcher p = PARAGRAPH.matcher(paragraphPart);
        if (!p.find()) return null;
        return new ProvisionKey(p.group(1), lawNumber, lawYear);
    }

    private static int matchCount(Set<ProvisionKey> wanted, List<String> decisionProvisions) {
        Set<ProvisionKey> have = provisionKeys(decisionProvisions);
        int matches = 0;
        for (ProvisionKey w : wanted) {
            if (have.stream().anyMatch(h -> h.matches(w))) matches++;
        }
        return matches;
    }
}
