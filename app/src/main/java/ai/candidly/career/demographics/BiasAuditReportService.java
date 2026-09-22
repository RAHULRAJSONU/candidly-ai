package ai.candidly.career.demographics;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import ai.candidly.career.domain.MatchScorecard;
import ai.candidly.career.domain.MatchScorecardRepository;

/**
 * NYC Local Law 144 (and the growing US state patchwork) annual independent bias audit
 * (docs/02 §4.3) and docs/03's fairness-metrics requirement: "score and advance-rate
 * parity across protected groups, computed on the *separately stored* demographic data
 * ... never let this data touch the scoring path." This is the one place in the codebase
 * where a {@code MatchScorecard} outcome and a candidate's voluntary self-identification
 * are ever joined, and it happens read-only, in memory, after both are already
 * committed - nothing here feeds back into matching.
 *
 * <p>Grouped independently by gender and by race/ethnicity (not the full intersectional
 * matrix real LL144 audits use) - a starting point to calibrate against real submission
 * volume per docs/03 §3, not a claim of regulatory completeness. Candidates with no
 * self-ID on file are bucketed under {@link CandidateDemographics#DECLINED} rather than
 * excluded, since a bias audit needs the decline rate visible too.
 */
@Service
public class BiasAuditReportService {

    private final MatchScorecardRepository matchScorecardRepository;
    private final CandidateDemographicsRepository demographicsRepository;

    public BiasAuditReportService(MatchScorecardRepository matchScorecardRepository,
            CandidateDemographicsRepository demographicsRepository) {
        this.matchScorecardRepository = matchScorecardRepository;
        this.demographicsRepository = demographicsRepository;
    }

    public BiasAuditReport generate() {
        List<MatchScorecard> scorecards = matchScorecardRepository.findAll();
        Map<UUID, CandidateDemographics> byCandidateId = demographicsRepository.findAll().stream()
                .collect(Collectors.toMap(CandidateDemographics::getCandidateId, Function.identity()));

        return new BiasAuditReport(
                groupBy(scorecards, byCandidateId, CandidateDemographics::getGender),
                groupBy(scorecards, byCandidateId, CandidateDemographics::getRaceEthnicity),
                Instant.now());
    }

    private List<GroupStat> groupBy(List<MatchScorecard> scorecards, Map<UUID, CandidateDemographics> byCandidateId,
            Function<CandidateDemographics, String> category) {
        Map<String, List<MatchScorecard>> grouped = scorecards.stream()
                .collect(Collectors.groupingBy(scorecard -> {
                    CandidateDemographics demographics = byCandidateId.get(scorecard.getCandidate().getId());
                    return demographics == null ? CandidateDemographics.DECLINED : category.apply(demographics);
                }, LinkedHashMap::new, Collectors.toList()));

        Map<String, Double> shortlistRateByCategory = new LinkedHashMap<>();
        for (var entry : grouped.entrySet()) {
            shortlistRateByCategory.put(entry.getKey(), shortlistRate(entry.getValue()));
        }
        double highestRate = shortlistRateByCategory.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<MatchScorecard> group = entry.getValue();
                    double rate = shortlistRateByCategory.get(entry.getKey());
                    return new GroupStat(entry.getKey(), group.size(),
                            group.stream().filter(MatchScorecard::isShortlisted).count(), rate,
                            group.stream().mapToDouble(MatchScorecard::getCompositeScore).average().orElse(0.0),
                            highestRate == 0.0 ? null : rate / highestRate);
                })
                .sorted(Comparator.comparing(GroupStat::category))
                .toList();
    }

    private double shortlistRate(List<MatchScorecard> group) {
        long shortlisted = group.stream().filter(MatchScorecard::isShortlisted).count();
        return group.isEmpty() ? 0.0 : (double) shortlisted / group.size();
    }

    public record BiasAuditReport(List<GroupStat> byGender, List<GroupStat> byRaceEthnicity, Instant generatedAt) {
    }

    /**
     * {@code impactRatio} is this group's shortlist rate divided by the highest
     * shortlist rate among groups in the same dimension - the four-fifths/80% rule from
     * EEOC's Uniform Guidelines on Employee Selection Procedures, the standard reference
     * test LL144 bias-audit reporting is built around. A ratio below 0.8 flags adverse
     * impact for that group; {@code null} only when no group in this dimension
     * shortlisted anyone.
     */
    public record GroupStat(String category, long totalScored, long shortlisted, double shortlistRate,
            double avgCompositeScore, Double impactRatio) {
    }
}
