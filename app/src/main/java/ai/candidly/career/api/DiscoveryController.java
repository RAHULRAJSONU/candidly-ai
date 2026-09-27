package ai.candidly.career.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.candidly.career.discovery.DiscoveryScheduler;

/** Manual trigger for the discovery poll that otherwise only runs hourly - see DiscoveryScheduler. */
@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {

    private final DiscoveryScheduler discoveryScheduler;

    public DiscoveryController(DiscoveryScheduler discoveryScheduler) {
        this.discoveryScheduler = discoveryScheduler;
    }

    @PostMapping("/poll")
    public DiscoveryScheduler.DiscoveryRunResult pollNow() {
        return discoveryScheduler.pollNow();
    }

    /** Real per-adapter status (enabled flag + last poll outcome) - only the sources this
     * app actually has an adapter for (Greenhouse, Lever, Ashby, Workday, and the generic
     * JSON-LD adapter); no fabricated entries, see DiscoveryScheduler's javadoc. */
    @GetMapping("/sources")
    public List<DiscoveryScheduler.SourceStatus> sources() {
        return discoveryScheduler.sourceStatuses();
    }
}
