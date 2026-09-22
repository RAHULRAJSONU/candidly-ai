package ai.candidly.career.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lever's public, unauthenticated postings API (docs/04 FR-1 names Lever alongside
 * Greenhouse/Ashby/Workday as an in-scope board; this is the second of the four, after
 * {@link GreenhouseDiscoveryAdapter}): {@code GET https://api.lever.co/v0/postings/
 * {company}?mode=json}, the same syndication-style endpoint Lever documents for
 * embedding a careers page - no key, no login. Unlike Greenhouse's response, this one is
 * a bare JSON array (no wrapper object) and already ships a plain-text description
 * ({@code descriptionPlain}), so no HTML stripping is needed on the common path - only
 * as a fallback if a posting omits it.
 *
 * <p>Lever's payload has no company display-name field (it's implicit in the URL slug),
 * so the configured slug is used as the company name - the same fallback
 * {@code GreenhouseDiscoveryAdapter} uses for a missing {@code company_name}.
 */
@Component
public class LeverDiscoveryAdapter implements JobBoardDiscoveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(LeverDiscoveryAdapter.class);
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern REMOTE_HINT = Pattern.compile("remote", Pattern.CASE_INSENSITIVE);

    private final RestClient leverRestClient;
    private final LeverDiscoveryProperties properties;

    public LeverDiscoveryAdapter(RestClient leverRestClient, LeverDiscoveryProperties properties) {
        this.leverRestClient = leverRestClient;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "lever";
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public List<DiscoveredJobPosting> poll() {
        List<DiscoveredJobPosting> postings = new ArrayList<>();
        for (String companySlug : properties.getCompanySlugs()) {
            try {
                postings.addAll(pollCompany(companySlug));
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode().value() == 429 || e.getStatusCode().value() == 503) {
                    log.warn("Lever site '{}' rate-limited/unavailable ({}); skipping until next poll",
                            companySlug, e.getStatusCode().value());
                } else {
                    log.warn("Lever site '{}' returned {}; skipping", companySlug, e.getStatusCode().value());
                }
            } catch (Exception e) {
                log.warn("Lever site '{}' poll failed: {}", companySlug, e.getMessage());
            }
        }
        return postings;
    }

    private List<DiscoveredJobPosting> pollCompany(String companySlug) {
        List<LeverPosting> response = leverRestClient.get()
                .uri("/{company}?mode=json", companySlug)
                .retrieve()
                .body(new ParameterizedTypeReference<List<LeverPosting>>() {
                });

        if (response == null) {
            return List.of();
        }

        List<DiscoveredJobPosting> result = new ArrayList<>();
        for (LeverPosting posting : response.stream().limit(properties.getMaxPostingsPerBoard()).toList()) {
            String location = posting.categories() != null ? posting.categories().location() : "";
            String description = posting.descriptionPlain() != null && !posting.descriptionPlain().isBlank()
                    ? posting.descriptionPlain()
                    : stripHtml(posting.description());
            boolean remote = "remote".equalsIgnoreCase(posting.workplaceType())
                    || REMOTE_HINT.matcher(location == null ? "" : location).find();
            result.add(new DiscoveredJobPosting(
                    "lever:" + companySlug + ":" + posting.id(),
                    companySlug,
                    posting.text(),
                    location == null ? "" : location,
                    remote,
                    description));
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
    private record LeverPosting(String id, String text, String description, String descriptionPlain,
            String workplaceType, LeverCategories categories) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LeverCategories(String location, String team, String commitment) {
    }
}
