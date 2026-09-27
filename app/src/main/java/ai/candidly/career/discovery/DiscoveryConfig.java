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

    @Bean
    @ConfigurationProperties("candidly.discovery.ashby")
    public AshbyDiscoveryProperties ashbyDiscoveryProperties() {
        return new AshbyDiscoveryProperties();
    }

    @Bean
    public RestClient ashbyRestClient() {
        return discoveryRestClient("https://api.ashbyhq.com/posting-api/job-board");
    }

    @Bean
    @ConfigurationProperties("candidly.discovery.workday")
    public WorkdayDiscoveryProperties workdayDiscoveryProperties() {
        return new WorkdayDiscoveryProperties();
    }

    /** No fixed base URL - each Workday tenant has its own pod hostname, so
     * {@code WorkdayDiscoveryAdapter} always builds and passes an absolute URI per call. */
    @Bean
    public RestClient workdayRestClient() {
        return discoveryRestClient(null);
    }

    @Bean
    @ConfigurationProperties("candidly.discovery.jsonld")
    public GenericJsonLdDiscoveryProperties genericJsonLdDiscoveryProperties() {
        return new GenericJsonLdDiscoveryProperties();
    }

    /** No fixed base URL either - each configured target is itself a full career-page URL
     * on a different company's own domain (see {@code GenericJsonLdDiscoveryAdapter}). */
    @Bean
    public RestClient jsonLdRestClient() {
        return discoveryRestClient(null);
    }

    /** No RestClient bean - {@code HeadlessCareerPageDiscoveryAdapter} drives a real headless
     * browser (Playwright) instead of a plain HTTP client, since the whole point of that adapter
     * is reaching pages that only render their content via client-side JS. */
    @Bean
    @ConfigurationProperties("candidly.discovery.headless")
    public HeadlessCareerDiscoveryProperties headlessCareerDiscoveryProperties() {
        return new HeadlessCareerDiscoveryProperties();
    }

    /** {@code baseUrl} null (Workday only) builds a client with no fixed base - every call
     * site then passes its own absolute {@link java.net.URI}, since a base wouldn't mean
     * anything shared across tenants anyway. */
    private RestClient discoveryRestClient(String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory)
                // Honest automation (docs/00 §1.2, docs/02 §5): a real, identifying user
                // agent - never spoofed - on a public syndication endpoint the board
                // itself documents for this use.
                .defaultHeader("User-Agent", "candidly-ai-discovery/0.1 (+https://github.com/candidly-ai)");
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}
