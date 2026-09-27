package ai.candidly.career.discovery;

import java.util.List;

public class AshbyDiscoveryProperties {

    private boolean enabled = false;
    private List<String> boardNames = List.of();
    /** A real Ashby board can list dozens of postings; each one costs a screening + embedding call downstream. */
    private int maxPostingsPerBoard = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getBoardNames() {
        return boardNames;
    }

    public void setBoardNames(List<String> boardNames) {
        this.boardNames = boardNames;
    }

    public int getMaxPostingsPerBoard() {
        return maxPostingsPerBoard;
    }

    public void setMaxPostingsPerBoard(int maxPostingsPerBoard) {
        this.maxPostingsPerBoard = maxPostingsPerBoard;
    }
}
