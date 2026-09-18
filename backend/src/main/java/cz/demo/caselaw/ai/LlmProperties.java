package cz.demo.caselaw.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Everything the app knows about "the model" is an alias resolved by the LiteLLM proxy
 * (chat-model, embed-model). Swapping OpenRouter for anything else is a proxy config change.
 */
@ConfigurationProperties("app.llm")
public record LlmProperties(
        @DefaultValue("http://localhost:4000/v1") String baseUrl,
        @DefaultValue("sk-litellm-demo") String apiKey,
        @DefaultValue("chat-model") String chatModel,
        @DefaultValue("embed-model") String embedModel,
        @DefaultValue("1024") int embeddingDimension,
        @DefaultValue("false") boolean mock,
        @DefaultValue("true") boolean toolsEnabled) {
}
