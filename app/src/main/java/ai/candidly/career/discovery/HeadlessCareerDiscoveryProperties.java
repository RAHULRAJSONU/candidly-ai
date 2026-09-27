package ai.candidly.career.discovery;

import java.util.ArrayList;
import java.util.List;

/**
 * Config for {@link HeadlessCareerPageDiscoveryAdapter}. Each {@link #portals} entry is one
 * company's own career-listing page - never linkedin.com or any other third-party job board;
 * see the adapter's own javadoc for why that boundary is enforced in code, not just by
 * convention.
 */
public class HeadlessCareerDiscoveryProperties {

    private boolean enabled = false;
    private List<Portal> portals = new ArrayList<>();
    private int maxPostingsPerPortal = 10;
    private int navigationTimeoutMillis = 30000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<Portal> getPortals() {
        return portals;
    }

    public void setPortals(List<Portal> portals) {
        this.portals = portals;
    }

    public int getMaxPostingsPerPortal() {
        return maxPostingsPerPortal;
    }

    public void setMaxPostingsPerPortal(int maxPostingsPerPortal) {
        this.maxPostingsPerPortal = maxPostingsPerPortal;
    }

    public int getNavigationTimeoutMillis() {
        return navigationTimeoutMillis;
    }

    public void setNavigationTimeoutMillis(int navigationTimeoutMillis) {
        this.navigationTimeoutMillis = navigationTimeoutMillis;
    }

    /** One company's career-listing page. {@code jobLinkPattern} is a regex matched against
     * each anchor's absolute href on that listing page to find individual job-detail links; if
     * left blank, a default heuristic (href containing "job", "career", "position", or "req")
     * on the same host is used instead. {@code companyName} is the fallback used when a job
     * detail page's own JSON-LD (if any) has no {@code hiringOrganization.name}. */
    public static class Portal {
        private String listingUrl;
        private String companyName;
        private String jobLinkPattern;

        public String getListingUrl() {
            return listingUrl;
        }

        public void setListingUrl(String listingUrl) {
            this.listingUrl = listingUrl;
        }

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }

        public String getJobLinkPattern() {
            return jobLinkPattern;
        }

        public void setJobLinkPattern(String jobLinkPattern) {
            this.jobLinkPattern = jobLinkPattern;
        }
    }
}
