package ai.candidly.career.interviewprep;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Exactly one of {@code questionId} (a static-bank lookup) or {@code questionText} (a
 * generated/follow-up question, whose full text the client already holds - see {@link
 * InterviewQuestion}) must be present; the controller validates this. {@code
 * candidateId}/{@code careerLevel}/{@code interviewStage} are optional so existing
 * callers that only send {@code questionId}/{@code answer}/{@code jobPostingId} keep
 * working unchanged.
 */
public record AnswerRequest(String questionId, String questionText, @NotBlank String answer,
        @NotNull UUID jobPostingId, UUID candidateId, CareerLevel careerLevel, InterviewStage interviewStage) {
}
