package ai.candidly.career.aiops;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.JobPostingRepository;
import ai.candidly.career.domain.JobPostingSource;
import ai.candidly.career.domain.MatchScorecard;
import ai.candidly.career.domain.MatchScorecardRepository;
import ai.candidly.career.domain.TailoredArtifact;
import ai.candidly.career.domain.TailoredArtifactRepository;
import ai.candidly.career.domain.TailoredArtifactStatus;
import ai.candidly.career.pipeline.Interview;
import ai.candidly.career.pipeline.InterviewRepository;
import ai.candidly.career.pipeline.Offer;
import ai.candidly.career.pipeline.OfferRepository;
import ai.candidly.career.taxonomy.SkillTaxonomy;
import ai.candidly.career.taxonomy.SkillTaxonomyEntry;

/**
 * Computes {@link InsightsSummary} - plain in-memory aggregation over rows already
 * persisted (job_posting, match_scorecard, tailored_artifact, interview, job_offer),
 * the same "read-only, no TypeSafe call, demo-scale in-memory grouping" approach
 * {@code AiOpsService}/{@code BiasAuditReportService} already use. Backs the "Autopilot
 * Insights" panel (plan Phase 3): which discovery sources, domains, and mandatory
 * skills actually correlate with a tailored artifact getting approved and a candidate
 * landing an interview - a human-readable signal a person can act on (e.g. tracking
 * more companies from a high-performing source, or re-weighting
 * {@code candidly.matching.weights.skill}), never a value this service or anything
 * downstream feeds back into scoring automatically.
 */
@Service
public class InsightsService {

    private final JobPostingRepository jobPostingRepository;
    private final MatchScorecardRepository matchScorecardRepository;
    private final TailoredArtifactRepository artifactRepository;
    private final InterviewRepository interviewRepository;
    private final OfferRepository offerRepository;
    private final SkillTaxonomy skillTaxonomy;

    public InsightsService(JobPostingRepository jobPostingRepository, MatchScorecardRepository matchScorecardRepository,
            TailoredArtifactRepository artifactRepository, InterviewRepository interviewRepository,
            OfferRepository offerRepository, SkillTaxonomy skillTaxonomy) {
        this.jobPostingRepository = jobPostingRepository;
        this.matchScorecardRepository = matchScorecardRepository;
        this.artifactRepository = artifactRepository;
        this.interviewRepository = interviewRepository;
        this.offerRepository = offerRepository;
        this.skillTaxonomy = skillTaxonomy;
    }

    public InsightsSummary summarize() {
        List<JobPosting> postings = jobPostingRepository.findAll();
        List<MatchScorecard> scorecards = matchScorecardRepository.findAll();
        List<TailoredArtifact> artifacts = artifactRepository.findAll();
        List<Interview> interviews = interviewRepository.findAll();
        List<Offer> offers = offerRepository.findAll();

        return new InsightsSummary(bySource(postings, scorecards, artifacts, interviews, offers),
                byDomain(postings, scorecards, artifacts, interviews),
                byMandatorySkill(postings, scorecards, artifacts));
    }

    private List<InsightsSummary.SourceInsight> bySource(List<JobPosting> postings, List<MatchScorecard> scorecards,
            List<TailoredArtifact> artifacts, List<Interview> interviews, List<Offer> offers) {
        Map<JobPostingSource, Long> discovered = countBy(postings, this::sourceKey);
        Map<JobPostingSource, Long> shortlisted = countBy(
                scorecards.stream().filter(MatchScorecard::isShortlisted).toList(),
                s -> sourceKey(s.getJobPosting()));
        Map<JobPostingSource, Long> tailored = countBy(artifacts, a -> sourceKey(a.getJobPosting()));
        Map<JobPostingSource, Long> approved = countBy(
                artifacts.stream().filter(a -> a.getStatus() == TailoredArtifactStatus.APPROVED).toList(),
                a -> sourceKey(a.getJobPosting()));
        Map<JobPostingSource, Long> rejected = countBy(
                artifacts.stream().filter(a -> a.getStatus() == TailoredArtifactStatus.REJECTED).toList(),
                a -> sourceKey(a.getJobPosting()));
        Map<JobPostingSource, Long> interviewCounts = countBy(interviews, i -> sourceKey(i.getJobPosting()));
        Map<JobPostingSource, Long> offerCounts = countBy(offers, o -> sourceKey(o.getJobPosting()));

        List<InsightsSummary.SourceInsight> result = new ArrayList<>();
        for (JobPostingSource source : JobPostingSource.values()) {
            long approvedCount = approved.getOrDefault(source, 0L);
            long rejectedCount = rejected.getOrDefault(source, 0L);
            long shortlistedCount = shortlisted.getOrDefault(source, 0L);
            result.add(new InsightsSummary.SourceInsight(source.name(), discovered.getOrDefault(source, 0L),
                    shortlistedCount, tailored.getOrDefault(source, 0L), approvedCount, rejectedCount,
                    interviewCounts.getOrDefault(source, 0L), offerCounts.getOrDefault(source, 0L),
                    rate(approvedCount, approvedCount + rejectedCount),
                    rate(interviewCounts.getOrDefault(source, 0L), shortlistedCount)));
        }
        return result.stream().sorted(Comparator.comparingLong(InsightsSummary.SourceInsight::discovered).reversed()).toList();
    }

