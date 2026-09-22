package ai.candidly.career.tailoring;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateExperienceRepository;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.TailoredArtifact;
import ai.candidly.career.domain.TailoredArtifactRepository;
import ai.candidly.career.domain.TailoredArtifactStatus;

/**
 * The evaluator-optimizer loop (docs/00 §3, docs/03 §4-5): generate, run the
 * deterministic verifier (the invariant) and the TypeSafe tone critic (advisory) in
 * parallel-in-spirit over the same batch of bullets, and regenerate with concrete
 * feedback up to maxCriticLoops times. What survives after the last loop is what ships;
 * anything still unresolved is dropped and the artifact is flagged for human review
 * rather than silently including an unbacked or overstated claim.
 *
 * <p>Simplification vs. docs/03 §4: this runs synchronously inside the request rather
 * than as an async worker off a SHORTLISTED event - there is no message queue in this
 * slice yet. The 15-40s latency budget and WebSocket progress reporting are Phase 3
 * concerns once this moves off the request thread.
 */
@Service
public class ResumeTailoringService {

    private final GroundedResumeGenerator generator;
    private final DeterministicGroundingVerifier groundingVerifier;
    private final ClaimToneVerifier toneVerifier;
    private final CandidateExperienceRepository experienceRepository;
    private final TailoredArtifactRepository artifactRepository;
    private final int maxCriticLoops;

    public ResumeTailoringService(GroundedResumeGenerator generator,
            DeterministicGroundingVerifier groundingVerifier,
            ClaimToneVerifier toneVerifier,
            CandidateExperienceRepository experienceRepository,
            TailoredArtifactRepository artifactRepository,
            @Value("${candidly.tailoring.max-critic-loops}") int maxCriticLoops) {
        this.generator = generator;
        this.groundingVerifier = groundingVerifier;
        this.toneVerifier = toneVerifier;
        this.experienceRepository = experienceRepository;
        this.artifactRepository = artifactRepository;
        this.maxCriticLoops = maxCriticLoops;
    }

    @Transactional
    public TailoredArtifact tailor(Candidate candidate, JobPosting job) {
        var experiences = experienceRepository.findByCandidateId(candidate.getId());
        if (experiences.isEmpty()) {
            throw new IllegalStateException("Candidate has no verified experience to ground a resume in");
        }

        List<String> acceptedBullets = new ArrayList<>();
        List<String> lastRejections = List.of();
        String feedback = null;
        int loopsUsed = 0;

        for (int loop = 1; loop <= maxCriticLoops; loop++) {
            loopsUsed = loop;
            List<String> bullets = generator.generateBullets(experiences, job, feedback);

            List<DeterministicGroundingVerifier.VerifiedBullet> groundingResults = groundingVerifier.verify(bullets, experiences);
            List<String> groundedBullets = groundingResults.stream()
                    .filter(DeterministicGroundingVerifier.VerifiedBullet::passed)
                    .map(DeterministicGroundingVerifier.VerifiedBullet::text)
                    .toList();

            List<ClaimToneVerifier.ToneFinding> toneFindings = toneVerifier.check(groundedBullets);

            List<String> rejections = new ArrayList<>();
            for (var result : groundingResults) {
                if (!result.passed()) {
                    rejections.add(result.text() + " -- REJECTED (ungrounded): " + result.rejectionReason());
                }
            }
            for (var finding : toneFindings) {
                if (finding.flagged()) {
                    rejections.add(finding.bullet() + " -- REJECTED (overstated, p=%.2f): rephrase as a literal, unembellished statement of the verified fact"
                            .formatted(finding.overstatementProbability()));
                } else {
                    acceptedBullets.add(finding.bullet());
                }
            }

            lastRejections = rejections;
            if (rejections.isEmpty() || loop == maxCriticLoops) {
                // Either fully grounded, or out of retries - what's accepted right now is final.
                break;
            }
            feedback = String.join("\n", rejections);
            acceptedBullets.clear(); // next loop regenerates from scratch; don't carry over a stale partial set
        }

        boolean groundingPassed = lastRejections.isEmpty();
        TailoredArtifactStatus status = groundingPassed
                ? TailoredArtifactStatus.PENDING_APPROVAL
                : TailoredArtifactStatus.NEEDS_HUMAN_REVIEW;

        TailoredArtifact artifact = new TailoredArtifact(candidate, job, String.join("\n", acceptedBullets),
                loopsUsed, groundingPassed, lastRejections, status);
        artifact.setCoverLetterContent(generateGroundedCoverLetter(experiences, job));
        artifact.setScreeningAnswers(generateGroundedScreeningAnswers(experiences, job, candidate));
        return artifactRepository.save(artifact);
    }

    /**
     * Single-pass grounded generation (no critic loop) for the cover letter: it's a
     * secondary artifact alongside the resume bullets, so this reuses the same
     * deterministic verifier but drops any ungrounded sentence rather than retrying -
     * an empty result just means the panel is omitted client-side.
     */
    private String generateGroundedCoverLetter(List<ai.candidly.career.domain.CandidateExperience> experiences, JobPosting job) {
        String raw = generator.generateCoverLetter(experiences, job);
        List<String> sentences = List.of(raw.split("(?<=[.!?])\\s+"));
        List<String> grounded = groundingVerifier.verify(sentences, experiences).stream()
                .filter(DeterministicGroundingVerifier.VerifiedBullet::passed)
                .map(DeterministicGroundingVerifier.VerifiedBullet::text)
                .toList();
        return String.join(" ", grounded);
    }

    private List<String> generateGroundedScreeningAnswers(List<ai.candidly.career.domain.CandidateExperience> experiences,
            JobPosting job, Candidate candidate) {
        List<String> answers = generator.generateScreeningAnswers(experiences, job, candidate);
        List<String> out = new ArrayList<>();
        List<String> questions = GroundedResumeGenerator.STANDARD_SCREENING_QUESTIONS;
        List<DeterministicGroundingVerifier.VerifiedBullet> results = groundingVerifier.verify(answers, experiences);
        for (int i = 0; i < results.size() && i < questions.size(); i++) {
            var result = results.get(i);
            String answerText = result.passed() ? result.text()
                    : "Unable to auto-generate a grounded answer for this question - please answer manually.";
            out.add(questions.get(i) + " || " + answerText);
        }
        return out;
    }
}
