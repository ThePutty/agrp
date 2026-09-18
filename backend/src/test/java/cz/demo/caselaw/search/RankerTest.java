package cz.demo.caselaw.search;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankerTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static Ranker.Candidate c(UUID id, String snippet, double score) {
        return new Ranker.Candidate(id, snippet, score);
    }

    @Test
    void rewardsDecisionsAppearingInBothLists() {
        var vector = new Ranker.RankedList(Ranker.Kind.VECTOR, List.of(c(A, "a", 0.9), c(B, "b", 0.8)));
        var text = new Ranker.RankedList(Ranker.Kind.TEXT, List.of(c(C, "c", 5.0), c(A, "a2", 4.0)));

        List<Ranker.Fused> fused = Ranker.fuse(List.of(vector, text), List.of(), Map.of(), 10);

        assertThat(fused).extracting(Ranker.Fused::decisionId).containsExactly(A, C, B);   // C is rank 1 in the text list, B only rank 2 in the vector list
        // A is rank 1 in one list and rank 2 in the other.
        assertThat(fused.get(0).fusedScore()).isCloseTo(1.0 / 61 + 1.0 / 62, within(1e-9));
        assertThat(fused.get(0).vectorScore()).isEqualTo(0.9);
        assertThat(fused.get(0).textScore()).isEqualTo(4.0);
    }

    @Test
    void keepsOnlyTheBestChunkPerDecisionInOneList() {
        var list = new Ranker.RankedList(Ranker.Kind.VECTOR, List.of(c(A, "best", 0.9), c(A, "worse", 0.4), c(B, "b", 0.3)));

        List<Ranker.Fused> fused = Ranker.fuse(List.of(list), List.of(), Map.of(), 10);

        assertThat(fused).hasSize(2);
        assertThat(fused.get(0).snippet()).isEqualTo("best");
        // B must be ranked 2, not 3 - the duplicate chunk of A does not consume a rank.
        assertThat(fused.get(1).fusedScore()).isCloseTo(1.0 / 62, within(1e-9));
    }

    @Test
    void provisionBonusCanOvertakeAHigherRankedDecision() {
        var list = new Ranker.RankedList(Ranker.Kind.VECTOR, List.of(c(A, "a", 0.9), c(B, "b", 0.8)));
        Map<UUID, List<String>> provisions = Map.of(
                A, List.of("§ 1000 z. č. 99/1963 Sb."),
                B, List.of("§ 2991 z. č. 89/2012 Sb.", "§ 2390 z. č. 89/2012 Sb."));

        List<Ranker.Fused> fused = Ranker.fuse(List.of(list), List.of("§ 2991", "§ 2390"), provisions, 10);

        assertThat(fused.get(0).decisionId()).isEqualTo(B);
        assertThat(fused.get(0).fusedScore()).isCloseTo(1.0 / 62 + 2 * Ranker.PROVISION_BONUS, within(1e-9));
    }

    @Test
    void provisionWithDifferentLawDoesNotMatch() {
        var list = new Ranker.RankedList(Ranker.Kind.VECTOR, List.of(c(A, "a", 0.9)));
        Map<UUID, List<String>> provisions = Map.of(A, List.of("§ 2991 z. č. 40/1964 Sb."));

        List<Ranker.Fused> fused = Ranker.fuse(List.of(list), List.of("§ 2991 z. č. 89/2012 Sb."), provisions, 10);

        assertThat(fused.get(0).fusedScore()).isCloseTo(1.0 / 61, within(1e-9));
    }

    @Test
    void parsesProvisionKeys() {
        assertThat(Ranker.provisionKey("§ 2991 z. č. 89/2012 Sb."))
                .isEqualTo(new Ranker.ProvisionKey("2991", "89", "2012"));
        assertThat(Ranker.provisionKey("§ 14b")).isEqualTo(new Ranker.ProvisionKey("14b", null, null));
        assertThat(Ranker.provisionKey("bez paragrafu")).isNull();
    }

    @Test
    void respectsTheLimitAndIsStable() {
        var list = new Ranker.RankedList(Ranker.Kind.VECTOR, List.of(c(A, "a", 0.9), c(B, "b", 0.8), c(C, "c", 0.7)));

        assertThat(Ranker.fuse(List.of(list), null, null, 2)).hasSize(2);
        assertThat(Ranker.fuse(List.of(), List.of(), Map.of(), 5)).isEmpty();
    }
}
