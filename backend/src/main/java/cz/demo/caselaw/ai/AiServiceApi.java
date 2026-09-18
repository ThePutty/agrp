package cz.demo.caselaw.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * The three LangChain4j AiServices used by the graph. Declarative interfaces + record return types
 * are all LangChain4j needs to produce structured output; the {@code strictNote} variable lets us
 * re-ask with a harsher JSON instruction when the first attempt does not parse.
 */
public final class AiServiceApi {

    private AiServiceApi() {
    }

    public interface QuestionAnalyzer {
        @SystemMessage(Prompts.ANALYZE_SYSTEM)
        @UserMessage("{{strictNote}}\n\nZkoumaná otázka:\n{{question}}")
        Dtos.QueryAnalysisDto analyze(@V("question") String question, @V("strictNote") String strictNote);
    }

    public interface SideAdvocate {
        @SystemMessage(Prompts.ARGUE_SYSTEM)
        @UserMessage("""
                Tvoje role: {{role}}
                {{strictNote}}
                {{feedback}}

                Zkoumaná otázka:
                {{question}}

                Rozhodnutí:
                {{context}}
                """)
        Dtos.ArgumentsDto argue(@V("role") String role,
                                @V("question") String question,
                                @V("context") String context,
                                @V("feedback") String feedback,
                                @V("strictNote") String strictNote);
    }

    public interface VerdictJudge {
        @SystemMessage(Prompts.JUDGE_SYSTEM)
        @UserMessage("""
                {{strictNote}}

                Zkoumaná otázka:
                {{question}}

                {{forArguments}}

                {{againstArguments}}

                Deterministická statistika podobných rozhodnutí (nepřepočítávej):
                {{outcome}}
                """)
        Dtos.JudgmentDto judge(@V("question") String question,
                               @V("forArguments") String forArguments,
                               @V("againstArguments") String againstArguments,
                               @V("outcome") String outcome,
                               @V("strictNote") String strictNote);
    }
}
