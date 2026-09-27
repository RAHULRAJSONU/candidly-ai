package ai.candidly.career.ai;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import ai.candidly.career.config.ProviderProperties;
import ai.candidly.career.error.ExternalAiServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal OpenAI-compatible chat client against Groq. This is plain generation only -
 * per docs/02 §1.1, the tailoring/critic subagents get zero tool access, so there is
 * deliberately no function-calling support here.
 */
@Component
public class GroqChatClient {

    private static final Logger log = LoggerFactory.getLogger(GroqChatClient.class);

    private final RestClient restClient;
    private final String model;

    public GroqChatClient(@Qualifier("groqRestClient") RestClient restClient, ProviderProperties groqProperties) {
        this.restClient = restClient;
        this.model = groqProperties.getModel();
    }

    public String complete(String systemPrompt, String userPrompt) {
        ChatRequest request = new ChatRequest(model, List.of(
                new ChatMessage("system", systemPrompt),
                new ChatMessage("user", userPrompt)),
                0.2);
        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientException e) {
            log.error("Groq chat completion call failed", e);
            throw new ExternalAiServiceException("Groq", "chat completion call failed", e);
        }
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            log.error("Groq returned no completion choices");
            throw new ExternalAiServiceException("Groq", "returned no completion choices", null);
        }
        return response.choices().get(0).message().content();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatMessage(String role, String content) {
    }

    private record ChatRequest(String model, List<ChatMessage> messages, double temperature) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(ChatMessage message) {
    }
}
