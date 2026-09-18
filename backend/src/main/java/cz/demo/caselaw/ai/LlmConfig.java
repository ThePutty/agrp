package cz.demo.caselaw.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Both models point at the same LiteLLM base URL - one OpenAI-compatible endpoint in front of a
 * remote free chat model and a local Ollama embedding model.
 *
 * The EmbeddingModel bean is created unconditionally: ingest needs embeddings even in mock mode
 * (Ollama is local, so it costs nothing).
 *
 * We deliberately do NOT declare RESPONSE_FORMAT_JSON_SCHEMA support: free OpenRouter models
 * frequently reject json_schema, so LangChain4j falls back to prompt-based JSON, which works
 * everywhere. Markdown fences and malformed JSON are repaired in LlmServices.
 */
@Configuration
public class LlmConfig {

    @Bean
    public LlmCallListener llmCallListener() {
        return new LlmCallListener();
    }

    @Bean
    public ChatModel chatModel(LlmProperties props, LlmCallListener listener) {
        return OpenAiChatModel.builder()
                .baseUrl(props.baseUrl())
                .apiKey(props.apiKey())
                .modelName(props.chatModel())
                .temperature(0.2)
                // Many free models are reasoning models: with a small budget they spend it all on
                // reasoning and return empty content. 6000 tokens leaves room for reasoning + JSON.
                .maxTokens(6000)
                // Free models via OpenRouter can take 1-2 minutes for a long argue prompt.
                .timeout(Duration.ofSeconds(120))
                // LiteLLM already retries + falls back; a second retry layer here only multiplies latency.
                .maxRetries(0)
                .logRequests(false)
                .listeners(List.of(listener))
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(LlmProperties props) {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(props.baseUrl())
                .apiKey(props.apiKey())
                .modelName(props.embedModel())
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}
