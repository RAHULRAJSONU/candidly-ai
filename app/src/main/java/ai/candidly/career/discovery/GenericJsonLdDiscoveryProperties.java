package ai.candidly.career.discovery;

import java.util.ArrayList;
import java.util.List;

/**
 * Config for {@link GenericJsonLdDiscoveryAdapter} - unlike the other four adapters'
 * one-fixed-global-host shape, each entry in {@link #careerPageUrls} is itself a full,
 * individually-chosen URL on a different company's own domain, since Schema.org
 * {@code JobPosting} markup has no single syndication endpoint to poll.
 */
public class GenericJsonLdDiscoveryProperties {

    private boolean enabled = false;
    private List<String> careerPageUrls = new ArrayList<>();
    private int maxPostingsPerPage = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getCareerPageUrls() {
        return careerPageUrls;
    }

    public void setCareerPageUrls(List<String> careerPageUrls) {
        this.careerPageUrls = careerPageUrls;
    }

    public int getMaxPostingsPerPage() {
        return maxPostingsPerPage;
    }

    public void setMaxPostingsPerPage(int maxPostingsPerPage) {
        this.maxPostingsPerPage = maxPostingsPerPage;
    }
}
