package ai.candidly.career.tailoring;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import ai.candidly.career.domain.TailoredArtifact;
import ai.candidly.career.domain.TailoringJob;
import ai.candidly.career.domain.TailoringJobRepository;

/**
 * The actual off-request-thread work (docs/03 §4). Deliberately a separate bean from
 * {@code TailoringJobService}: {@code @Async} only takes effect through the Spring
 * proxy, so calling this method from another method on the *same* bean would silently
 * run synchronously - splitting the "submit" and "run" halves into different beans is
 * what makes the async dispatch real.
 *
 * <p>The status writes are delegated to {@link TailoringJobStatusService}, a third bean,
 * for the identical reason: {@code @Transactional} needs the proxy too, and this class's
 * {@code run} method used to call {@code this.markCompleted(...)} directly, which never
 * actually opened a transaction (see that class's javadoc for how this was caught).
 */
@Component
public class TailoringAsyncExecutor {

    private final ResumeTailoringService resumeTailoringService;
    private final TailoringJobRepository tailoringJobRepository;
    private final TailoringJobStatusService statusService;

    public TailoringAsyncExecutor(ResumeTailoringService resumeTailoringService,
            TailoringJobRepository tailoringJobRepository,
            TailoringJobStatusService statusService) {
        this.resumeTailoringService = resumeTailoringService;
        this.tailoringJobRepository = tailoringJobRepository;
        this.statusService = statusService;
    }

    @Async("tailoringTaskExecutor")
    public void run(java.util.UUID tailoringJobId) {
        TailoringJob job = tailoringJobRepository.findById(tailoringJobId)
                .orElseThrow(() -> new IllegalStateException("TailoringJob vanished: " + tailoringJobId));

        statusService.markRunning(job);
        try {
            TailoredArtifact artifact = resumeTailoringService.tailor(job.getCandidate(), job.getJobPosting());
            statusService.markCompleted(tailoringJobId, artifact);
        } catch (Exception e) {
            statusService.markFailed(tailoringJobId, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
