package ai.candidly.career.discovery;

/**
 * A raw posting as a discovery adapter found it - deliberately the same shape
 * {@link ai.candidly.career.api.JobPostingIngestionService} already knows how to screen,
 * embed, and dedupe, so a discovered posting goes through exactly the same guardrail and
 * indexing path as one submitted by hand via the API.
 *
 * <p>{@code compMinMinorUnits}/{@code compMaxMinorUnits}/{@code currency} are all
 * nullable - live-verified during an end-to-end validation pass that not every source
 * publishes comp at all: GitLab's public Greenhouse board and Palantir's public Lever
 * board never include a compensation field in their default API response (confirmed by
 * inspecting the raw response keys, not just an empty value), so
 * {@link GreenhouseDiscoveryAdapter}/{@link LeverDiscoveryAdapter}/
 * {@link WorkdayDiscoveryAdapter} always pass {@code null} here - an honest gap in the
 * source data, not something this adapter layer can fix. {@link AshbyDiscoveryAdapter}
 * (with {@code ?includeCompensation=true}, undocumented but live-confirmed to work) and
 * {@link GenericJsonLdDiscoveryAdapter} (Schema.org {@code baseSalary}) do populate it
 * when the source states one.
 */
public record DiscoveredJobPosting(
        String sourceId,
        String company,
        String title,
        String location,
        boolean remote,
        String rawDescription,
        Long compMinMinorUnits,
        Long compMaxMinorUnits,
        String currency) {
}
