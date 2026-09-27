package ai.candidly.career.discovery;

import java.util.List;

/**
 * One legitimate, unauthenticated public job-board GET source (docs/01 §3.1, docs/04
 * FR-1). Discovery is the honest half of Tier 5 - no evasion, no scraping past what a
 * board's own public API is designed to serve.
 */
public interface JobBoardDiscoveryAdapter {

    /** A short, stable name for logging/metrics - e.g. "greenhouse:gitlab". */
    String name();

    /**
     * Gates {@code DiscoveryScheduler}'s automatic hourly poll only - the manual
     * {@code POST /api/discovery/poll} trigger always calls every adapter's
     * {@link #poll()} regardless, same as before this flag existed for a single
     * adapter (see DiscoveryScheduler and CLAUDE.md).
     */
    boolean isEnabled();

    /** Polls this adapter's configured board list only. */
    default List<DiscoveredJobPosting> poll() {
        return poll(List.of());
    }

    /**
     * Polls the union of this adapter's configured board/company list and
     * {@code additionalTargets} (candidate-tracked companies from
     * {@code AutopilotSettings.trackedCompanySlugs}, resolved by {@code DiscoveryScheduler}
     * before calling this - each adapter recognizes only its own namespace prefix).
     */
    List<DiscoveredJobPosting> poll(List<String> additionalTargets);
}
