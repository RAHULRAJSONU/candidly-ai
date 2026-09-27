package ai.candidly.career.discovery;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Greenhouse's public, unauthenticated Job Board API (docs/00 §1.1, docs/01 §3.1):
 * {@code GET https://boards-api.greenhouse.io/v1/boards/{token}/jobs?content=true}.
 * This is the syndication endpoint Greenhouse itself documents for building a careers
 * page - no key, no login, exactly the "discovery is unaffected, it's the apply half
 * that doesn't exist for a job seeker" distinction from docs/00 §1.1.
 *
 * <p>Honest-automation disciplines actually applied: a real, identifying User-Agent
 * (see {@code GreenhouseDiscoveryConfig}), and a per-board try/catch that skips a board
 * on 429/503 rather than retrying into a rate limit. Not yet applied: a robots.txt
 * check (Greenhouse's own docs designate this exact endpoint for syndication, but a
 * production discovery adapter should still check per docs/01 §3.1) and a real
 * backoff/retry budget beyond "wait for the next scheduled poll."
 */
@Component
public class GreenhouseDiscoveryAdapter implements JobBoardDiscoveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(GreenhouseDiscoveryAdapter.class);
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern REMOTE_HINT = Pattern.compile("remote", Pattern.CASE_INSENSITIVE);

    private final RestClient greenhouseRestClient;
    private final GreenhouseDiscoveryProperties properties;

    public GreenhouseDiscoveryAdapter(RestClient greenhouseRestClient, GreenhouseDiscoveryProperties properties) {
        this.greenhouseRestClient = greenhouseRestClient;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "greenhouse";
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public List<DiscoveredJobPosting> poll(List<String> additionalTargets) {
        List<DiscoveredJobPosting> postings = new ArrayList<>();
        Set<String> boardTokens = new LinkedHashSet<>(properties.getBoardTokens());
        boardTokens.addAll(additionalTargets);
        for (String boardToken : boardTokens) {
            try {
                postings.addAll(pollBoard(boardToken));
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode().value() == 429 || e.getStatusCode().value() == 503) {
                    log.warn("Greenhouse board '{}' rate-limited/unavailable ({}); skipping until next poll",
                            boardToken, e.getStatusCode().value());
                } else {
                    log.warn("Greenhouse board '{}' returned {}; skipping", boardToken, e.getStatusCode().value());
                }
            } catch (Exception e) {
                log.warn("Greenhouse board '{}' poll failed: {}", boardToken, e.getMessage());
            }
        }
        return postings;
    }

    private List<DiscoveredJobPosting> pollBoard(String boardToken) {
        GreenhouseJobsResponse response = greenhouseRestClient.get()
                .uri("/{token}/jobs?content=true", boardToken)
                .retrieve()
                .body(GreenhouseJobsResponse.class);

        if (response == null || response.jobs() == null) {
            return List.of();
        }

        List<DiscoveredJobPosting> result = new ArrayList<>();
        List<GreenhouseJob> jobs = response.jobs().stream()
                .limit(properties.getMaxPostingsPerBoard())
                .toList();
        for (GreenhouseJob job : jobs) {
            String locationName = job.location() != null ? job.location().name() : "";
            String plainDescription = stripHtml(job.content());
            result.add(new DiscoveredJobPosting(
                    "greenhouse:" + boardToken + ":" + job.id(),
                    job.companyName() != null ? job.companyName() : boardToken,
                    job.title(),
                    locationName,
                    REMOTE_HINT.matcher(locationName == null ? "" : locationName).find(),
                    plainDescription,
                    // Greenhouse's public Job Board API response has no compensation field at
                    // all for this board (confirmed by inspecting the raw response keys, not
                    // just an empty value) - see DiscoveredJobPosting's javadoc.
                    null, null, null));
        }
        return result;
    }

    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        String unescaped = HtmlUtils.htmlUnescape(html);
        return HTML_TAG.matcher(unescaped).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GreenhouseJobsResponse(List<GreenhouseJob> jobs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GreenhouseJob(long id, String title, String content,
            @com.fasterxml.jackson.annotation.JsonProperty("company_name") String companyName,
            GreenhouseLocation location) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GreenhouseLocation(String name) {
    }
}
