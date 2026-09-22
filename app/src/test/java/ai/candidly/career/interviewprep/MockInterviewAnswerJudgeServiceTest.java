package ai.candidly.career.interviewprep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.typesafe.TypeSafeAnswer;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeResponse;

@ExtendWith(MockitoExtension.class)
class MockInterviewAnswerJudgeServiceTest {

    @Mock
    private TypeSafeClient typeSafeClient;

    @Test
    void normalizesEachDimensionIndependently() {
        JobPosting job = new JobPosting("hash-1", "Acme", "Backend Engineer", "Remote", true, null, null, Set.of(),
                Set.of("skill.java"), Set.of(), "fintech", 5, "raw");

        when(typeSafeClient.ask(any(), anyMap())).thenReturn(new TypeSafeResponse("jev-1.13.0", Map.of(
                "clarity", score(4.0),
                "depth", score(2.0),
                "relevance", score(0.0),
                "needs_followup", noul(0.8))));

        var feedback = new MockInterviewAnswerJudgeService(typeSafeClient).judge("Explain a rate limiter.",
                "A token bucket algorithm ...", job, CareerLevel.MID, InterviewStage.TECHNICAL_DEEP_DIVE);

        assertThat(feedback.clarity()).isEqualTo(1.0);
        assertThat(feedback.depth()).isEqualTo(0.5);
        assertThat(feedback.relevance()).isEqualTo(0.0);
        assertThat(feedback.suggestion()).contains("what was actually asked");
        assertThat(feedback.needsFollowUpProbability()).isEqualTo(0.8);
        assertThat(feedback.followUpQuestion()).isNull();
    }

    @Test
    void blankAnswerShortCircuitsWithoutCallingTypeSafe() {
        JobPosting job = new JobPosting("hash-2", "Acme", "Backend Engineer", "Remote", true, null, null, Set.of(),
                Set.of(), Set.of(), "fintech", 5, "raw");

        var feedback = new MockInterviewAnswerJudgeService(typeSafeClient).judge("Explain a rate limiter.", "  ", job,
                CareerLevel.MID, InterviewStage.TECHNICAL_DEEP_DIVE);

        assertThat(feedback.clarity()).isEqualTo(0.0);
        assertThat(feedback.depth()).isEqualTo(0.0);
        assertThat(feedback.relevance()).isEqualTo(0.0);
        assertThat(feedback.needsFollowUpProbability()).isEqualTo(0.0);
        verifyNoInteractions(typeSafeClient);
    }

    private static TypeSafeAnswer score(double value) {
        return new TypeSafeAnswer("score", null, null, value, null, null, null);
    }

    private static TypeSafeAnswer noul(double value) {
        return new TypeSafeAnswer("noul", value, null, null, null, null, null);
    }
}
