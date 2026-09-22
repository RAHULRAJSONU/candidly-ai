package ai.candidly.career.interviewprep;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ai.candidly.career.ai.GroqChatClient;
import ai.candidly.career.domain.JobPosting;

/**
 * Generates a single natural interviewer follow-up when {@link
 * MockInterviewAnswerJudgeService}'s {@code needs_followup} Noul judges the candidate's
 * answer likely leaves a gap worth probing - the same "TypeSafe judges, Groq generates"
 * split as {@link PersonalizedQuestionGeneratorService}. This is what makes the practice
 * session feel like a real back-and-forth interview rather than a one-shot Q&A form.
 *
 * <p>Failure here must never break the main answer-feedback response - a missing
 * follow-up degrades gracefully to "no follow-up", so every failure path returns {@code
 * null} rather than propagating.
 */
@Service
public class FollowUpQuestionGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(FollowUpQuestionGeneratorService.class);

    private static final String SYSTEM_PROMPT = "You are an experienced interviewer. Given the question you just "
            + "asked and the candidate's answer, write exactly one natural follow-up question probing the gap in "
            + "their answer. Output only the follow-up question itself - no preamble, no labels, no quotation marks.";

    private final GroqChatClient groqChatClient;

    public FollowUpQuestionGeneratorService(GroqChatClient groqChatClient) {
        this.groqChatClient = groqChatClient;
    }

    public String generate(String question, String answer, JobPosting job, CareerLevel careerLevel) {
        try {
            String userPrompt = "Original question: " + question + "\n"
                    + "Candidate's answer: " + answer + "\n"
                    + "Target role: " + job.getTitle() + " (domain: " + job.getDomain() + ")\n"
                    + "Candidate's career level: " + careerLevel;
            String completion = groqChatClient.complete(SYSTEM_PROMPT, userPrompt);
            if (completion == null) {
                return null;
            }
            String cleaned = completion.strip();
            return cleaned.isBlank() ? null : cleaned;
        } catch (RuntimeException e) {
            log.warn("Follow-up question generation failed - continuing without a follow-up", e);
            return null;
        }
    }
}
