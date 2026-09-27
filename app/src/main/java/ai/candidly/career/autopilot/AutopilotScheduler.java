package ai.candidly.career.autopilot;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs one Autopilot cycle for every candidate whose {@link AutopilotSettings#getStatus()}
 * is {@link AutopilotStatus#RUNNING} and whose {@code nextRunAt} has passed - same
 * off-by-default-until-a-candidate-opts-in shape as {@code DiscoveryScheduler}, except the
 * opt-in here is per-candidate (via {@code POST .../autopilot/start}) rather than a global
 * config flag, since Autopilot only ever acts on one candidate's own data.
 */
@Component
public class AutopilotScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutopilotScheduler.class);

    private final AutopilotSettingsRepository settingsRepository;
    private final AutopilotService autopilotService;

    public AutopilotScheduler(AutopilotSettingsRepository settingsRepository, AutopilotService autopilotService) {
        this.settingsRepository = settingsRepository;
        this.autopilotService = autopilotService;
    }

    @Scheduled(fixedRateString = "${candidly.autopilot.scheduler-interval-seconds:300}000", initialDelay = 60_000)
    public void pollScheduled() {
        List<AutopilotSettings> running = settingsRepository.findByStatus(AutopilotStatus.RUNNING);
        Instant now = Instant.now();
        for (AutopilotSettings settings : running) {
            if (settings.getNextRunAt() == null || !settings.getNextRunAt().isAfter(now)) {
                try {
                    autopilotService.runCycle(settings.getCandidateId());
                } catch (Exception e) {
                    log.warn("Autopilot cycle failed for candidate {}: {}", settings.getCandidateId(), e.getMessage());
                }
            }
        }
    }
}
