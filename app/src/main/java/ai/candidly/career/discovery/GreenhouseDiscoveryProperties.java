package ai.candidly.career.discovery;

import java.util.List;

public class GreenhouseDiscoveryProperties {

    private boolean enabled = false;
    private List<String> boardTokens = List.of();
    /** A real board can list hundreds of postings; each one costs a screening + embedding call downstream. */
    private int maxPostingsPerBoard = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getBoardTokens() {
        return boardTokens;
    }

    public void setBoardTokens(List<String> boardTokens) {
        this.boardTokens = boardTokens;
    }

    public int getMaxPostingsPerBoard() {
        return maxPostingsPerBoard;
    }

    public void setMaxPostingsPerBoard(int maxPostingsPerBoard) {
        this.maxPostingsPerBoard = maxPostingsPerBoard;
    }
}
