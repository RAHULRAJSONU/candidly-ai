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

import ai.candidly.career.domain.CurrencyCodes;

/**
 * Ashby's public, unauthenticated Job Board API (docs/04 FR-1 names Ashby as the third of
 * the four in-scope boards, after {@link GreenhouseDiscoveryAdapter}/
 * {@link LeverDiscoveryAdapter}): {@code GET https://api.ashbyhq.com/posting-api/
 * job-board/{boardName}?includeCompensation=true} - the same no-key, no-login
 * syndication endpoint Ashby documents for embedding a careers page, verified live
 * against the real {@code ashby} board during this implementation. Like Lever's payload,
 * there is no company display-name field in the response (it is implicit in the board
 * name), so the configured board name is used as the company name, and a plain-text
 * {@code descriptionPlain} already ships on most postings - HTML stripping of
 * {@code descriptionHtml} is only a fallback for the rare posting that omits it.
 * {@code includeCompensation=true} (undocumented in Ashby's public API reference but
 * live-confirmed to work) adds a {@code compensation.summaryComponents} array; the first
 * component with {@code compensationType: "Salary"} is used as this posting's comp range,
 * converted from Ashby's whole-major-unit values to minor units via
 * {@link CurrencyCodes#toMinorUnits}. Not every posting states one (equity-only or
 * unlisted-comp roles have no Salary component), in which case all three fields stay null.
 */
@Component
public class AshbyDiscoveryAdapter implements JobBoardDiscoveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(AshbyDiscoveryAdapter.class);
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern REMOTE_HINT = Pattern.compile("remote", Pattern.CASE_INSENSITIVE);

    private final RestClient ashbyRestClient;
    private final AshbyDiscoveryProperties properties;

    public AshbyDiscoveryAdapter(RestClient ashbyRestClient, AshbyDiscoveryProperties properties) {
        this.ashbyRestClient = ashbyRestClient;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "ashby";
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public List<DiscoveredJobPosting> poll(List<String> additionalTargets) {
        List<DiscoveredJobPosting> postings = new ArrayList<>();
        Set<String> boardNames = new LinkedHashSet<>(properties.getBoardNames());
        boardNames.addAll(additionalTargets);
        for (String boardName : boardNames) {
            try {
                postings.addAll(pollBoard(boardName));
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode().value() == 429 || e.getStatusCode().value() == 503) {
                    log.warn("Ashby board '{}' rate-limited/unavailable ({}); skipping until next poll",
                            boardName, e.getStatusCode().value());
                } else {
                    log.warn("Ashby board '{}' returned {}; skipping", boardName, e.getStatusCode().value());
                }
            } catch (Exception e) {
                log.warn("Ashby board '{}' poll failed: {}", boardName, e.getMessage());
            }
        }
        return postings;
    }

    private List<DiscoveredJobPosting> pollBoard(String boardName) {
        AshbyJobBoardResponse response = ashbyRestClient.get()
                .uri("/{boardName}?includeCompensation=true", boardName)
                .retrieve()
                .body(AshbyJobBoardResponse.class);

        if (response == null || response.jobs() == null) {
            return List.of();
        }

        List<DiscoveredJobPosting> result = new ArrayList<>();
        for (AshbyJob job : response.jobs().stream().filter(AshbyJob::isListed).limit(properties.getMaxPostingsPerBoard()).toList()) {
            String location = job.location() == null ? "" : job.location();
            String description = job.descriptionPlain() != null && !job.descriptionPlain().isBlank()
                    ? job.descriptionPlain()
                    : stripHtml(job.descriptionHtml());
            boolean remote = job.isRemote() || REMOTE_HINT.matcher(location).find();
            AshbyCompensationComponent salary = salaryComponent(job.compensation());
            Long compMin = salary == null ? null : CurrencyCodes.toMinorUnits(salary.minValue(), salary.currencyCode());
            Long compMax = salary == null ? null : CurrencyCodes.toMinorUnits(salary.maxValue(), salary.currencyCode());
            String currency = salary == null ? null : salary.currencyCode();
            result.add(new DiscoveredJobPosting(
                    "ashby:" + boardName + ":" + job.id(),
                    boardName,
                    job.title(),
                    location,
                    remote,
                    description,
                    compMin, compMax, currency));
        }
        return result;
    }

    private AshbyCompensationComponent salaryComponent(AshbyCompensation compensation) {
        if (compensation == null || compensation.summaryComponents() == null) {
            return null;
        }
        return compensation.summaryComponents().stream()
                .filter(c -> "Salary".equals(c.compensationType()) && c.minValue() != null
                        && c.maxValue() != null && c.currencyCode() != null)
                .findFirst()
                .orElse(null);
    }

    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        String unescaped = HtmlUtils.htmlUnescape(html);
        return HTML_TAG.matcher(unescaped).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AshbyJobBoardResponse(List<AshbyJob> jobs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AshbyJob(String id, String title, String location, boolean isRemote, boolean isListed,
            String descriptionHtml, String descriptionPlain, AshbyCompensation compensation) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AshbyCompensation(List<AshbyCompensationComponent> summaryComponents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AshbyCompensationComponent(String compensationType, String currencyCode, Long minValue, Long maxValue) {
    }
}
