package ai.candidly.career.pipeline;

import java.util.UUID;

import org.springframework.stereotype.Service;

import ai.candidly.career.domain.MatchScorecardRepository;
import ai.candidly.career.domain.TailoredArtifactStatus;
import ai.candidly.career.domain.TailoringJobRepository;

/**
 * The docs/04 application-pipeline funnel, computed from real rows across every stage
 * this repo actually tracks - no separate "Application" entity duplicating state that
 * {@code MatchScorecard}/{@code TailoringJob}/{@code Interview}/{@code Offer} already
 * hold. A stage count here is a derived view, not a source of truth.
 */
@Service
public class PipelineSummaryService {

    private final MatchScorecardRepository matchScorecardRepository;
    private final TailoringJobRepository tailoringJobRepository;
    private final InterviewRepository interviewRepository;
    private final OfferRepository offerRepository;

    public PipelineSummaryService(MatchScorecardRepository matchScorecardRepository,
            TailoringJobRepository tailoringJobRepository, InterviewRepository interviewRepository,
            OfferRepository offerRepository) {
        this.matchScorecardRepository = matchScorecardRepository;
        this.tailoringJobRepository = tailoringJobRepository;
        this.interviewRepository = interviewRepository;
        this.offerRepository = offerRepository;
    }

    public PipelineSummary summarize(UUID candidateId) {
        var scorecards = matchScorecardRepository.findByCandidateId(candidateId);
        var tailoringJobs = tailoringJobRepository.findByCandidateId(candidateId);
        var interviews = interviewRepository.findByCandidateId(candidateId);
        var offers = offerRepository.findByCandidateId(candidateId);

        long matched = scorecards.size();
        long shortlisted = scorecards.stream().filter(m -> m.isShortlisted()).count();
        long tailoring = tailoringJobs.size();
        long pendingApproval = tailoringJobs.stream()
                .filter(j -> j.getResultArtifact() != null
                        && (j.getResultArtifact().getStatus() == TailoredArtifactStatus.PENDING_APPROVAL
                                || j.getResultArtifact().getStatus() == TailoredArtifactStatus.NEEDS_HUMAN_REVIEW))
                .count();
        long approved = tailoringJobs.stream()
                .filter(j -> j.getResultArtifact() != null && j.getResultArtifact().getStatus() == TailoredArtifactStatus.APPROVED)
                .count();

        return new PipelineSummary(matched, shortlisted, tailoring, pendingApproval, approved, interviews.size(),
                offers.size());
    }

    public record PipelineSummary(long matched, long shortlisted, long tailoring, long pendingApproval, long approved,
            long interviews, long offers) {
    }
}
