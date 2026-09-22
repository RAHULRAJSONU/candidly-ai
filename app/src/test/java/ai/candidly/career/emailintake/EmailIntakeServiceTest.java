package ai.candidly.career.emailintake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.MatchScorecard;
import ai.candidly.career.domain.MatchScorecardRepository;
import ai.candidly.career.pipeline.InterviewMode;
import ai.candidly.career.pipeline.InterviewRepository;
import ai.candidly.career.typesafe.TypeSafeAnswer;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeResponse;

class EmailIntakeServiceTest {

    private final MatchScorecardRepository matchScorecardRepository = mock(MatchScorecardRepository.class);
    private final InterviewRepository interviewRepository = mock(InterviewRepository.class);
    private final TypeSafeClient typeSafeClient = mock(TypeSafeClient.class);
    private final EmailIntakeService service = new EmailIntakeService(matchScorecardRepository, interviewRepository, typeSafeClient);

    @Test
    void interviewInvitationForAKnownApplicationCreatesAnInterview() {
        Candidate candidate = candidate();
        JobPosting job = job();
        MatchScorecard scorecard = new MatchScorecard(candidate, job, true, 0.8, 0.8, 0.8, 0.8, 0.8, true, List.of());
        when(matchScorecardRepository.findByCandidateId(candidate.getId())).thenReturn(List.of(scorecard));

        when(typeSafeClient.ask(any(), anyMap())).thenReturn(new TypeSafeResponse("jev-1.13.0", Map.of(
                "email_type", choice("INTERVIEW_INVITATION"),
                "mode", choice("VIRTUAL"),
                "related_application", choice(job.getId().toString()))));
        when(interviewRepository.save(any())).thenAnswer(inv -> {
            var interview = inv.getArgument(0, ai.candidly.career.pipeline.Interview.class);
            setId(interview);
            return interview;
        });

        var result = service.classify(candidate, "We'd like to invite you to interview on Sep 24, 2026 at 2:00 PM.");

        assertThat(result.emailType()).isEqualTo(EmailType.INTERVIEW_INVITATION);
        assertThat(result.mode()).isEqualTo(InterviewMode.VIRTUAL);
        assertThat(result.relatedJobPostingId()).isEqualTo(job.getId());
        assertThat(result.createdInterviewId()).isNotNull();
        verify(interviewRepository).save(any());
    }

    @Test
    void rejectionEmailNeverCreatesAnInterviewEvenWithAKnownApplication() {
        Candidate candidate = candidate();
        JobPosting job = job();
        MatchScorecard scorecard = new MatchScorecard(candidate, job, true, 0.8, 0.8, 0.8, 0.8, 0.8, true, List.of());
        when(matchScorecardRepository.findByCandidateId(candidate.getId())).thenReturn(List.of(scorecard));

        when(typeSafeClient.ask(any(), anyMap())).thenReturn(new TypeSafeResponse("jev-1.13.0", Map.of(
                "email_type", choice("REJECTION"),
                "mode", choice("UNKNOWN"),
                "related_application", choice(job.getId().toString()))));

        var result = service.classify(candidate, "Thank you for your interest, we've decided to move forward with other candidates.");

        assertThat(result.emailType()).isEqualTo(EmailType.REJECTION);
        assertThat(result.createdInterviewId()).isNull();
        verify(interviewRepository, never()).save(any());
    }

    @Test
    void noKnownApplicationsSkipsTheRelatedApplicationQuestionEntirely() {
        Candidate candidate = candidate();
        when(matchScorecardRepository.findByCandidateId(candidate.getId())).thenReturn(List.of());

        when(typeSafeClient.ask(any(), anyMap())).thenReturn(new TypeSafeResponse("jev-1.13.0", Map.of(
                "email_type", choice("OTHER"),
                "mode", choice("UNKNOWN"))));

        var result = service.classify(candidate, "Newsletter: 5 tips for your job search.");

        assertThat(result.emailType()).isEqualTo(EmailType.OTHER);
        assertThat(result.relatedJobPostingId()).isNull();
        verify(interviewRepository, never()).save(any());
    }

    private Candidate candidate() {
        Candidate candidate = new Candidate("Test Candidate", "test@example.com", "Remote", Set.of("US"), 0, Set.of());
        setId(candidate);
        return candidate;
    }

    private JobPosting job() {
        JobPosting job = new JobPosting("hash-" + UUID.randomUUID(), "Acme", "Backend Engineer", "Remote", true, null,
                null, Set.of(), Set.of(), Set.of(), "fintech", 3, "raw");
        setId(job);
        return job;
    }

    private void setId(Object entity) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static TypeSafeAnswer choice(String value) {
        return new TypeSafeAnswer("choice", null, value, null, null, null, null);
    }
}
