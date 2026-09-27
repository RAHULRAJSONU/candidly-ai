package ai.candidly.career.matching;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateExperience;
import ai.candidly.career.domain.JobPosting;

/**
 * Deterministic composite scoring (docs/03 §2.1, corrected weights). Location and work
 * authorization are NOT inputs here - they are the EligibilityGateService's job and
 * must have already passed before this is called.
 *
 * <pre>
 * S_final = 0.47 * S_skill + 0.29 * S_exp + 0.12 * S_sem + 0.12 * S_dom
 * </pre>
 *
 * <p>S_skill and S_exp are pure set/arithmetic - no model involved. S_sem and S_dom are
 * both TypeSafe Score judgments over the candidate's actual employer/title/narrative
 * history (see SemanticFitJudgeService, DomainFitJudgeService) rather than embedding
 * cosine similarity - "does the shape of this career read like this job" and "has this
 * candidate really worked in this industry" are judgment calls about a career history,
 * not string-similarity problems. S_sem is scoped to scope/seniority/nature-of-work,
 * explicitly excluding the skill-keyword overlap S_skill already covers and the
 * industry/domain depth S_dom already covers, so the three don't just re-measure each
 * other under different names. That split is this implementation's own choice - the
 * source docs specify the weights but not this sub-score construction, so treat it as a
 * starting point to calibrate against real outcomes (docs/03 §3).
 */
@Service
public class CompositeScoringService {

    private static final double REQUIRED_SKILL_WEIGHT = 0.70;
    private static final double PREFERRED_SKILL_WEIGHT = 0.30;

    private final double weightSkill;
    private final double weightExperience;
    private final double weightSemantic;
    private final double weightDomain;

    private final SemanticFitJudgeService semanticFitJudgeService;
    private final DomainFitJudgeService domainFitJudgeService;

    public CompositeScoringService(SemanticFitJudgeService semanticFitJudgeService,
            DomainFitJudgeService domainFitJudgeService,
            @Value("${candidly.matching.weights.skill:0.47}") double weightSkill,
            @Value("${candidly.matching.weights.experience:0.29}") double weightExperience,
            @Value("${candidly.matching.weights.semantic:0.12}") double weightSemantic,
            @Value("${candidly.matching.weights.domain:0.12}") double weightDomain) {
        this.semanticFitJudgeService = semanticFitJudgeService;
        this.domainFitJudgeService = domainFitJudgeService;
        this.weightSkill = weightSkill;
        this.weightExperience = weightExperience;
        this.weightSemantic = weightSemantic;
        this.weightDomain = weightDomain;
    }

    public ScoreBreakdown score(Candidate candidate, List<CandidateExperience> experiences, JobPosting job) {
        double skillScore = skillScore(candidate.getSkillIds(), job.getMandatorySkillIds(), job.getPreferredSkillIds());
        double experienceScore = experienceScore(experiences, job);
        double semanticScore = semanticFitJudgeService.judge(experiences, job);
        double domainScore = experiences.isEmpty() ? 0.0 : domainFitJudgeService.judge(experiences, job);

        // jaccard() returns 1.0 (a "perfect match") whenever a posting states no mandatory/
        // preferred skills at all - correct for the eligibility gate (a candidate can't be
        // rejected for skills nobody asked for) but wrong here: a posting with no extracted
        // skill requirement isn't a perfect skill match, it's simply not comparable on this
        // dimension. Live-caught during an end-to-end validation pass, where this vacuous 1.0
        // let non-technical postings with zero extracted skills (e.g. "Associate Renewals
        // Manager") outrank real engineering matches for a technical candidate. Redistribute
        // weightSkill across the other three dimensions for this one call instead of letting
        // it inflate the composite - the same "not comparable, don't fail or inflate" treatment
        // EligibilityGateService already applies to cross-currency comp comparisons.
        boolean skillDataAvailable = !job.getMandatorySkillIds().isEmpty() || !job.getPreferredSkillIds().isEmpty();
        double composite;
        if (skillDataAvailable) {
            composite = weightSkill * skillScore + weightExperience * experienceScore
                    + weightSemantic * semanticScore + weightDomain * domainScore;
        } else {
            double remaining = weightExperience + weightSemantic + weightDomain;
            double factor = remaining == 0 ? 0 : weightSkill / remaining;
            composite = (weightExperience + weightExperience * factor) * experienceScore
                    + (weightSemantic + weightSemantic * factor) * semanticScore
                    + (weightDomain + weightDomain * factor) * domainScore;
        }

        return new ScoreBreakdown(skillScore, experienceScore, semanticScore, domainScore, composite);
    }

    private double skillScore(Set<String> candidateSkills, Set<String> mandatorySkills, Set<String> preferredSkills) {
        double requiredJaccard = jaccard(candidateSkills, mandatorySkills);
        double preferredJaccard = jaccard(candidateSkills, preferredSkills);
        return REQUIRED_SKILL_WEIGHT * requiredJaccard + PREFERRED_SKILL_WEIGHT * preferredJaccard;
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        if (b.isEmpty()) {
            return 1.0;
        }
        long intersection = b.stream().filter(a::contains).count();
        long union = a.size() + b.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private double experienceScore(List<CandidateExperience> experiences, JobPosting job) {
        if (job.getMinYearsExperience() <= 0) {
            return 1.0;
        }
        double relevantYears = experiences.stream()
                .filter(exp -> !java.util.Collections.disjoint(exp.getSkillIds(), job.getMandatorySkillIds())
                        || !java.util.Collections.disjoint(exp.getSkillIds(), job.getPreferredSkillIds()))
                .mapToDouble(this::years)
                .sum();
        return Math.min(1.0, relevantYears / job.getMinYearsExperience());
    }

    private double years(CandidateExperience experience) {
        LocalDate end = experience.getEndDate() != null ? experience.getEndDate() : LocalDate.now();
        return ChronoUnit.DAYS.between(experience.getStartDate(), end) / 365.25;
    }

    public record ScoreBreakdown(double skillScore, double experienceScore, double semanticScore,
            double domainScore, double compositeScore) {
    }
}
