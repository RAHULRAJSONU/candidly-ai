package ai.candidly.career.discovery;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import tools.jackson.databind.JsonNode;

/**
 * A sixth discovery source, deliberately scoped to one thing: a company's own career-listing
 * page, rendered with a real (headless) browser so postings that {@link
 * GenericJsonLdDiscoveryAdapter} can't see - because the page injects its job listings via
 * client-side JS rather than embedding them statically in the initial HTML response - become
 * reachable too. This is the honest fix for that adapter's own documented limitation (GitLab,
 * Meta, Figma, Notion, and most other real career pages render this way).
 *
 * <p><b>Never linkedin.com, by design, not just by configuration.</b> LinkedIn's Terms of
 * Service explicitly prohibit automated access/scraping regardless of authentication state, and
 * LinkedIn has pursued this legally (LinkedIn Corp. v. hiQ Labs). {@link #pollPortal} rejects
 * any configured {@code listingUrl} whose host is linkedin.com (or a subdomain of it) before
 * launching a browser against it at all - a code-level guardrail, not just an operator
 * convention, so a misconfigured entry can't silently start scraping LinkedIn.
 *
 * <p>Each poll: (1) render the configured listing page, (2) extract same-host anchor hrefs
 * matching {@code jobLinkPattern} (or a default job/career/position/req heuristic) as candidate
 * job-detail links, capped at {@code maxPostingsPerPortal}, (3) render each detail page and try
 * {@link JsonLdJobPostingParser} against it first (some client-rendered sites still inject
 * JSON-LD after load, e.g. via a hydration script) - falling back to a much lower-fidelity
 * title/description-only extraction from the page's own {@code <title>} and visible text when no
 * structured data is present at all, since a title-only match is still more useful than no
 * posting. That fallback never fabricates skill/comp data it didn't find.
 */
