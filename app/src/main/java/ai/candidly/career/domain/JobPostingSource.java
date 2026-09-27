package ai.candidly.career.domain;

/**
 * Which discovery adapter found this posting - GREENHOUSE/LEVER/ASHBY/WORKDAY for the
 * four named docs/04 FR-1 adapters, JSONLD for the fifth, generically-shaped
 * {@code GenericJsonLdDiscoveryAdapter} (Schema.org {@code JobPosting} markup on a
 * company's own career page, not a named ATS), MANUAL for anything submitted directly
 * via {@code POST /api/job-postings} rather than through a
 * {@code JobBoardDiscoveryAdapter}.
 */
public enum JobPostingSource {
    GREENHOUSE,
    LEVER,
    ASHBY,
    WORKDAY,
    JSONLD,
    MANUAL
}
