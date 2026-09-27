package ai.candidly.career.tailoring;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.TailoringJob;
import ai.candidly.career.domain.TailoringJobRepository;
import ai.candidly.career.domain.TailoringJobStatus;
import ai.candidly.career.settings.CandidateSettingsService;

/**
 * Submission half of async tailoring (docs/03 §4): creates/reuses the {@link
 * TailoringJob} row and hands off to {@link TailoringAsyncExecutor} - a separate bean,
 * so the {@code @Async} dispatch actually goes through Spring's proxy.
 */
@Service
public class TailoringJobService {

    private final TailoringJobRepository tailoringJobRepository;
    private final TailoringAsyncExecutor asyncExecutor;
    private final CandidateSettingsService settingsService;

    public TailoringJobService(TailoringJobRepository tailoringJobRepository, TailoringAsyncExecutor asyncExecutor,
            CandidateSettingsService settingsService) {
        this.tailoringJobRepository = tailoringJobRepository;
        this.asyncExecutor = asyncExecutor;
        this.settingsService = settingsService;
    }

    /** @throws CandidateSettingsService.AiFeatureDisabledException if the candidate has
     * turned AI resume tailoring off on the Settings page - see {@link
     * ai.candidly.career.api.TailoringController#tailor} for the HTTP mapping. */
    @Transactional
    public TailoringJob submit(Candidate candidate, JobPosting job) {
        settingsService.requireResumeTailoringEnabled(candidate.getId());
        var existing = tailoringJobRepository.findByCandidateIdAndJobPostingId(candidate.getId(), job.getId());
        if (existing.isPresent()
                && (existing.get().getStatus() == TailoringJobStatus.QUEUED
                        || existing.get().getStatus() == TailoringJobStatus.RUNNING)) {
            return existing.get(); // already in flight - don't double-submit
        }
        // A prior COMPLETED/FAILED run for this pair is being retried: replace it. Force
        // the delete to flush before the insert - see MatchOrchestratorService for why
        // (Hibernate flushes inserts before deletes within one flush by default).
        existing.ifPresent(job1 -> {
            tailoringJobRepository.delete(job1);
            tailoringJobRepository.flush();
        });

        TailoringJob queued = tailoringJobRepository.save(new TailoringJob(candidate, job));

        // Defer the async kickoff until this transaction actually commits - otherwise
        // the background thread can call findById() on a row that isn't visible yet
        // (this method's transaction hasn't committed while it's still running).
        java.util.UUID queuedId = queued.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                asyncExecutor.run(queuedId);
            }
        });

        return queued;
    }
}
