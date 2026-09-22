package ai.candidly.career.config;

/**
 * One shape reused for every OpenAI-compatible-ish provider we talk to
 * (Groq for chat, Jina for embeddings, TypeSafe for System One judgments).
 * Each provider gets its own instance bound under its own prefix - see AiClientsConfig.
 */
public class ProviderProperties {

    private String baseUrl;
    private String apiKey;
    /** Chat model id (Groq) or embedding model id (Jina) or judgment model id (TypeSafe). */
    private String model;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
