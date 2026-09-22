package ai.candidly.career.interviewprep;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ai.candidly.career.ai.GroqChatClient;
import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateExperience;
import ai.candidly.career.domain.JobPosting;

/**
 * Generates interview questions tailored to a candidate/job/level/stage pairing, via
 * Groq ({@link GroqChatClient}) rather than TypeSafe - per the typesafe-ai skill,
 * TypeSafe's Jev model is "not a generator" (forcing free-text generation performs
 * poorly there), so free-text generation in this repo always goes through Groq (see
 * {@code tailoring.GroundedResumeGenerator}), with TypeSafe reserved for typed
 * judgments over already-generated text. There is no grounding-verification step here
 * like tailoring has, because interview questions aren't factual claims about the
 * candidate that need to trace back to verified evidence - they're prompts, and a
 * slightly-off one just gets skipped by the candidate.
 *
 * <p>State fed to Groq is deliberately narrow, mirroring {@link
 * MockInterviewAnswerJudgeService}'s discipline: the job's already-screened structured
 * fields only (title/domain/mandatory skills/min years) - never {@code
 * JobPosting.rawDescription} - plus the candidate's own skills/experience (their own
 * data, not third-party text).
 */
@Service
public class PersonalizedQuestionGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PersonalizedQuestionGeneratorService.class);
    private static final int MAX_QUESTIONS = 5;
    private static final int MIN_QUESTION_LENGTH = 15;

    private final GroqChatClient groqChatClient;
    private final InterviewQuestionBank fallbackBank;

    public PersonalizedQuestionGeneratorService(GroqChatClient groqChatClient, InterviewQuestionBank fallbackBank) {
        this.groqChatClient = groqChatClient;
        this.fallbackBank = fallbackBank;
    }

    public List<InterviewQuestion> generate(Candidate candidate, List<CandidateExperience> experience,
            JobPosting job, CareerLevel careerLevel, InterviewStage stage, InterviewCategory category) {
        try {
            String completion = groqChatClient.complete(systemPrompt(category), userPrompt(candidate, experience, job,
                    careerLevel, stage, category));
            List<InterviewQuestion> parsed = parse(completion, category);
            if (!parsed.isEmpty()) {
                return parsed;
            }
        } catch (RuntimeException e) {
            log.warn("Personalized question generation failed, falling back to the static bank", e);
        }
        return fallbackBank.byCategory(category);
    }

    private String systemPrompt(InterviewCategory category) {
        return "You are an experienced technical interviewer preparing " + MAX_QUESTIONS + " interview "
                + "questions for the " + category + " category. Output exactly one question per line, no "
                + "numbering, no bullets, no preamble, no commentary - just the questions, one per line.";
    }

    private String userPrompt(Candidate candidate, List<CandidateExperience> experience, JobPosting job,
            CareerLevel careerLevel, InterviewStage stage, InterviewCategory category) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Target role: ").append(job.getTitle()).append(" (domain: ").append(job.getDomain())
                .append(", minimum years experience: ").append(job.getMinYearsExperience()).append(")\n");
        prompt.append("Mandatory skills for the role: ").append(String.join(", ", job.getMandatorySkillIds())).append("\n");
        prompt.append("Candidate's career level: ").append(careerLevel).append("\n");
        prompt.append("Interview stage: ").append(stage).append("\n");
        prompt.append("Candidate's skills: ").append(String.join(", ", candidate.getSkillIds())).append("\n");
        prompt.append("Candidate's experience:\n");
        for (CandidateExperience item : experience) {
            prompt.append("- ").append(item.getTitle()).append(" at ").append(item.getEmployer());
            if (item.getNarrative() != null && !item.getNarrative().isBlank()) {
                prompt.append(": ").append(item.getNarrative());
            }
            prompt.append("\n");
        }
        prompt.append("\nWrite ").append(MAX_QUESTIONS).append(' ').append(category)
                .append(" interview questions calibrated to this candidate's actual background and this specific "
                        + "role, appropriate for a ").append(stage).append(" stage interview at a ")
                .append(careerLevel).append(" career level.");
        return prompt.toString();
    }

    private List<InterviewQuestion> parse(String completion, InterviewCategory category) {
        if (completion == null) {
            return List.of();
        }
        List<InterviewQuestion> questions = new ArrayList<>();
        for (String line : completion.split("\\R")) {
            String cleaned = line.strip().replaceFirst("^[-*\\d.)\\s]+", "").strip();
            if (cleaned.length() < MIN_QUESTION_LENGTH) {
                continue;
            }
            questions.add(new InterviewQuestion("gen-" + UUID.randomUUID(), category, cleaned, true));
            if (questions.size() >= MAX_QUESTIONS) {
                break;
            }
        }
        return questions;
    }
}
