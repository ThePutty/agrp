package cz.demo.caselaw.ai;

import cz.demo.caselaw.domain.TraceEntry;

import java.util.function.Consumer;

/**
 * Tells the ChatModelListener which node issued the current LLM call and where to append the
 * Technical Trace row. Published through a ThreadLocal because LangGraph4j runs each node on its
 * own worker thread (and `reargue` re-opens it inside its CompletableFuture tasks).
 */
public final class LlmCallScope implements AutoCloseable {

    /** callName = the AiService/node issuing the call, used for the Technical Trace. */
    public record Scope(String callName, Consumer<TraceEntry> trace) {
    }

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private LlmCallScope() {
    }

    public static LlmCallScope open(String callName, Consumer<TraceEntry> trace) {
        CURRENT.set(new Scope(callName, trace));
        return new LlmCallScope();
    }

    public static Scope current() {
        return CURRENT.get();
    }

    @Override
    public void close() {
        CURRENT.remove();
    }
}