@Component
public class HeadlessCareerPageDiscoveryAdapter implements JobBoardDiscoveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(HeadlessCareerPageDiscoveryAdapter.class);
    private static final Pattern DEFAULT_JOB_LINK_HINT = Pattern.compile(
            "job|career|position|req", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMOTE_HINT = Pattern.compile("remote|telecommute", Pattern.CASE_INSENSITIVE);
    private static final String USER_AGENT = "candidly-ai-discovery/0.1 (+https://github.com/candidly-ai)";

    private final JsonLdJobPostingParser parser;
    private final HeadlessCareerDiscoveryProperties properties;

    public HeadlessCareerPageDiscoveryAdapter(JsonLdJobPostingParser parser, HeadlessCareerDiscoveryProperties properties) {
        this.parser = parser;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "headless";
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public List<DiscoveredJobPosting> poll(List<String> additionalTargets) {
        List<HeadlessCareerDiscoveryProperties.Portal> portals = new ArrayList<>(properties.getPortals());
        // additionalTargets (candidate-tracked URLs, namespace "headless:https://...") get no
        // company-name/link-pattern override - the default heuristics have to be enough.
        for (String target : additionalTargets) {
            HeadlessCareerDiscoveryProperties.Portal portal = new HeadlessCareerDiscoveryProperties.Portal();
            portal.setListingUrl(target);
            portals.add(portal);
        }
        if (portals.isEmpty()) {
            return List.of();
        }

        List<DiscoveredJobPosting> results = new ArrayList<>();
        try (Playwright playwright = Playwright.create()) {
            try (Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
                BrowserContext context = browser.newContext(new Browser.NewContextOptions().setUserAgent(USER_AGENT));
                for (HeadlessCareerDiscoveryProperties.Portal portal : portals) {
                    try {
                        results.addAll(pollPortal(context, portal));
                    } catch (Exception e) {
                        log.warn("Headless career page '{}' poll failed: {}", portal.getListingUrl(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            // Most commonly: Playwright's browser binary hasn't been installed on this machine
            // yet (a one-time `mvn -Dexec... playwright install chromium`, see CLAUDE.md) - this
            // adapter degrades to "found nothing" rather than failing the whole discovery poll.
            log.warn("Headless browser unavailable, skipping this adapter's poll: {}", e.getMessage());
        }
        return results;
    }

    private List<DiscoveredJobPosting> pollPortal(BrowserContext context, HeadlessCareerDiscoveryProperties.Portal portal) {
        String listingUrl = portal.getListingUrl();
        String host = hostOf(listingUrl);
        if (host == null || host.equalsIgnoreCase("linkedin.com") || host.toLowerCase().endsWith(".linkedin.com")) {
            log.warn("Refusing to poll '{}' - linkedin.com is out of scope for this adapter (see class javadoc)", listingUrl);
            return List.of();
        }

        Page listingPage = context.newPage();
        try {
            navigateAndSettle(listingPage, listingUrl);
            @SuppressWarnings("unchecked")
            List<String> hrefs = (List<String>) listingPage.evalOnSelectorAll("a", "els => els.map(e => e.href)");
            Set<String> detailUrls = jobDetailUrls(hrefs, host, listingUrl, portal.getJobLinkPattern());

            List<DiscoveredJobPosting> result = new ArrayList<>();
            for (String detailUrl : detailUrls) {
                if (result.size() >= properties.getMaxPostingsPerPortal()) {
                    break;
                }
                DiscoveredJobPosting posting = pollDetailPage(context, host, portal.getCompanyName(), detailUrl);
                if (posting != null) {
                    result.add(posting);
                }
            }
            if (result.isEmpty()) {
                log.debug("Headless career page '{}' yielded no job postings", listingUrl);
            }
            return result;
        } finally {
            listingPage.close();
        }
    }

    private Set<String> jobDetailUrls(List<String> hrefs, String host, String listingUrl, String configuredPattern) {
        Pattern pattern = configuredPattern != null && !configuredPattern.isBlank()
                ? Pattern.compile(configuredPattern, Pattern.CASE_INSENSITIVE)
                : DEFAULT_JOB_LINK_HINT;
        Set<String> detailUrls = new LinkedHashSet<>();
        for (String href : hrefs) {
            if (href == null || href.isBlank() || href.equalsIgnoreCase(listingUrl)) {
                continue;
            }
            String hrefHost = hostOf(href);
            if (hrefHost == null || !hrefHost.equalsIgnoreCase(host)) {
                continue;
            }
            if (pattern.matcher(href).find()) {
                detailUrls.add(href);
            }
        }
        return detailUrls;
    }

    private DiscoveredJobPosting pollDetailPage(BrowserContext context, String host, String companyName, String detailUrl) {
        Page detailPage = context.newPage();
        try {
            navigateAndSettle(detailPage, detailUrl);
            String html = detailPage.content();
            List<JsonNode> jobNodes = parser.extractJobPostingNodes(html);
            if (!jobNodes.isEmpty()) {
                JsonLdJobPostingParser.ParsedJobPosting fields = parser.toFields(
                        companyName != null ? companyName : host, jobNodes.get(0));
                return new DiscoveredJobPosting("headless:" + host + ":" + fields.identifierValue(),
                        fields.company(), fields.title(), fields.location(), fields.remote(), fields.description(),
                        fields.compMinMinorUnits(), fields.compMaxMinorUnits(), fields.currency());
            }
            return fallbackFromRenderedPage(detailPage, host, companyName, detailUrl);
        } finally {
            detailPage.close();
        }
    }

    /** No structured data at all on this rendered page - the honest fallback: title from the
     * page's own {@code <title>}, company from the configured portal name (or the host), and a
     * truncated slice of visible body text as the description. No skill/experience/comp fields
     * are guessed - {@code JobPostingExtractionService} runs its own TypeSafe extraction pass on
     * whatever description text this does capture, same as every other adapter's postings. */
    private DiscoveredJobPosting fallbackFromRenderedPage(Page detailPage, String host, String companyName, String detailUrl) {
        String title = detailPage.title();
        if (title == null || title.isBlank()) {
            return null;
        }
        String bodyText = detailPage.innerText("body");
        String description = bodyText == null ? "" : bodyText.strip().replaceAll("\\s+", " ");
        if (description.length() > 4000) {
            description = description.substring(0, 4000);
        }
        boolean remote = REMOTE_HINT.matcher(title + " " + description).find();
        String identifierValue = Integer.toHexString(detailUrl.hashCode());
        return new DiscoveredJobPosting("headless:" + host + ":" + identifierValue,
                companyName != null ? companyName : host, title.trim(), "", remote, description,
                null, null, null);
    }

    /** {@code WaitUntilState.NETWORKIDLE} sounds right for "let client-side JS finish
     * rendering" but is unreliable in practice - live-caught during this implementation:
     * both figma.com/careers and about.sourcegraph.com/jobs timed out after 30s waiting for
     * network idle, because real sites keep background analytics/polling connections open
     * indefinitely, so network activity never truly stops. {@code DOMCONTENTLOADED} plus a
     * short fixed settle delay is what actually works against real pages. */
    private void navigateAndSettle(Page page, String url) {
        page.navigate(url, new Page.NavigateOptions()
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(properties.getNavigationTimeoutMillis()));
        page.waitForTimeout(3000);
    }

    private String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }
}
