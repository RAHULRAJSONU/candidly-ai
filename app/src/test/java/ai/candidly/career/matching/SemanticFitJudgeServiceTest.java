package ai.candidly.career.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateExperience;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.typesafe.TypeSafeAnswer;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeResponse;

@ExtendWith(MockitoExtension.class)
class SemanticFitJudgeServiceTest {

    @Mock
    private TypeSafeClient typeSafeClient;

    @Test
    void normalizesTopLevelScoreToOne() {
        Candidate candidate = new Candidate("Ada", "ada@example.com", "Remote", Set.of("US"), 0, Set.of());
        CandidateExperience experience = new CandidateExperience(candidate, "Acme", "Staff Engineer",
                LocalDate.of(2018, 1, 1), null, "Led a team building high-scale backend platforms.", Set.of(), Set.of());
        JobPosting job = new JobPosting("src-1", "Acme", "Staff Engineer", "Remote", true,
                null, null, Set.of("US"), Set.of(), Set.of(), "fintech", 5, "raw description");

        // Top level of a 5-level Score (indices 0-4) normalizes to 4/4 = 1.0.
        when(typeSafeClient.ask(any(), anyMap()))
                .thenReturn(new TypeSafeResponse("jev-1.13.0", Map.of("semantic_fit", score(4.0))));

        double result = new SemanticFitJudgeService(typeSafeClient).judge(List.of(experience), job);

        assertThat(result).isEqualTo(1.0);
    }

    @Test
    void noExperienceShortCircuitsToZeroWithoutCallingTypeSafe() {
        JobPosting job = new JobPosting("src-2", "Acme", "Staff Engineer", "Remote", true,
                null, null, Set.of("US"), Set.of(), Set.of(), "fintech", 5, "raw description");

        double result = new SemanticFitJudgeService(typeSafeClient).judge(List.of(), job);

        assertThat(result).isEqualTo(0.0);
    }

    private static TypeSafeAnswer score(double value) {
        return new TypeSafeAnswer("score", null, null, value, null, null, null);
    }
}
