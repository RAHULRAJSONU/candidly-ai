package ai.candidly.career.discovery;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ai.candidly.career.api.JobPostingIngestionService;
import ai.candidly.career.api.JobPostingRequest;
import ai.candidly.career.domain.JobPosting;

/**
 * FR-1 discovery scheduling (docs/04): "poll on a schedule (hourly is reasonable)".
 * Every discovered posting goes through the exact same {@link JobPostingIngestionService}
 * path as a hand-submitted one - SHA-256 dedupe against (company, title, location,
 * sourceId), the prompt-injection guardrail screen, and embedding - so discovery gets no
 * special trust a manually-submitted posting wouldn't also get.
 *
 * <p>Skill/domain/experience fields aren't extractable from a board's free-text
 * description without another judgment step this slice doesn't build yet, so discovered
 * postings carry empty mandatory/preferred skill sets and no domain - which the
 * eligibility gate and composite scorer already treat as "no constraint stated" rather
 * than failing anything.
 *
 * <p>Each adapter's own {@link JobBoardDiscoveryAdapter#isEnabled()} gates whether the
 * automatic hourly {@link #pollScheduled()} run includes it - added when
 * {@code LeverDiscoveryAdapter} joined {@code GreenhouseDiscoveryAdapter} so one board's
 * opt-in doesn't silently start polling every other configured board too. The manual
 * {@link #pollNow()} trigger (also reachable via {@code POST /api/discovery/poll})
 * ignores the flag entirely and always runs every registered adapter, same as before
 * this flag existed.
 */
@Component
public class DiscoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryScheduler.class);

    private final List<JobBoardDiscoveryAdapter> adapters;
    private final JobPostingIngestionService ingestionService;

    public DiscoveryScheduler(List<JobBoardDiscoveryAdapter> adapters, JobPostingIngestionService ingestionService) {
        this.adapters = adapters;
        this.ingestionService = ingestionService;
    }

    @Scheduled(fixedRate = 3_600_000, initialDelay = 60_000)
    public void pollScheduled() {
        List<JobBoardDiscoveryAdapter> enabled = adapters.stream().filter(JobBoardDiscoveryAdapter::isEnabled).toList();
        if (enabled.isEmpty()) {
            return;
        }
        poll(enabled);
    }

    /** Runs every configured adapter once regardless of its enabled flag, ingesting whatever it finds. */
    public DiscoveryRunResult pollNow() {
        return poll(adapters);
    }

    private DiscoveryRunResult poll(List<JobBoardDiscoveryAdapter> adaptersToRun) {
        int discovered = 0;
        int ingested = 0;
        for (JobBoardDiscoveryAdapter adapter : adaptersToRun) {
            List<DiscoveredJobPosting> found = adapter.poll();
            discovered += found.size();
            for (DiscoveredJobPosting posting : found) {
                try {
                    JobPosting result = ingestionService.ingest(toRequest(posting));
                    ingested++;
                    log.debug("[{}] ingested {} ({})", adapter.name(), posting.title(), result.getScreeningDecision());
                } catch (Exception e) {
                    log.warn("[{}] failed to ingest {}: {}", adapter.name(), posting.sourceId(), e.getMessage());
                }
            }
        }
        log.info("Discovery run: {} postings found, {} ingested/deduped", discovered, ingested);
        return new DiscoveryRunResult(discovered, ingested);
    }

    private JobPostingRequest toRequest(DiscoveredJobPosting posting) {
        return new JobPostingRequest(
                posting.sourceId(),
                posting.company(),
                posting.title(),
                posting.location() == null || posting.location().isBlank() ? "Unspecified" : posting.location(),
                posting.remote(),
                null,
                null,
                Set.of(),
                Set.of(),
                Set.of(),
                null,
                0,
                posting.rawDescription().isBlank() ? "(no description provided)" : posting.rawDescription());
    }

    public record DiscoveryRunResult(int discovered, int ingested) {
    }
}
