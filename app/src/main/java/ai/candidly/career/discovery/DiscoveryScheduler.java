package ai.candidly.career.discovery;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ai.candidly.career.api.JobPostingIngestionService;
import ai.candidly.career.api.JobPostingRequest;
import ai.candidly.career.autopilot.AutopilotSettings;
import ai.candidly.career.autopilot.AutopilotSettingsRepository;
import ai.candidly.career.autopilot.AutopilotStatus;
import ai.candidly.career.domain.JobPosting;

/**
 * FR-1 discovery scheduling (docs/04): "poll on a schedule (hourly is reasonable)".
 * Every discovered posting goes through the exact same {@link JobPostingIngestionService}
 * path as a hand-submitted one - SHA-256 dedupe against (company, title, location,
 * sourceId), the prompt-injection guardrail screen, and embedding - so discovery gets no
 * special trust a manually-submitted posting wouldn't also get.
 *
 * <p>Every discovered posting that clears screening gets its skill/domain/experience
 * fields backfilled by {@code JobPostingExtractionService} (see
 * {@code JobPostingIngestionService}) - discovered postings are no longer permanently
 * empty on those fields the way they were before that extraction step existed.
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
    private final AutopilotSettingsRepository autopilotSettingsRepository;

    /** Real, in-memory (not persisted - reset on restart) per-adapter poll history, keyed
     * by {@link JobBoardDiscoveryAdapter#name()} - backs GET /api/discovery/sources. This
     * is genuine data from actual poll runs, never a stand-in for a board this app doesn't
     * actually have an adapter for. All four docs/04 FR-1 boards are implemented now
     * (Greenhouse, Lever, {@code AshbyDiscoveryAdapter}, {@code WorkdayDiscoveryAdapter}) -
     * see the latter's javadoc for the one real gap left: Workday has no single guessable
     * demo default the way gitlab/palantir/ashby do, so it ships with an empty configured
     * site list. A fifth adapter, {@code GenericJsonLdDiscoveryAdapter}, widens discovery
     * beyond named ATS platforms to any company career page that embeds Schema.org
     * {@code JobPosting} markup - see its own javadoc for what that could and couldn't
     * verify live. */
    private final Map<String, SourceStatus> lastPollByAdapter = new ConcurrentHashMap<>();

    public DiscoveryScheduler(List<JobBoardDiscoveryAdapter> adapters, JobPostingIngestionService ingestionService,
            AutopilotSettingsRepository autopilotSettingsRepository) {
        this.adapters = adapters;
        this.ingestionService = ingestionService;
        this.autopilotSettingsRepository = autopilotSettingsRepository;
    }

    /** One entry per registered {@link JobBoardDiscoveryAdapter}, reflecting its real enabled flag
     * and (if it has ever run) the outcome of its most recent poll - no fabricated boards or counts. */
    public List<SourceStatus> sourceStatuses() {
        return adapters.stream()
                .map(a -> {
                    SourceStatus last = lastPollByAdapter.get(a.name());
                    return new SourceStatus(a.name(), a.isEnabled(),
                            last == null ? null : last.lastPolledAt(),
                            last == null ? 0 : last.lastDiscovered(),
                            last == null ? 0 : last.lastIngested());
                })
                .toList();
    }

    @Scheduled(fixedRateString = "${candidly.discovery.poll-interval-seconds:3600}000", initialDelay = 60_000)
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
            List<DiscoveredJobPosting> found = adapter.poll(trackedTargetsFor(adapter.name()));
            int adapterIngested = 0;
            discovered += found.size();
            for (DiscoveredJobPosting posting : found) {
                try {
                    JobPosting result = ingestionService.ingest(toRequest(posting));
                    ingested++;
                    adapterIngested++;
                    log.debug("[{}] ingested {} ({})", adapter.name(), posting.title(), result.getScreeningDecision());
                } catch (Exception e) {
                    log.warn("[{}] failed to ingest {}: {}", adapter.name(), posting.sourceId(), e.getMessage());
                }
            }
            lastPollByAdapter.put(adapter.name(),
                    new SourceStatus(adapter.name(), adapter.isEnabled(), Instant.now(), found.size(), adapterIngested));
        }
        log.info("Discovery run: {} postings found, {} ingested/deduped", discovered, ingested);
        return new DiscoveryRunResult(discovered, ingested);
    }

    /** Every RUNNING candidate's tracked companies for this adapter (namespace-prefix
     * stripped) - PAUSED/STOPPED candidates don't expand what gets polled. Only the
     * manual {@link #pollNow()} path reaches a disabled adapter at all; a RUNNING
     * candidate's tracked company on a globally-disabled adapter still waits for that. */
    private List<String> trackedTargetsFor(String adapterName) {
        String prefix = adapterName + ":";
        return autopilotSettingsRepository.findByStatus(AutopilotStatus.RUNNING).stream()
                .map(AutopilotSettings::getTrackedCompanySlugs)
                .flatMap(Set::stream)
                .filter(slug -> slug.startsWith(prefix))
                .map(slug -> slug.substring(prefix.length()))
                .distinct()
                .toList();
    }

    private JobPostingRequest toRequest(DiscoveredJobPosting posting) {
        return new JobPostingRequest(
                posting.sourceId(),
                posting.company(),
                posting.title(),
                posting.location() == null || posting.location().isBlank() ? "Unspecified" : posting.location(),
                posting.remote(),
                posting.compMinMinorUnits(),
                posting.compMaxMinorUnits(),
                posting.currency(),
                Set.of(),
                Set.of(),
                Set.of(),
                null,
                0,
                posting.rawDescription().isBlank() ? "(no description provided)" : posting.rawDescription());
    }

    public record DiscoveryRunResult(int discovered, int ingested) {
    }

    /** {@code lastPolledAt} is null if this adapter hasn't run in this process yet. */
    public record SourceStatus(String name, boolean enabled, Instant lastPolledAt, int lastDiscovered, int lastIngested) {
    }
}
