package ai.candidly.career.discovery;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Workday's CXS API (docs/04 FR-1's fourth and last named board, after Greenhouse/Lever/
 * {@link AshbyDiscoveryAdapter}): {@code POST https://{host}/wday/cxs/{tenant}/{site}/
 * jobs} - the same request thousands of Workday customer career sites make from the
 * browser to render their own public job search page, so it carries the same "no key, no
 * login, exactly what the page itself calls" discipline as the other three adapters.
 *
 * <p><b>Honest limitation, unlike the other three:</b> Greenhouse/Lever/Ashby each expose
 * one fixed global host with a company identified by a single slug, so this session could
 * pick a real, stable, well-known board (gitlab/palantir/ashby) and verify the adapter
 * against live traffic. Workday has no such universal endpoint - every tenant is served
 * from its own numbered pod hostname (see {@link WorkdaySiteConfig}), so there is no
 * "gitlab-style" default this repo can ship pre-configured and verified live; a real
 * deployment supplies its own site's {@code hostname}/{@code tenant}/{@code site} (found
 * from that company's own careers URL). {@code candidly.discovery.workday.sites} is
 * empty by default for exactly this reason, not just the usual off-by-default caution the
 * other adapters use. The request/response shapes below are Workday's documented, stable
 * public contract; every response record tolerates unknown fields
 * ({@code @JsonIgnoreProperties(ignoreUnknown = true)}) and a single bad posting's detail
 * fetch is caught per-item so it can't take down the rest of that site's poll.
 *
 * <p>Also unlike the other three, {@code additionalTargets} (candidate-tracked companies
 * from {@code AutopilotSettings.trackedCompanySlugs}) cannot be supported here: a tracked
 * "company" there is one flat string, but a Workday site needs three fields
 * (hostname/tenant/site) that don't fit that shape - so this adapter only ever polls the
 * sites in {@code application.yml}, and silently ignores {@code additionalTargets} rather
 * than guessing at a mapping.
 */
@Component
public class WorkdayDiscoveryAdapter implements JobBoardDiscoveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(WorkdayDiscoveryAdapter.class);
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern REMOTE_HINT = Pattern.compile("remote", Pattern.CASE_INSENSITIVE);

    private final RestClient workdayRestClient;
    private final WorkdayDiscoveryProperties properties;

    public WorkdayDiscoveryAdapter(RestClient workdayRestClient, WorkdayDiscoveryProperties properties) {
        this.workdayRestClient = workdayRestClient;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "workday";
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public List<DiscoveredJobPosting> poll(List<String> additionalTargets) {
        List<DiscoveredJobPosting> postings = new ArrayList<>();
        for (WorkdaySiteConfig site : properties.getSites()) {
            try {
                postings.addAll(pollSite(site));
            } catch (HttpStatusCodeException e) {
                log.warn("Workday site '{}/{}/{}' returned {}; skipping", site.getHostname(), site.getTenant(),
                        site.getSite(), e.getStatusCode().value());
            } catch (Exception e) {
                log.warn("Workday site '{}/{}/{}' poll failed: {}", site.getHostname(), site.getTenant(),
                        site.getSite(), e.getMessage());
            }
        }
        return postings;
    }

    private List<DiscoveredJobPosting> pollSite(WorkdaySiteConfig site) {
        String base = "https://" + site.getHostname() + "/wday/cxs/" + site.getTenant() + "/" + site.getSite();
        WorkdayJobsResponse response = workdayRestClient.post()
                .uri(URI.create(base + "/jobs"))
                .body(new WorkdayJobsRequest(properties.getMaxPostingsPerSite(), 0, ""))
                .retrieve()
                .body(WorkdayJobsResponse.class);

        if (response == null || response.jobPostings() == null) {
            return List.of();
        }

        List<DiscoveredJobPosting> result = new ArrayList<>();
        for (WorkdayJobPosting posting : response.jobPostings()) {
            try {
                result.add(toDiscovered(base, site, posting));
            } catch (Exception e) {
                log.warn("Workday posting '{}' on '{}/{}' failed to load; skipping", posting.externalPath(),
                        site.getTenant(), site.getSite());
            }
        }
        return result;
    }

    private DiscoveredJobPosting toDiscovered(String base, WorkdaySiteConfig site, WorkdayJobPosting posting) {
        String description = "";
        try {
            WorkdayJobDetailResponse detail = workdayRestClient.get()
                    .uri(URI.create(base + posting.externalPath()))
                    .retrieve()
                    .body(WorkdayJobDetailResponse.class);
            if (detail != null && detail.jobPostingInfo() != null) {
                description = stripHtml(detail.jobPostingInfo().jobDescription());
            }
        } catch (Exception e) {
            log.debug("Workday job detail fetch failed for '{}': {}", posting.externalPath(), e.getMessage());
        }
        String location = posting.locationsText() == null ? "" : posting.locationsText();
        return new DiscoveredJobPosting(
                "workday:" + site.getTenant() + ":" + site.getSite() + ":" + posting.externalPath(),
                site.getTenant(),
                posting.title(),
                location,
                REMOTE_HINT.matcher(location).find(),
                description,
                // No live-verified Workday tenant in this repo to confirm a comp field shape
                // against (see this adapter's own javadoc) - see DiscoveredJobPosting's javadoc.
                null, null, null);
    }

    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        String unescaped = HtmlUtils.htmlUnescape(html);
        return HTML_TAG.matcher(unescaped).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    private record WorkdayJobsRequest(int limit, int offset, String searchText) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WorkdayJobsResponse(int total, List<WorkdayJobPosting> jobPostings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WorkdayJobPosting(String title, String externalPath, String locationsText, String postedOn) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WorkdayJobDetailResponse(WorkdayJobPostingInfo jobPostingInfo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WorkdayJobPostingInfo(String jobDescription) {
    }
}
