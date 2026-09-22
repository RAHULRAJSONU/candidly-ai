package ai.candidly.career.api;

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
}
