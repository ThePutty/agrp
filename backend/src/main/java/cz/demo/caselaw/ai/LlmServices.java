package cz.demo.caselaw.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.demo.caselaw.domain.Hit;
import cz.demo.caselaw.domain.OutcomeStats;
import cz.demo.caselaw.domain.Side;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Thin facade over the LangChain4j AiServices with a two-step robustness ladder, because free
 * models are unreliable JSON producers:
 *   1. the declarative AiService (LangChain4j prompt-based JSON, no json_schema);
 *   2. one retry as a single plain prompt with a stricter "JSON only" instruction, then fence
 *      stripping + Jackson.
 * If both fail the caller degrades gracefully - the graph never dies because of a bad answer.
 * Two attempts, not three: the free tier is rate limited and each call costs ~15 s.
 */
@Component
public class LlmServices {

    private static final Logger log = LoggerFactory.getLogger(LlmServices.class);

    private final ChatModel chatModel;
    private final AiServiceApi.QuestionAnalyzer analyzer;
    private final AiServiceApi.SideAdvocate advocate;
    private final AiServiceApi.VerdictJudge judge;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public LlmServices(ChatModel chatModel, CaseLawTools tools, LlmProperties props) {
        this.chatModel = chatModel;
        this.analyzer = AiServices.builder(AiServiceApi.QuestionAnalyzer.class).chatModel(chatModel).build();
        AiServices<AiServiceApi.SideAdvocate> advocateBuilder =
                AiServices.builder(AiServiceApi.SideAdvocate.class).chatModel(chatModel);
        if (props.toolsEnabled()) {
            // Tool calling is only wired here: the argument nodes are the ones that need more text.
            advocateBuilder.tools(tools).maxToolCallingRoundTrips(3);
        }
        this.advocate = advocateBuilder.build();
        this.judge = AiServices.builder(AiServiceApi.VerdictJudge.class).chatModel(chatModel).build();
    }

    private static final String ANALYSIS_SHAPE =
            "{\"legalConcepts\":[\"\"],\"provisions\":[\"\"],\"searchQueries\":[\"\"]}";
    private static final String ARGUMENTS_SHAPE =
            "{\"arguments\":[{\"claim\":\"\",\"reasoning\":\"\",\"citationIndexes\":[1]}],"
                    + "\"citations\":[{\"index\":1,\"decisionId\":\"\",\"caseNumber\":\"\",\"quote\":\"\"}]}";
    private static final String JUDGMENT_SHAPE = "{\"verdict\":\"\",\"strongerSide\":\"FOR|AGAINST|BALANCED\"}";

    public Dtos.QueryAnalysisDto analyze(String question) {
        return call(Dtos.QueryAnalysisDto.class,
                strict -> analyzer.analyze(question, strict),
                Prompts.ANALYZE_SYSTEM + "\n\nZkoumaná otázka:\n" + question,
                ANALYSIS_SHAPE);
    }

    public Dtos.ArgumentsDto argue(Side side, String question, List<Hit> hits, String feedback) {
        String role = Prompts.roleFor(side);
        String context = Prompts.context(hits);
        String fallbackPrompt = Prompts.ARGUE_SYSTEM + "\n\nTvoje role: " + role
                + "\n" + (feedback == null ? "" : feedback)
                + "\n\nZkoumaná otázka:\n" + question + "\n\nRozhodnutí:\n" + context;
        return call(Dtos.ArgumentsDto.class,
                strict -> advocate.argue(role, question, context, feedback == null ? "" : feedback, strict),
                fallbackPrompt, ARGUMENTS_SHAPE);
    }

    public Dtos.JudgmentDto judge(String question, String forArguments, String againstArguments, OutcomeStats stats) {
        String outcome = Prompts.outcomeSummary(stats);
        String fallbackPrompt = Prompts.JUDGE_SYSTEM + "\n\nZkoumaná otázka:\n" + question
                + "\n\n" + forArguments + "\n\n" + againstArguments
                + "\n\nDeterministická statistika (nepřepočítávej):\n" + outcome;
        return call(Dtos.JudgmentDto.class,
                strict -> judge.judge(question, forArguments, againstArguments, outcome, strict),
                fallbackPrompt, JUDGMENT_SHAPE);
    }

    @FunctionalInterface
    private interface ServiceCall<T> {
        T apply(String strictNote);
    }

    private <T> T salvage(Class<T> type, RuntimeException e) {
        String message = e.getMessage();
        if (message == null || !message.contains("Failed to parse")) {
            return null;
        }
        String json = JsonText.extractObject(message);
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private <T> T call(Class<T> type, ServiceCall<T> service, String rawPrompt, String shape) {
        try {
            return service.apply("");
        } catch (RuntimeException first) {
            // Reasoning models often wrap the JSON in thinking text. LangChain4j's parser gives up, but
            // its exception carries the raw output - salvage the JSON from it before spending another call.
            T salvaged = salvage(type, first);
            if (salvaged != null) {
                log.info("AiService output parsed after salvaging JSON from the raw model text");
                return salvaged;
            }
            log.info("AiService call failed, retrying as a plain strict-JSON prompt: {}", Prompts.cut(first.toString(), 300));
        }
        String raw = chatModel.chat(rawPrompt + "\n\n" + Prompts.STRICT_JSON_NOTE + "\nStruktura: " + shape);
        String json = JsonText.extractObject(raw);
        if (json == null) {
            throw new IllegalStateException("Model nevrátil validní JSON: " + Prompts.cut(raw, 500));
        }
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Model nevrátil validní JSON: " + Prompts.cut(json, 500), e);
        }
    }
}
