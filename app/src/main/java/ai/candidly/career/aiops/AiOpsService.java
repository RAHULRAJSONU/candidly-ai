package ai.candidly.career.aiops;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import ai.candidly.career.domain.JobPostingRepository;
import ai.candidly.career.domain.MatchScorecard;
import ai.candidly.career.domain.MatchScorecardRepository;
import ai.candidly.career.domain.ScreeningDecision;
import ai.candidly.career.domain.TailoredArtifact;
import ai.candidly.career.domain.TailoredArtifactRepository;
import ai.candidly.career.domain.TailoredArtifactStatus;

/**
 * Aggregates real numbers already sitting in this database - grounding pass rate,
 * critic-loop usage, screening-decision distribution, composite-score average - for the
 * "AI Ops" view (see AiOpsSummary's javadoc for why this isn't a multi-model comparison).
 * Read-only, no TypeSafe/Groq/Jina calls of its own.
 */
@Service
public class AiOpsService {

    private final TailoredArtifactRepository artifactRepository;
    private final MatchScorecardRepository matchScorecardRepository;
    private final JobPostingRepository jobPostingRepository;

    public AiOpsService(TailoredArtifactRepository artifactRepository, MatchScorecardRepository matchScorecardRepository,
            JobPostingRepository jobPostingRepository) {
        this.artifactRepository = artifactRepository;
        this.matchScorecardRepository = matchScorecardRepository;
        this.jobPostingRepository = jobPostingRepository;
    }

    public AiOpsSummary summarize() {
        List<TailoredArtifact> artifacts = artifactRepository.findAll();
        List<MatchScorecard> scorecards = matchScorecardRepository.findAll();

        long totalArtifacts = artifacts.size();
        double groundingPassRate = totalArtifacts == 0 ? 0.0
                : artifacts.stream().filter(TailoredArtifact::isGroundingPassed).count() / (double) totalArtifacts;
        double avgCriticLoops = totalArtifacts == 0 ? 0.0
                : artifacts.stream().mapToInt(TailoredArtifact::getCriticLoopsUsed).average().orElse(0.0);
        double avgRejectedClaims = totalArtifacts == 0 ? 0.0
                : artifacts.stream().mapToInt(a -> a.getRejectedClaims().size()).average().orElse(0.0);

        double avgCompositeScore = scorecards.isEmpty() ? 0.0
                : scorecards.stream().mapToDouble(MatchScorecard::getCompositeScore).average().orElse(0.0);

        Map<String, Long> screeningCounts = new LinkedHashMap<>();
        for (ScreeningDecision d : ScreeningDecision.values()) {
            screeningCounts.put(d.name(), jobPostingRepository.countByScreeningDecision(d));
        }

        Map<TailoredArtifactStatus, Long> statusCountsByEnum = new EnumMap<>(TailoredArtifactStatus.class);
        for (TailoredArtifactStatus s : TailoredArtifactStatus.values()) {
            statusCountsByEnum.put(s, 0L);
        }
        for (TailoredArtifact a : artifacts) {
            statusCountsByEnum.merge(a.getStatus(), 1L, Long::sum);
        }
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        statusCountsByEnum.forEach((k, v) -> statusCounts.put(k.name(), v));

        return new AiOpsSummary(totalArtifacts, groundingPassRate, avgCriticLoops, avgRejectedClaims,
                scorecards.size(), avgCompositeScore, screeningCounts, statusCounts);
    }
}
