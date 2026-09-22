package ai.candidly.career.discovery;

/**
 * A raw posting as a discovery adapter found it - deliberately the same shape
 * {@link ai.candidly.career.api.JobPostingIngestionService} already knows how to screen,
 * embed, and dedupe, so a discovered posting goes through exactly the same guardrail and
 * indexing path as one submitted by hand via the API.
 */
public record DiscoveredJobPosting(
        String sourceId,
        String company,
        String title,
        String location,
        boolean remote,
        String rawDescription) {
}
