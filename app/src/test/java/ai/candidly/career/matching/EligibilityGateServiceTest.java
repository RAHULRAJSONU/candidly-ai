package ai.candidly.career.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.JobPosting;

/**
 * Docs/03 §2.1 (C-4): a hard eligibility failure must reject outright, never just
 * lower a weighted term a strong skill score could offset. These tests pin that
 * invariant down at the gate level, independent of CompositeScoringService.
 */
class EligibilityGateServiceTest {

    private final EligibilityGateService gate = new EligibilityGateService(0.60);

    @Test
    void rejectsWhenNoWorkAuthorizationOverlap() {
        Candidate candidate = candidate(Set.of("EU"), 0, Set.of());
        JobPosting job = job(false, Set.of("US"), Set.of(), Set.of());

        var result = gate.evaluate(candidate, job);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).anyMatch(r -> r.contains("work authorization"));
    }

    @Test
    void remoteJobIgnoresLocationMismatch() {
        Candidate candidate = candidate(Set.of("US"), 0, Set.of());
        JobPosting job = job(true, Set.of("US"), Set.of(), Set.of());

        assertThat(gate.evaluate(candidate, job).passed()).isTrue();
    }

    @Test
    void rejectsBelowMandatorySkillThresholdEvenWithZeroCompGap() {
        Candidate candidate = candidate(Set.of("US"), 0, Set.of("skill.java"));
        JobPosting job = job(true, Set.of("US"), Set.of("skill.java", "skill.kubernetes", "skill.aws"), Set.of());

        var result = gate.evaluate(candidate, job);

        assertThat(result.passed()).isFalse();
        assertThat(result.mandatorySkillCoverage()).isCloseTo(1.0 / 3, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void passesWhenAllHardConstraintsSatisfied() {
        Candidate candidate = candidate(Set.of("US"), 0, Set.of("skill.java", "skill.aws"));
        JobPosting job = job(true, Set.of("US"), Set.of("skill.java"), Set.of());

        assertThat(gate.evaluate(candidate, job).passed()).isTrue();
    }

    @Test
    void rejectsWhenSameCurrencyJobMaxIsBelowFloor() {
        Candidate candidate = candidate(Set.of("US"), 15_000_000, Set.of());
        candidate.setPreferredCurrency("INR");
        JobPosting job = jobWithComp(10_000_000L, "INR");

        var result = gate.evaluate(candidate, job);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureReasons()).anyMatch(r -> r.contains("compensation"));
    }

    @Test
    void doesNotCompareCompAcrossCurrencies() {
        // ₹57,00,000 floor vs a $200k posting: the raw minor-unit numbers say "below floor",
        // but without an FX rate they aren't comparable, so this must not reject.
        Candidate candidate = candidate(Set.of("US"), 570_000_000, Set.of());
        candidate.setPreferredCurrency("INR");
        JobPosting job = jobWithComp(20_000_000L, null);

        assertThat(job.getCurrency()).isEqualTo("USD");
        assertThat(gate.evaluate(candidate, job).passed()).isTrue();
    }

    private Candidate candidate(Set<String> auths, long compFloor, Set<String> skills) {
        return new Candidate("Ada Lovelace", "ada@example.com", "Remote", auths, compFloor, skills, Set.of());
    }

    private JobPosting job(boolean remote, Set<String> acceptedAuths, Set<String> mandatorySkills, Set<String> preferredSkills) {
        return new JobPosting("hash-" + System.nanoTime(), "Acme", "Engineer", "Nowhere", remote,
                null, null, acceptedAuths, mandatorySkills, preferredSkills, "fintech", 0, "raw description");
    }

    private JobPosting jobWithComp(Long compMax, String currency) {
        JobPosting job = new JobPosting("hash-" + System.nanoTime(), "Acme", "Engineer", "Nowhere", true,
                null, compMax, Set.of(), Set.of(), Set.of(), "fintech", 0, "raw description");
        job.assignCurrency(currency);
        return job;
    }
}
