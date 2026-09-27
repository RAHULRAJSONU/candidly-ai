package ai.candidly.career.discovery;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

/**
 * A fifth, deliberately different-shaped discovery source: Schema.org {@code JobPosting}
 * structured data embedded on a company's own career page, rather than a named ATS's
 * syndication API - the "generic web / JSON-LD" tier from the job-sourcing research this
 * repo's discovery roadmap drew on, and the honest way to reach a company that isn't on
 * Greenhouse, Lever, Ashby, or Workday at all. Unlike those four adapters, there is no
 * single fixed global host to point at - {@code candidly.discovery.jsonld.career-page-urls}
 * holds full, individually-configured page URLs (one per company), and a candidate's
 * {@code AutopilotSettings.trackedCompanySlugs} entry under this adapter's namespace
 * ("jsonld:https://...") is the whole URL itself, not a short slug the way the other
 * adapters' tracked-company entries are (there is currently no
 * {@code AutopilotSettings.tsx} control for adding one, though {@code DiscoveryScheduler}
 * would already pick it up if one were added directly - this is an honest gap, not a
 * silent one).
 *
 * <p>Verified live during this implementation against a real Ashby-*hosted* individual
 * job page ({@code https://jobs.ashbyhq.com/{board}/{jobId}}), which server-renders one
 * clean {@code JobPosting} JSON-LD block per page. That's also this approach's real,
 * documented limitation: every other career site tried during verification - GitLab,
 * Meta, Figma, Notion, Sourcegraph, Buffer, LinkedIn, Indeed, Workable, SmartRecruiters,
 * even Ashby's own board *listing* page (as opposed to an individual job page) - is
 * client-side rendered and embeds no {@code JobPosting} JSON-LD in the initial HTML at
 * all. This adapter only ever sees markup present in that initial response, since this
 * repo has no headless-browser/JS-rendering layer (consistent with the standing "no
 * Kafka/Docker/Kubernetes/Redis" decision - adding one just to render client-side SPAs
 * would be a much bigger infra commitment than this adapter's incremental coverage
 * justifies). A configured URL that doesn't statically embed {@code JobPosting} JSON-LD
 * simply yields zero postings on that poll - logged at debug, not treated as a failure.
 */
@Component
public class GenericJsonLdDiscoveryAdapter implements JobBoardDiscoveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(GenericJsonLdDiscoveryAdapter.class);

    private final RestClient jsonLdRestClient;
    private final JsonLdJobPostingParser parser;
    private final GenericJsonLdDiscoveryProperties properties;

    public GenericJsonLdDiscoveryAdapter(RestClient jsonLdRestClient, JsonLdJobPostingParser parser,
            GenericJsonLdDiscoveryProperties properties) {
        this.jsonLdRestClient = jsonLdRestClient;
        this.parser = parser;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "jsonld";
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public List<DiscoveredJobPosting> poll(List<String> additionalTargets) {
        List<DiscoveredJobPosting> postings = new ArrayList<>();
        Set<String> pageUrls = new LinkedHashSet<>(properties.getCareerPageUrls());
        pageUrls.addAll(additionalTargets);
        for (String pageUrl : pageUrls) {
            try {
                postings.addAll(pollPage(pageUrl));
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode().value() == 429 || e.getStatusCode().value() == 503) {
                    log.warn("JSON-LD page '{}' rate-limited/unavailable ({}); skipping until next poll",
                            pageUrl, e.getStatusCode().value());
                } else {
                    log.warn("JSON-LD page '{}' returned {}; skipping", pageUrl, e.getStatusCode().value());
                }
            } catch (Exception e) {
                log.warn("JSON-LD page '{}' poll failed: {}", pageUrl, e.getMessage());
            }
        }
        return postings;
    }

    private List<DiscoveredJobPosting> pollPage(String pageUrl) {
        String html = jsonLdRestClient.get().uri(URI.create(pageUrl)).retrieve().body(String.class);
        if (html == null || html.isBlank()) {
            return List.of();
        }
        String host = hostOf(pageUrl);
        List<DiscoveredJobPosting> result = new ArrayList<>();
        for (JsonNode job : parser.extractJobPostingNodes(html)) {
            if (result.size() >= properties.getMaxPostingsPerPage()) {
                break;
            }
            result.add(toDiscoveredPosting(host, job));
        }
        if (result.isEmpty()) {
            log.debug("JSON-LD page '{}' had no embedded JobPosting markup", pageUrl);
        }
        return result;
    }

    private DiscoveredJobPosting toDiscoveredPosting(String host, JsonNode job) {
        JsonLdJobPostingParser.ParsedJobPosting fields = parser.toFields(host, job);
        return new DiscoveredJobPosting("jsonld:" + (host == null ? "unknown" : host) + ":" + fields.identifierValue(),
                fields.company(), fields.title(), fields.location(), fields.remote(), fields.description(),
                fields.compMinMinorUnits(), fields.compMaxMinorUnits(), fields.currency());
    }

    private String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }
}
