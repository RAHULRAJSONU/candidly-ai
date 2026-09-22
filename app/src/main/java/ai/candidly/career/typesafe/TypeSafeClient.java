package ai.candidly.career.typesafe;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ai.candidly.career.config.ProviderProperties;

/**
 * Thin wrapper over POST /v1/systemone. One call per judgment site: ask every
 * independent question about the same state together (they run in parallel and cannot
 * see each other's answers - see docs/typesafe "Compose and verify").
 */
@Component
public class TypeSafeClient {

    private final RestClient restClient;
    private final String model;

    public TypeSafeClient(@Qualifier("typeSafeRestClient") RestClient restClient, ProviderProperties typeSafeProperties) {
        this.restClient = restClient;
        this.model = typeSafeProperties.getModel();
    }

    public TypeSafeResponse ask(Object state, Map<String, TypeSafeQuestion> questions) {
        TypeSafeRequest request = new TypeSafeRequest(state, model, questions);
        return restClient.post()
                .uri("/systemone")
                .body(request)
                .retrieve()
                .body(TypeSafeResponse.class);
    }
}