    private List<InsightsSummary.DomainInsight> byDomain(List<JobPosting> postings, List<MatchScorecard> scorecards,
            List<TailoredArtifact> artifacts, List<Interview> interviews) {
        Map<String, Long> discovered = countBy(postings, this::domainKey);
        Map<String, Long> shortlisted = countBy(scorecards.stream().filter(MatchScorecard::isShortlisted).toList(),
                s -> domainKey(s.getJobPosting()));
        Map<String, Long> approved = countBy(
                artifacts.stream().filter(a -> a.getStatus() == TailoredArtifactStatus.APPROVED).toList(),
                a -> domainKey(a.getJobPosting()));
        Map<String, Long> rejected = countBy(
                artifacts.stream().filter(a -> a.getStatus() == TailoredArtifactStatus.REJECTED).toList(),
                a -> domainKey(a.getJobPosting()));
        Map<String, Long> interviewCounts = countBy(interviews, i -> domainKey(i.getJobPosting()));

        return discovered.entrySet().stream()
                .map(entry -> {
                    String domain = entry.getKey();
                    long discoveredCount = entry.getValue();
                    long shortlistedCount = shortlisted.getOrDefault(domain, 0L);
                    long approvedCount = approved.getOrDefault(domain, 0L);
                    long rejectedCount = rejected.getOrDefault(domain, 0L);
                    return new InsightsSummary.DomainInsight(domain, discoveredCount, shortlistedCount, approvedCount,
                            rejectedCount, interviewCounts.getOrDefault(domain, 0L),
                            rate(shortlistedCount, discoveredCount), rate(approvedCount, approvedCount + rejectedCount));
                })
                .sorted(Comparator.comparingLong(InsightsSummary.DomainInsight::discovered).reversed())
                .limit(8)
                .toList();
    }

    private List<InsightsSummary.SkillInsight> byMandatorySkill(List<JobPosting> postings, List<MatchScorecard> scorecards,
            List<TailoredArtifact> artifacts) {
        List<InsightsSummary.SkillInsight> result = new ArrayList<>();
        for (SkillTaxonomyEntry skill : skillTaxonomy.entries()) {
            long discoveredCount = postings.stream().filter(p -> requiresSkill(p, skill.id())).count();
            if (discoveredCount == 0) {
                continue;
            }
            long shortlistedCount = scorecards.stream()
                    .filter(MatchScorecard::isShortlisted)
                    .filter(s -> requiresSkill(s.getJobPosting(), skill.id()))
                    .count();
            long approvedCount = artifacts.stream()
                    .filter(a -> a.getStatus() == TailoredArtifactStatus.APPROVED)
                    .filter(a -> requiresSkill(a.getJobPosting(), skill.id()))
                    .count();
            long rejectedCount = artifacts.stream()
                    .filter(a -> a.getStatus() == TailoredArtifactStatus.REJECTED)
                    .filter(a -> requiresSkill(a.getJobPosting(), skill.id()))
                    .count();
            result.add(new InsightsSummary.SkillInsight(skill.id(), skill.canonicalName(), discoveredCount,
                    shortlistedCount, approvedCount, rejectedCount, rate(shortlistedCount, discoveredCount),
                    rate(approvedCount, approvedCount + rejectedCount)));
        }
        return result.stream()
                .sorted(Comparator.comparingLong(InsightsSummary.SkillInsight::postingsRequiring).reversed())
                .limit(10)
                .toList();
    }

    private boolean requiresSkill(JobPosting posting, String skillId) {
        Set<String> mandatory = posting.getMandatorySkillIds();
        return mandatory != null && mandatory.contains(skillId);
    }

    private String domainKey(JobPosting posting) {
        return posting.getDomain() == null || posting.getDomain().isBlank() ? "Unspecified" : posting.getDomain();
    }

    /** {@code JobPosting.source} is nullable for a row persisted before that column
     * existed (same {@code ddl-auto=update} backfill gap as {@code discoveredAt}, see
     * CLAUDE.md) - {@code groupingBy} throws on a null key, so this falls back to MANUAL,
     * matching the field's own declared default for a freshly-constructed entity. */
    private JobPostingSource sourceKey(JobPosting posting) {
        JobPostingSource source = posting.getSource();
        return source == null ? JobPostingSource.MANUAL : source;
    }

    private <T, K> Map<K, Long> countBy(List<T> items, Function<T, K> keyFn) {
        return items.stream().collect(Collectors.groupingBy(keyFn, LinkedHashMap::new, Collectors.counting()));
    }

    /** {@code null} when {@code denominator} is 0 - "no data yet" is not the same as "0%". */
    private Double rate(long numerator, long denominator) {
        return denominator == 0 ? null : (double) numerator / denominator;
    }
}
