package ai.candidly.career.demographics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.MatchScorecard;
import ai.candidly.career.domain.MatchScorecardRepository;

class BiasAuditReportServiceTest {

    private final MatchScorecardRepository matchScorecardRepository = mock(MatchScorecardRepository.class);
    private final CandidateDemographicsRepository demographicsRepository = mock(CandidateDemographicsRepository.class);
    private final BiasAuditReportService service = new BiasAuditReportService(matchScorecardRepository, demographicsRepository);

    @Test
    void computesShortlistRateAndImpactRatioPerGroup() {
        Candidate manA = candidate();
        Candidate manB = candidate();
        Candidate womanA = candidate();

        JobPosting job = job();
        List<MatchScorecard> scorecards = List.of(
                scorecard(manA, job, true),
                scorecard(manB, job, false),
                scorecard(womanA, job, true));
        when(matchScorecardRepository.findAll()).thenReturn(scorecards);

        when(demographicsRepository.findAll()).thenReturn(List.of(
                demographics(manA.getId(), "Male"),
                demographics(manB.getId(), "Male"),
                demographics(womanA.getId(), "Female")));

        var report = service.generate();

        var male = report.byGender().stream().filter(g -> g.category().equals("Male")).findFirst().orElseThrow();
        var female = report.byGender().stream().filter(g -> g.category().equals("Female")).findFirst().orElseThrow();

        assertThat(male.totalScored()).isEqualTo(2);
        assertThat(male.shortlisted()).isEqualTo(1);
        assertThat(male.shortlistRate()).isEqualTo(0.5);
        assertThat(female.shortlistRate()).isEqualTo(1.0);
        // Female has the highest rate in this dimension, so its own impact ratio is 1.0;
        // Male's is 0.5 / 1.0 - below the four-fifths/0.8 threshold, correctly flagged.
        assertThat(female.impactRatio()).isEqualTo(1.0);
        assertThat(male.impactRatio()).isEqualTo(0.5);
    }

    @Test
    void candidatesWithNoSelfIdOnFileAreBucketedUnderDeclined() {
        Candidate undisclosed = candidate();
        JobPosting job = job();
        when(matchScorecardRepository.findAll()).thenReturn(List.of(scorecard(undisclosed, job, true)));
        when(demographicsRepository.findAll()).thenReturn(List.of());

        var report = service.generate();

        assertThat(report.byGender()).hasSize(1);
        assertThat(report.byGender().get(0).category()).isEqualTo(CandidateDemographics.DECLINED);
    }

    private Candidate candidate() {
        Candidate candidate = new Candidate("Test Candidate", UUID.randomUUID() + "@example.com", "Remote",
                Set.of("US"), 0, Set.of());
        setField(candidate, "id", UUID.randomUUID());
        return candidate;
    }

    private JobPosting job() {
        return new JobPosting("hash-" + UUID.randomUUID(), "Acme", "Engineer", "Remote", true, null, null,
                Set.of(), Set.of(), Set.of(), "tech", 0, "raw");
    }

    private MatchScorecard scorecard(Candidate candidate, JobPosting job, boolean shortlisted) {
        return new MatchScorecard(candidate, job, true, 0.8, 0.8, 0.8, 0.8, 0.8, shortlisted, List.of());
    }

    private CandidateDemographics demographics(UUID candidateId, String gender) {
        return new CandidateDemographics(candidateId, gender, null, null, null);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
