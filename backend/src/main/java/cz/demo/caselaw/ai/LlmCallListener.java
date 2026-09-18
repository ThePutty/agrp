package cz.demo.caselaw.ai;

import cz.demo.caselaw.domain.StepStatus;
import cz.demo.caselaw.domain.TraceEntry;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single observability hook for every chat call:
 *  - appends a "LangChain4j" row (duration, model, tokens) to the Technical Trace;
 *  - remembers the concrete model name LiteLLM/OpenRouter actually used behind the alias.
 */
public class LlmCallListener implements ChatModelListener {

    private static final String STARTED = "llmCall.startedNanos";

    private final AtomicReference<String> lastModelName = new AtomicReference<>();

    public Optional<String> lastModelName() {
        return Optional.ofNullable(lastModelName.get());
    }

    @Override
    public void onRequest(ChatModelRequestContext ctx) {
        ctx.attributes().put(STARTED, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        ChatResponse response = ctx.chatResponse();
        String model = response != null && response.metadata() != null ? response.metadata().modelName() : null;
        if (model != null && !model.isBlank()) {
            lastModelName.set(model);
        }
        trace(StepStatus.DONE, elapsedMs(ctx.attributes()),
                "model=" + (model == null ? "?" : model) + tokens(response));
    }

    @Override
    public void onError(ChatModelErrorContext ctx) {
        trace(StepStatus.FAILED, elapsedMs(ctx.attributes()), String.valueOf(ctx.error()));
    }

    private void trace(StepStatus status, int ms, String detail) {
        LlmCallScope.Scope scope = LlmCallScope.current();
        if (scope != null && scope.trace() != null) {
            scope.trace().accept(new TraceEntry("LangChain4j", scope.callName(), status, ms, detail));
        }
    }

    private static String tokens(ChatResponse response) {
        if (response == null || response.metadata() == null || response.metadata().tokenUsage() == null) {
            return "";
        }
        return ", tokeny=" + response.metadata().tokenUsage().totalTokenCount();
    }

    private static int elapsedMs(Map<Object, Object> attributes) {
        Object started = attributes.get(STARTED);
        return started instanceof Long n ? (int) ((System.nanoTime() - n) / 1_000_000) : 0;
    }
}
