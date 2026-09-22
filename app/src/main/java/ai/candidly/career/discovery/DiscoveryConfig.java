package ai.candidly.career.discovery;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class DiscoveryConfig {

    @Bean
    @ConfigurationProperties("candidly.discovery.greenhouse")
    public GreenhouseDiscoveryProperties greenhouseDiscoveryProperties() {
        return new GreenhouseDiscoveryProperties();
    }

    @Bean
    public RestClient greenhouseRestClient() {
        return discoveryRestClient("https://boards-api.greenhouse.io/v1/boards");
    }

    @Bean
    @ConfigurationProperties("candidly.discovery.lever")
    public LeverDiscoveryProperties leverDiscoveryProperties() {
        return new LeverDiscoveryProperties();
    }

    @Bean
    public RestClient leverRestClient() {
        return discoveryRestClient("https://api.lever.co/v0/postings");
    }

    private RestClient discoveryRestClient(String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                // Honest automation (docs/00 §1.2, docs/02 §5): a real, identifying user
                // agent - never spoofed - on a public syndication endpoint the board
                // itself documents for this use.
                .defaultHeader("User-Agent", "candidly-ai-discovery/0.1 (+https://github.com/candidly-ai)")
                .build();
    }
}
