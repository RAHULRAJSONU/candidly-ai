package ai.candidly.career.typesafe;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import ai.candidly.career.config.ProviderProperties;
import ai.candidly.career.error.ExternalAiServiceException;

/**
 * Thin wrapper over POST /v1/systemone. One call per judgment site: ask every
 * independent question about the same state together (they run in parallel and cannot
 * see each other's answers - see docs/typesafe "Compose and verify").
 */
@Component
public class TypeSafeClient {

    private static final Logger log = LoggerFactory.getLogger(TypeSafeClient.class);

    private final RestClient restClient;
    private final String model;

    public TypeSafeClient(@Qualifier("typeSafeRestClient") RestClient restClient, ProviderProperties typeSafeProperties) {
        this.restClient = restClient;
        this.model = typeSafeProperties.getModel();
    }

    public TypeSafeResponse ask(Object state, Map<String, TypeSafeQuestion> questions) {
        TypeSafeRequest request = new TypeSafeRequest(state, model, questions);
        TypeSafeResponse response;
        try {
            response = restClient.post()
                    .uri("/systemone")
                    .body(request)
                    .retrieve()
                    .body(TypeSafeResponse.class);
        } catch (RestClientException e) {
            log.error("TypeSafe judgment call failed", e);
            throw new ExternalAiServiceException("TypeSafe", "judgment call failed", e);
        }
        if (response == null) {
            log.error("TypeSafe returned an empty response");
            throw new ExternalAiServiceException("TypeSafe", "returned an empty response", null);
        }
        return response;
    }
}
