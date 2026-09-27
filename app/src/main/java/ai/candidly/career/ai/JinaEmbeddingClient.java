package ai.candidly.career.ai;

import java.util.Comparator;
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
 * Embedding client for Jina's OpenAI-compatible /embeddings endpoint.
 *
 * <p>Per docs/01 §4.1: pin one embedding model, size vectors to it, and never mix
 * models in one similarity index. This app only ever calls jina-embeddings-v3
 * (see application.yml candidly.jina.model) - a model change is a re-embedding
 * migration, not an in-place update.
 */
@Component
public class JinaEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(JinaEmbeddingClient.class);

    public static final String MODEL_ID = "jina-embeddings-v3";

    private final RestClient restClient;
    private final String model;

    public JinaEmbeddingClient(@Qualifier("jinaRestClient") RestClient restClient, ProviderProperties jinaProperties) {
        this.restClient = restClient;
        this.model = jinaProperties.getModel();
    }

    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    public List<float[]> embedAll(List<String> texts) {
        EmbeddingRequest request = new EmbeddingRequest(model, texts);
        EmbeddingResponse response;
        try {
            response = restClient.post()
                    .uri("/embeddings")
                    .body(request)
                    .retrieve()
                    .body(EmbeddingResponse.class);
        } catch (RestClientException e) {
            log.error("Jina embedding call failed", e);
            throw new ExternalAiServiceException("Jina", "embedding call failed", e);
        }
        if (response == null || response.data() == null) {
            log.error("Jina returned no embedding data");
            throw new ExternalAiServiceException("Jina", "returned no embedding data", null);
        }
        return response.data().stream()
                .sorted(Comparator.comparingInt(EmbeddingData::index))
                .map(EmbeddingData::embedding)
                .toList();
    }

    private record EmbeddingRequest(String model, List<String> input) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(List<EmbeddingData> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingData(int index, float[] embedding) {
    }
}
