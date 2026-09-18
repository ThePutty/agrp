package cz.demo.caselaw.ai;

import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.domain.Hit;
import cz.demo.caselaw.domain.OutcomeBreakdown;
import cz.demo.caselaw.domain.OutcomeBucket;
import cz.demo.caselaw.domain.OutcomeStats;

import java.util.List;

/**
 * All prompts in one place, in Czech (the corpus and the users are Czech).
 * The anti-hallucination rules are repeated in every argument prompt on purpose - they are the
 * only thing standing between the model and an invented spisová značka.
 */
public final class Prompts {

    private Prompts() {
    }

    /** Hard cap so a long justification cannot push the request over the free model's context. */
    public static final int MAX_CONTEXT_CHARS = 9_000;
    public static final int MAX_JUSTIFICATION_CHARS = 900;

    public static final String STRICT_JSON_NOTE =
            "DŮLEŽITÉ: Odpověz POUZE validním JSON objektem bez jakéhokoli dalšího textu, "
                    + "bez markdown bloků a bez komentářů.";

    public static final String ANALYZE_SYSTEM = """
            Jsi asistent pro rešerši publikované české judikatury. Z otázky uživatele urči:
            - legalConcepts: 3-6 právních pojmů (podstatná jména, česky),
            - provisions: ustanovení uvedená nebo zjevně dotčená, ve tvaru "§ 2913" nebo "§ 11 odst. 1",
            - searchQueries: 2-4 vyhledávací dotazy pro fulltextové a vektorové hledání v rozhodnutích.
            Nevymýšlej si ustanovení, která z otázky nevyplývají. Odpovídej česky.
            Neuvažuj nahlas: odpověz rovnou výsledným JSON objektem, bez úvodního textu.
            """;

    // Free safety-filtered models (Gemma) refuse anything framed as giving legal advice, so the
    // task is framed as what it actually is: an academic analysis of published case law.
    public static final String ARGUE_SYSTEM = """
            Jsi právní analytik a provádíš rešerši publikované české judikatury pro studijní účely.
            Nejde o právní radu konkrétní osobě, pouze o rozbor rozhodovací praxe soudů.
            Dostaneš zkoumanou otázku a očíslovaná soudní rozhodnutí. Sestav 2-4 argumenty
            podporující zadanou stranu sporu a ke každému uveď citace.

            PRAVIDLA (porušení znamená neplatnou odpověď):
            - Používej POUZE poskytnutá rozhodnutí.
            - Každou citaci uveď jako doslovný úryvek (min. 25 znaků, max. 300 znaků) zkopírovaný
              z textu rozhodnutí a uveď decisionId a spisovou značku přesně tak, jak jsou uvedeny.
            - Nevymýšlej si spisové značky ani rozhodnutí.
            - citationIndexes u argumentu odkazují na pole index z tvého seznamu citations.
            - Odpovídej česky.
            - Neuvažuj nahlas: odpověz rovnou výsledným JSON objektem, bez úvodního textu.
            """;

    public static final String JUDGE_SYSTEM = """
            Jsi právní analytik a shrnuješ rozbor publikované judikatury pro studijní účely.
            Nejde o právní radu. Dostaneš argumenty obou stran a deterministicky spočítanou
            statistiku výsledků podobných rozhodnutí.

            PRAVIDLA:
            - Skóre/statistika jsou dané deterministicky, NEPŘEPOČÍTÁVEJ je, jen je stručně okomentuj.
            - Neuvažuj nahlas: odpověz rovnou výsledným JSON objektem, bez úvodního textu.
            - verdict: 3-6 vět česky, věcně, bez floskulí.
            - strongerSide: FOR, AGAINST nebo BALANCED.
            - Nevymýšlej si rozhodnutí ani čísla.
            """;

    public static String roleFor(cz.demo.caselaw.domain.Side side) {
        return side == cz.demo.caselaw.domain.Side.FOR
                ? "v roli zástupce žalobce rozeber argumenty, které z judikatury svědčí VE PROSPĚCH nároku"
                : "v roli zástupce žalovaného rozeber argumenty, které z judikatury svědčí PROTI nároku";
    }

    public static final String CITATION_FEEDBACK = """
            Předchozí pokus obsahoval neověřitelné citace: %s.
            Citace [%s] neexistuje / úryvek nebyl nalezen v textu rozhodnutí; použij pouze doslovné
            úryvky z poskytnutých textů.
            """;

    /** Numbered decision context: [n] header + snippet + the beginning of the justification. */
    public static String context(List<Hit> hits) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Hit hit : hits) {
            Decision d = hit.decision();
            if (d == null) {
                continue;
            }
            n++;
            String block = """
                    [%d] decisionId=%s sp. zn.=%s soud=%s datum=%s § %s klíčová slova=%s
                    Úryvek: %s
                    Odůvodnění (začátek): %s

                    """.formatted(n, d.id(), nvl(d.caseNumber()), nvl(d.court()), d.decidedOn(),
                    join(d.provisions()), join(d.keywords()), nvl(hit.snippet()),
                    cut(d.justificationText(), MAX_JUSTIFICATION_CHARS));
            if (sb.length() + block.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            sb.append(block);
        }
        return sb.toString();
    }

    /** Deterministic numbers rendered for the judge; the model only comments on them. */
    public static String outcomeSummary(OutcomeStats stats) {
        if (stats == null) {
            return "Statistika není k dispozici.";
        }
        StringBuilder sb = new StringBuilder("Vzorek: " + stats.sampleSize() + " podobných rozhodnutí.\n");
        for (OutcomeBucket b : nvlList(stats.buckets())) {
            sb.append("- %s: %d (%.0f %%)%n".formatted(b.category(), b.count(), b.share() * 100));
        }
        for (OutcomeBreakdown b : nvlList(stats.byCourtLevel())) {
            sb.append("- %s: vyhověno %.0f %% (n=%d)%n".formatted(b.label(), b.grantedShare() * 100, b.sampleSize()));
        }
        return sb.toString();
    }

    public static String arguments(String title, List<cz.demo.caselaw.domain.Argument> args) {
        StringBuilder sb = new StringBuilder(title).append(":\n");
        if (args == null || args.isEmpty()) {
            return sb.append("(žádné)\n").toString();
        }
        for (cz.demo.caselaw.domain.Argument a : args) {
            sb.append("- ").append(a.claim()).append(" | ").append(nvl(a.reasoning())).append('\n');
        }
        return sb.toString();
    }

    static String cut(String s, int max) {
        if (s == null || s.isBlank()) {
            return "(bez textu)";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? "-" : String.join(", ", values);
    }

    private static String nvl(String s) {
        return s == null ? "-" : s;
    }

    private static <T> List<T> nvlList(List<T> l) {
        return l == null ? List.of() : l;
    }
}
