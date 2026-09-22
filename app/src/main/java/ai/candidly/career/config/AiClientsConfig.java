package ai.candidly.career.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires up direct RestClient beans for the three external AI providers this app talks
 * to. Groq and Jina both speak the OpenAI-compatible REST shape but are different
 * providers with different base URLs/keys, so each gets its own named RestClient rather
 * than sharing Spring AI's single spring.ai.openai.* namespace.
 */
@Configuration
public class AiClientsConfig {

    @Bean
    @ConfigurationProperties("candidly.groq")
    public ProviderProperties groqProperties() {
        return new ProviderProperties();
    }

    @Bean
    @ConfigurationProperties("candidly.jina")
    public ProviderProperties jinaProperties() {
        return new ProviderProperties();
    }

    @Bean
    @ConfigurationProperties("candidly.typesafe")
    public ProviderProperties typeSafeProperties() {
        return new ProviderProperties();
    }

    @Bean
    public RestClient groqRestClient(ProviderProperties groqProperties) {
        return restClient(groqProperties);
    }

    @Bean
    public RestClient jinaRestClient(ProviderProperties jinaProperties) {
        return restClient(jinaProperties);
    }

    @Bean
    public RestClient typeSafeRestClient(ProviderProperties typeSafeProperties) {
        return restClient(typeSafeProperties);
    }

    private RestClient restClient(ProviderProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }
}
