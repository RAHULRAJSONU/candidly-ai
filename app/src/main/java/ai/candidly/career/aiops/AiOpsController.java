package ai.candidly.career.aiops;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Backs the "AI Ops" screen - real, aggregated numbers, not a fabricated multi-model comparison (see AiOpsSummary). */
@RestController
@RequestMapping("/api/ai-ops")
public class AiOpsController {

    private final AiOpsService aiOpsService;
    private final InsightsService insightsService;

    public AiOpsController(AiOpsService aiOpsService, InsightsService insightsService) {
        this.aiOpsService = aiOpsService;
        this.insightsService = insightsService;
    }

    @GetMapping("/summary")
    public AiOpsSummary summary() {
        return aiOpsService.summarize();
    }

    /** "Autopilot Insights" panel data (plan Phase 3) - see InsightsService's javadoc. */
    @GetMapping("/insights")
    public InsightsSummary insights() {
        return insightsService.summarize();
    }
}
