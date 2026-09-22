package ai.candidly.career.autopilot;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.candidly.career.audit.AuditEvent;

@RestController
@RequestMapping("/api/candidates/{candidateId}/autopilot")
public class AutopilotController {

    private final AutopilotService autopilotService;

    public AutopilotController(AutopilotService autopilotService) {
        this.autopilotService = autopilotService;
    }

    @GetMapping("/settings")
    public AutopilotSettings getSettings(@PathVariable UUID candidateId) {
        return autopilotService.getOrCreateSettings(candidateId);
    }

    @PutMapping("/settings")
    public AutopilotSettings updateSettings(@PathVariable UUID candidateId, @RequestBody AutopilotSettingsRequest request) {
        return autopilotService.updateSettings(candidateId, request);
    }

    @GetMapping("/status")
    public AutopilotStatusView status(@PathVariable UUID candidateId) {
        AutopilotSettings settings = autopilotService.getOrCreateSettings(candidateId);
        return new AutopilotStatusView(settings, autopilotService.stats(candidateId));
    }

    @PostMapping("/start")
    public AutopilotSettings start(@PathVariable UUID candidateId) {
        return autopilotService.start(candidateId);
    }

    @PostMapping("/pause")
    public AutopilotSettings pause(@PathVariable UUID candidateId) {
        return autopilotService.pause(candidateId);
    }

    @PostMapping("/stop")
    public AutopilotSettings stop(@PathVariable UUID candidateId) {
        return autopilotService.stop(candidateId);
    }

    /** Manual "run now" - runs one cycle immediately regardless of schedule, same as the scheduler would. */
    @PostMapping("/run-now")
    public AutopilotSettings runNow(@PathVariable UUID candidateId) {
        autopilotService.runCycle(candidateId);
        return autopilotService.getOrCreateSettings(candidateId);
    }

    @GetMapping("/activity")
    public List<AuditEvent> activity(@PathVariable UUID candidateId, @RequestParam(defaultValue = "50") int limit) {
        return autopilotService.activity(candidateId, limit);
    }

    public record AutopilotStatusView(AutopilotSettings settings, AutopilotService.AutopilotStats stats) {
    }
}
