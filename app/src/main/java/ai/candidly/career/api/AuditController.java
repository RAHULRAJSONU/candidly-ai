package ai.candidly.career.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatusCode;

import ai.candidly.career.audit.AuditEvent;
import ai.candidly.career.audit.AuditEventRepository;
import ai.candidly.career.audit.AuditLedgerService;
import ai.candidly.career.demographics.BiasAuditReportService;

/**
 * Read side of the audit ledger (docs/02 §4.3 LL144 bias-audit data, §4.4 adverse-action
 * reasons): a bias auditor or a candidate exercising an access request reads through
 * here, they never touch {@code AuditLedgerService.record} directly.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditEventRepository repository;
    private final AuditLedgerService ledgerService;
    private final BiasAuditReportService biasAuditReportService;

    public AuditController(AuditEventRepository repository, AuditLedgerService ledgerService,
            BiasAuditReportService biasAuditReportService) {
        this.repository = repository;
        this.ledgerService = ledgerService;
        this.biasAuditReportService = biasAuditReportService;
    }

    /** NYC Local Law 144 annual independent bias audit data (docs/02 §4.3, docs/03 fairness metrics). */
    @GetMapping("/bias-report")
    public BiasAuditReportService.BiasAuditReport biasReport() {
        return biasAuditReportService.generate();
    }

    @GetMapping("/events/subject/{subjectId}")
    public List<AuditEvent> forSubject(@PathVariable UUID subjectId) {
        return repository.findBySubjectIdOrderByOccurredAtAsc(subjectId);
    }

    @GetMapping("/events")
    public List<AuditEvent> all() {
        return repository.findAllByOrderByOccurredAtAscIdAsc();
    }

    /** Recomputes the hash chain; {@code intact: false} names the first row that fails to verify. */
    @GetMapping("/verify")
    public Map<String, Object> verify() {
        return ledgerService.verifyChain()
                .<Map<String, Object>>map(badId -> Map.of("intact", false, "firstBrokenEventId", badId))
                .orElseGet(() -> Map.of("intact", true));
    }

    /**
     * Manual retention trigger (docs/02 §2 storage limitation), same "manual poll"
     * shape as {@code DiscoveryController} rather than an automatic schedule - purging
     * the ledger is consequential enough to want an explicit call, not a silent cron
     * job, at this stage. Refuses (409) if the chain isn't currently intact; see
     * {@code AuditLedgerService.purgeOlderThan}'s javadoc for why.
     */
    @PostMapping("/retention/purge")
    public Map<String, Object> purge(@RequestParam(defaultValue = "365") long olderThanDays) {
        try {
            int purged = ledgerService.purgeOlderThan(Instant.now().minus(olderThanDays, ChronoUnit.DAYS));
            return Map.of("purged", purged);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409), e.getMessage());
        }
    }
}
