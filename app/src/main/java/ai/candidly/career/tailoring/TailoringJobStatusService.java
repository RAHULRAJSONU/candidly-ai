package ai.candidly.career.tailoring;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.candidly.career.audit.AuditEventType;
import ai.candidly.career.audit.AuditLedgerService;
import ai.candidly.career.domain.TailoredArtifact;
import ai.candidly.career.domain.TailoringJob;
import ai.candidly.career.domain.TailoringJobRepository;

/**
 * The status-write half of {@link TailoringAsyncExecutor}, split into its own bean for
 * the same reason {@code TailoringJobService}/{@code TailoringAsyncExecutor} are split:
 * {@code @Transactional} (like {@code @Async}) only takes effect through the Spring
 * proxy, so calling these methods from {@code this} inside {@code TailoringAsyncExecutor
 * .run} would silently run with no transaction at all - which is exactly what happened
 * here before the split (caught because {@link AuditLedgerService#record} enforces
 * {@code Propagation.MANDATORY} and threw "No existing transaction found" the moment it
 * was added to {@link #markCompleted}, surfacing a pre-existing bug in status persistence
 * that had gone unnoticed only because each repository call was quietly opening its own
 * one-statement transaction).
 */
@Service
public class TailoringJobStatusService {

    private final TailoringJobRepository tailoringJobRepository;
    private final AuditLedgerService auditLedgerService;

    public TailoringJobStatusService(TailoringJobRepository tailoringJobRepository, AuditLedgerService auditLedgerService) {
        this.tailoringJobRepository = tailoringJobRepository;
        this.auditLedgerService = auditLedgerService;
    }

    @Transactional
    public void markRunning(TailoringJob job) {
        job.markRunning();
        tailoringJobRepository.save(job);
    }

    @Transactional
    public void markCompleted(UUID jobId, TailoredArtifact artifact) {
        TailoringJob job = tailoringJobRepository.findById(jobId).orElseThrow();
        job.markCompleted(artifact);
        tailoringJobRepository.save(job);
        auditLedgerService.record(AuditEventType.TAILORED_ARTIFACT_GENERATED, job.getCandidate().getId(),
                "job=%s artifact=%s criticLoops=%d groundingPassed=%s".formatted(job.getJobPosting().getId(),
                        artifact.getId(), artifact.getCriticLoopsUsed(), artifact.isGroundingPassed()));
    }

    @Transactional
    public void markFailed(UUID jobId, String errorMessage) {
        TailoringJob job = tailoringJobRepository.findById(jobId).orElseThrow();
        job.markFailed(errorMessage);
        tailoringJobRepository.save(job);
    }
}
