package cz.demo.caselaw.graphql;

import cz.demo.caselaw.ai.LlmProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Demo helper: lists the model aliases the LiteLLM gateway exposes, so the browser can show them
 * without knowing the LiteLLM master key. The application itself only ever talks to these aliases.
 */
@RestController
public class LlmModelsController {

    private final RestClient client;

    public LlmModelsController(LlmProperties props) {
        this.client = RestClient.builder()
                .baseUrl(props.baseUrl().replaceAll("/v1/?$", ""))
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .build();
    }

    @GetMapping(value = "/api/llm/models", produces = MediaType.APPLICATION_JSON_VALUE)
    public String models() {
        return client.get().uri("/models").retrieve().body(String.class);
    }
}
