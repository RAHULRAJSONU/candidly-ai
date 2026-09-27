package ai.candidly.career.matching;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CurrencyCodes;
import ai.candidly.career.domain.JobPosting;

/**
 * The hard eligibility gate (docs/03 §2.1, C-4). Location, work authorization, comp
 * floor, and mandatory-skill coverage are boolean pre-filters evaluated BEFORE any
 * composite score is computed - never weighted terms a candidate can compensate for
 * with a strong skill/semantic score. A failure here routes straight to rejection with
 * a deterministic, human-readable reason (docs/02 §4.4).
 */
@Service
public class EligibilityGateService {

    private final double mandatorySkillMinimum;

    public EligibilityGateService(@Value("${candidly.thresholds.mandatory-skill-minimum}") double mandatorySkillMinimum) {
        this.mandatorySkillMinimum = mandatorySkillMinimum;
    }

    public EligibilityResult evaluate(Candidate candidate, JobPosting job) {
        List<String> failureReasons = new ArrayList<>();

        boolean authOk = job.getAcceptedWorkAuthorizations().isEmpty()
                || !Collections.disjoint(job.getAcceptedWorkAuthorizations(), candidate.getWorkAuthorizations());
        if (!authOk) {
            failureReasons.add("work authorization: candidate holds none of " + job.getAcceptedWorkAuthorizations());
        }

        boolean locationOk = job.isRemote() || job.getLocation().equalsIgnoreCase(candidate.getLocation());
        if (!locationOk) {
            failureReasons.add("location: job requires " + job.getLocation() + ", candidate is in " + candidate.getLocation());
        }

        // Minor units in two different currencies aren't comparable and there's no FX source
        // to convert them (see CurrencyCodes), so a cross-currency posting is treated like one
        // that states no comp at all: it can't fail this check. Mirrored in the frontend's
        // JobDiscovery computeEligibilityChecks - keep the two in sync.
        boolean compComparable = job.getCompMaxMinorUnits() != null
                && CurrencyCodes.sameCurrency(job.getCurrency(), candidate.getPreferredCurrency());
        boolean compOk = !compComparable || job.getCompMaxMinorUnits() >= candidate.getCompFloorMinorUnits();
        if (!compOk) {
            failureReasons.add("compensation: job max below candidate floor");
        }

        double mandatoryCoverage = mandatorySkillCoverage(candidate.getSkillIds(), job.getMandatorySkillIds());
        boolean mandatorySkillsOk = mandatoryCoverage >= mandatorySkillMinimum;
        if (!mandatorySkillsOk) {
            failureReasons.add("mandatory skills: candidate covers %.0f%% of required, need >= %.0f%%"
                    .formatted(mandatoryCoverage * 100, mandatorySkillMinimum * 100));
        }

        boolean passed = failureReasons.isEmpty();
        return new EligibilityResult(passed, mandatoryCoverage, failureReasons);
    }

    private double mandatorySkillCoverage(Set<String> candidateSkills, Set<String> mandatorySkills) {
        if (mandatorySkills.isEmpty()) {
            return 1.0;
        }
        long covered = mandatorySkills.stream().filter(candidateSkills::contains).count();
        return (double) covered / mandatorySkills.size();
    }

    public record EligibilityResult(boolean passed, double mandatorySkillCoverage, List<String> failureReasons) {
    }
}
