package ai.candidly.career.discovery;

import java.util.List;

public class LeverDiscoveryProperties {

    private boolean enabled = false;
    private List<String> companySlugs = List.of();
    /** A real Lever site can list hundreds of postings; each one costs a screening + embedding call downstream. */
    private int maxPostingsPerBoard = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getCompanySlugs() {
        return companySlugs;
    }

    public void setCompanySlugs(List<String> companySlugs) {
        this.companySlugs = companySlugs;
    }

    public int getMaxPostingsPerBoard() {
        return maxPostingsPerBoard;
    }

    public void setMaxPostingsPerBoard(int maxPostingsPerBoard) {
        this.maxPostingsPerBoard = maxPostingsPerBoard;
    }
}
