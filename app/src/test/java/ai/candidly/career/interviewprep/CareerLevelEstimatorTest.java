package ai.candidly.career.interviewprep;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateExperience;

class CareerLevelEstimatorTest {

    private final CareerLevelEstimator estimator = new CareerLevelEstimator();

    @Test
    void noExperienceIsEntry() {
        assertThat(estimator.estimate(List.of())).isEqualTo(CareerLevel.ENTRY);
    }

    @Test
    void oneYearIsEntry() {
        var experience = experience("Software Engineer", LocalDate.now().minusYears(1), LocalDate.now());
        assertThat(estimator.estimate(List.of(experience))).isEqualTo(CareerLevel.ENTRY);
    }

    @Test
    void sevenYearsIsSenior() {
        var experience = experience("Software Engineer", LocalDate.now().minusYears(7), null);
        assertThat(estimator.estimate(List.of(experience))).isEqualTo(CareerLevel.SENIOR);
    }

    @Test
    void seniorTitleBumpsLevelUpANotch() {
        var experience = experience("Staff Software Engineer", LocalDate.now().minusYears(3), null);
        assertThat(estimator.estimate(List.of(experience))).isEqualTo(CareerLevel.SENIOR);
    }

    @Test
    void twelveYearsIsStaffPlusAndDoesNotBumpPastIt() {
        var experience = experience("Principal Engineer", LocalDate.now().minusYears(12), null);
        assertThat(estimator.estimate(List.of(experience))).isEqualTo(CareerLevel.STAFF_PLUS);
    }

    private CandidateExperience experience(String title, LocalDate start, LocalDate end) {
        Candidate candidate = new Candidate("Jane Doe", "jane@example.com", "Remote", Set.of("US"), 0, Set.of());
        return new CandidateExperience(candidate, "Acme", title, start, end, "narrative", Set.of(), Set.of());
    }
}
