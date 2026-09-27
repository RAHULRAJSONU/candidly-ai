package ai.candidly.career.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateExperience;
import ai.candidly.career.domain.JobPosting;

/**
 * Live-caught during an end-to-end validation pass: a job posting with zero extracted
 * mandatory/preferred skills (a legitimate outcome for non-technical roles, not an
 * extraction failure) previously scored a vacuous, perfect skillScore=1.0, letting
 * postings like "Associate Renewals Manager" outrank real engineering matches for a
 * technical candidate. These tests pin down the fix: the skill dimension's weight is
 * redistributed across experience/semantic/domain when a job states no skill
 * requirement, rather than counted as a perfect match.
 */
@ExtendWith(MockitoExtension.class)
class CompositeScoringServiceTest {

    @Mock
    private SemanticFitJudgeService semanticFitJudgeService;
    @Mock
    private DomainFitJudgeService domainFitJudgeService;

    // Constructed manually per test (not @InjectMocks) so the four docs/03 §2.1 weights
    // are pinned to their documented values regardless of application.yml.
    private CompositeScoringService scoring() {
        return new CompositeScoringService(semanticFitJudgeService, domainFitJudgeService, 0.47, 0.29, 0.12, 0.12);
    }

    @Test
    void jobWithNoSkillsAtAllRedistributesSkillWeightInsteadOfScoringItPerfect() {
        Candidate candidate = candidate(Set.of("skill.java"));
        List<CandidateExperience> experiences = List.of();
        JobPosting job = job(Set.of(), Set.of(), 0);

        when(semanticFitJudgeService.judge(experiences, job)).thenReturn(0.5);
        // domainFitJudgeService.judge is never called when experiences is empty (short-circuited to 0.0).

        var breakdown = scoring().score(candidate, experiences, job);

        assertThat(breakdown.skillScore()).isEqualTo(1.0); // raw jaccard value, unchanged - still reported
        // experienceScore=1.0 (job states no minYearsExperience), domainScore=0.0 (no experience rows).
        // With skill's 0.47 redistributed proportionally across experience(0.29)/semantic(0.12)/domain(0.12):
        // wExperience=0.29*(1+0.47/0.53), wSemantic=0.12*(1+0.47/0.53), wDomain=0.12*(1+0.47/0.53)
        double factor = 0.47 / (0.29 + 0.12 + 0.12);
        double expected = (0.29 + 0.29 * factor) * 1.0 + (0.12 + 0.12 * factor) * 0.5 + (0.12 + 0.12 * factor) * 0.0;
        assertThat(breakdown.compositeScore()).isCloseTo(expected, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void jobWithMandatorySkillsUsesFixedWeightsUnchanged() {
        Candidate candidate = candidate(Set.of("skill.java"));
        List<CandidateExperience> experiences = List.of();
        JobPosting job = job(Set.of("skill.java"), Set.of(), 0);

        when(semanticFitJudgeService.judge(experiences, job)).thenReturn(0.5);

        var breakdown = scoring().score(candidate, experiences, job);

        // skillScore = 0.70*jaccard(required)+0.30*jaccard(preferred); required jaccard=1.0 (full overlap),
        // preferred empty -> jaccard=1.0 too (vacuous on the preferred side only, unaffected by this fix).
        assertThat(breakdown.skillScore()).isEqualTo(1.0);
        double expected = 0.47 * 1.0 + 0.29 * 1.0 + 0.12 * 0.5 + 0.12 * 0.0;
        assertThat(breakdown.compositeScore()).isCloseTo(expected, org.assertj.core.data.Offset.offset(1e-9));
    }

    private Candidate candidate(Set<String> skills) {
        return new Candidate("Ada Lovelace", "ada@example.com", "Remote", Set.of("US"), 0, skills, Set.of());
    }

    private JobPosting job(Set<String> mandatorySkills, Set<String> preferredSkills, int minYearsExperience) {
        return new JobPosting("hash-" + System.nanoTime(), "Acme", "Engineer", "Remote", true,
                null, null, Set.of("US"), mandatorySkills, preferredSkills, "fintech", minYearsExperience, "raw description");
    }
}
