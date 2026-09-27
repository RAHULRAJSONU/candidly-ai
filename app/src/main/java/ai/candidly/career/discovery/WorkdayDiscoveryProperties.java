package ai.candidly.career.discovery;

import java.util.List;

public class WorkdayDiscoveryProperties {

    private boolean enabled = false;
    private List<WorkdaySiteConfig> sites = List.of();
    private int maxPostingsPerSite = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<WorkdaySiteConfig> getSites() {
        return sites;
    }

    public void setSites(List<WorkdaySiteConfig> sites) {
        this.sites = sites;
    }

    public int getMaxPostingsPerSite() {
        return maxPostingsPerSite;
    }

    public void setMaxPostingsPerSite(int maxPostingsPerSite) {
        this.maxPostingsPerSite = maxPostingsPerSite;
    }
}
