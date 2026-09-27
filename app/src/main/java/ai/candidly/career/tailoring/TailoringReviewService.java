package ai.candidly.career.tailoring;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.candidly.career.audit.AuditEventType;
import ai.candidly.career.audit.AuditLedgerService;
import ai.candidly.career.domain.MatchScorecard;
import ai.candidly.career.domain.MatchScorecardRepository;
import ai.candidly.career.domain.TailoredArtifact;
import ai.candidly.career.domain.TailoredArtifactRepository;
import ai.candidly.career.domain.TailoredArtifactStatus;

/**
 * The human-in-the-loop console's backend (docs/01 §2: "Human-in-the-Loop Approval
 * Console - DEFAULT path, not the exception"). A reviewer approves or rejects a
 * generated artifact; nothing here submits anything anywhere - approval only clears the
 * artifact to be handed to the candidate for their own session (docs/04 FR-5, consumer
 * path).
 *
 * <p>{@link #pendingReview()}/{@link #recentlyDecided()} attach each artifact's
 * {@code MatchScorecard} (skill/experience/semantic/domain sub-scores and the
 * deterministic {@code reasonCodes} - docs/02 §4.4 adverse-action explainability) so a
 * reviewer sees WHY this pair was shortlisted for tailoring in the first place, not just
 * the generated text - the same reason codes an adverse-action notice or an LL144
 * auditor would need are exactly what the human approving the artifact should see too.
 */
@Service
public class TailoringReviewService {

    private final TailoredArtifactRepository artifactRepository;
    private final MatchScorecardRepository matchScorecardRepository;
    private final AuditLedgerService auditLedgerService;

    public TailoringReviewService(TailoredArtifactRepository artifactRepository,
            MatchScorecardRepository matchScorecardRepository, AuditLedgerService auditLedgerService) {
        this.artifactRepository = artifactRepository;
        this.matchScorecardRepository = matchScorecardRepository;
        this.auditLedgerService = auditLedgerService;
    }

    public List<ArtifactReviewView> pendingReview() {
        return withScorecards(artifactRepository.findByStatusInOrderByGeneratedAtAsc(
                List.of(TailoredArtifactStatus.PENDING_APPROVAL, TailoredArtifactStatus.NEEDS_HUMAN_REVIEW)));
    }

    /** Already-decided artifacts, most recently reviewed first - review history/context for the console. */
    public List<ArtifactReviewView> recentlyDecided() {
        return withScorecards(artifactRepository.findByStatusInOrderByReviewedAtDesc(
                List.of(TailoredArtifactStatus.APPROVED, TailoredArtifactStatus.REJECTED)));
    }

    public ArtifactReviewView getById(UUID artifactId) {
        TailoredArtifact artifact = get(artifactId);
        return new ArtifactReviewView(artifact,
                matchScorecardRepository.findByCandidateIdAndJobPostingId(
                        artifact.getCandidate().getId(), artifact.getJobPosting().getId()).orElse(null));
    }

    /**
     * The Browser Assist flow's final step: the candidate confirms they personally
     * clicked Submit in their own authenticated browser session (docs/00's
     * no-auto-submission guarantee) - this only records that confirmation to the audit
     * ledger, it never fires an outbound request to any third-party ATS itself.
     */
    @Transactional
    public void recordCandidateSubmitted(UUID artifactId) {
        TailoredArtifact artifact = get(artifactId);
        auditLedgerService.record(ai.candidly.career.audit.AuditEventType.CANDIDATE_SUBMITTED_APPLICATION,
                artifact.getCandidate().getId(),
                "artifact=%s jobPosting=%s - candidate confirmed manual submission in their own browser session"
                        .formatted(artifactId, artifact.getJobPosting().getId()));
    }

    private List<ArtifactReviewView> withScorecards(List<TailoredArtifact> artifacts) {
        return artifacts.stream()
                .map(artifact -> new ArtifactReviewView(artifact,
                        matchScorecardRepository.findByCandidateIdAndJobPostingId(
                                artifact.getCandidate().getId(), artifact.getJobPosting().getId()).orElse(null)))
                .toList();
    }

    @Transactional
    public TailoredArtifact approve(UUID artifactId, String note) {
        TailoredArtifact artifact = get(artifactId);
        artifact.approve(note);
        TailoredArtifact saved = artifactRepository.save(artifact);
        auditLedgerService.record(AuditEventType.TAILORED_ARTIFACT_REVIEWED, artifact.getCandidate().getId(),
                "artifact=%s job=%s decision=APPROVED note=%s"
                        .formatted(artifactId, artifact.getJobPosting().getId(), note));
        return saved;
    }

    @Transactional
    public TailoredArtifact reject(UUID artifactId, String note) {
        TailoredArtifact artifact = get(artifactId);
        artifact.reject(note);
        TailoredArtifact saved = artifactRepository.save(artifact);
        auditLedgerService.record(AuditEventType.TAILORED_ARTIFACT_REVIEWED, artifact.getCandidate().getId(),
                "artifact=%s job=%s decision=REJECTED note=%s"
                        .formatted(artifactId, artifact.getJobPosting().getId(), note));
        return saved;
    }

    private TailoredArtifact get(UUID artifactId) {
        return artifactRepository.findById(artifactId)
                .orElseThrow(() -> new IllegalArgumentException("Tailored artifact not found: " + artifactId));
    }

    /** {@code matchScorecard} is null only if the underlying scorecard was since deleted (e.g. GDPR erasure). */
    public record ArtifactReviewView(TailoredArtifact artifact, MatchScorecard matchScorecard) {
    }
}
