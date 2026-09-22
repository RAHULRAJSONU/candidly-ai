package ai.candidly.career.interviewprep;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeQuestion;
import ai.candidly.career.typesafe.TypeSafeResponse;

/**
 * Scores a candidate's free-text mock-interview answer on three independent dimensions,
 * plus a fourth "does this need a follow-up" gate, all batched in one TypeSafe call
 * (typesafe-ai skill: "ask independent questions over the same state together"). Three
 * separate Score questions rather than one composite, because clarity/depth/relevance
 * are genuinely different failure modes a candidate needs different feedback for - a
 * single blended number would hide which one to work on (docs/typesafe "split
 * independently useful dimensions").
 *
 * <p>State is deliberately narrow: the question, the candidate's own answer, the target
 * role's structured fields (title/domain/mandatory skills), and the candidate's chosen
 * career level/interview stage (used only to calibrate how much is "enough" for the
 * Score rubrics) - never {@code job.rawDescription} (attacker-controlled, unscreened at
 * this call site - same discipline as {@code SemanticFitJudgeService}). The candidate's
 * answer is their own input, not third-party text, but it still gets no tool access
 * here either: TypeSafe's Score/Choice/Noul primitives never carry tool-calling
 * capability in this repo (see {@code TypeSafeClient}), so that invariant holds
 * structurally, not by convention.
 *
 * <p>{@code needs_followup} is a Noul, not a Score, because it's a genuine yes/no
 * question ("would an experienced interviewer probe further?") rather than a spectrum -
 * per the typesafe-ai skill, a Noul near 0.5 means "equally likely either way", not
 * "medium". The controller only generates a follow-up above a fixed probability
 * threshold (see {@code InterviewPrepController.FOLLOW_UP_THRESHOLD}).
 */
@Service
public class MockInterviewAnswerJudgeService {

    private static final List<String> LEVELS = List.of(
            "Very weak - rambling, hard to follow, or largely missing the point.",
            "Weak - some structure but meanders or drifts from the question.",
            "Adequate - understandable and on-topic, but generic or shallow.",
            "Strong - clear, well-organized, and substantive for this level of question.",
            "Excellent - concise, well-structured, and demonstrates real command of the topic.");

    private final TypeSafeClient typeSafeClient;

    public MockInterviewAnswerJudgeService(TypeSafeClient typeSafeClient) {
        this.typeSafeClient = typeSafeClient;
    }

    public AnswerFeedback judge(String question, String answer, JobPosting job, CareerLevel careerLevel,
            InterviewStage stage) {
        if (answer == null || answer.isBlank()) {
            return new AnswerFeedback(0, 0, 0, "No answer submitted - there's nothing to evaluate yet.", 0, null);
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("question", question);
        state.put("candidate_answer", answer);
        state.put("target_title", job.getTitle());
        state.put("target_domain", job.getDomain());
        state.put("target_mandatory_skills", job.getMandatorySkillIds());
        state.put("candidate_career_level", careerLevel);
        state.put("interview_stage", stage);

        TypeSafeResponse response = typeSafeClient.ask(state, Map.of(
                "clarity", TypeSafeQuestion.score(
                        "How clear and well-structured is `candidate_answer` as a response to `question` - "
                                + "easy to follow, logically organized, free of rambling?",
                        LEVELS),
                "depth", TypeSafeQuestion.score(
                        "How much genuine technical/domain depth does `candidate_answer` demonstrate for "
                                + "someone at `candidate_career_level` targeting the `target_title` role (domain: "
                                + "`target_domain`, skills: `target_mandatory_skills`) in a `interview_stage` "
                                + "interview - specifics and reasoning appropriate to that level, not just buzzwords?",
                        LEVELS),
                "relevance", TypeSafeQuestion.score(
                        "How directly does `candidate_answer` address what `question` actually asked, without "
                                + "drifting into unrelated material?",
                        LEVELS),
                "needs_followup", TypeSafeQuestion.noul(
                        "Given `candidate_career_level` and `interview_stage`, would an experienced interviewer "
                                + "feel `candidate_answer` leaves an important gap in `question` worth probing "
                                + "further with one more question, rather than moving on?",
                        "Yes, there's a specific gap worth probing further.",
                        "No, the answer is complete enough to move on.")));

        double clarity = normalize(response.answers().get("clarity").score());
        double depth = normalize(response.answers().get("depth").score());
        double relevance = normalize(response.answers().get("relevance").score());
        double needsFollowUp = response.answers().get("needs_followup").probabilityOfYes();

        return new AnswerFeedback(clarity, depth, relevance, feedbackFor(clarity, depth, relevance), needsFollowUp, null);
    }

    private double normalize(double rawScore) {
        return rawScore / (LEVELS.size() - 1);
    }

    /** Code-composed, not model-generated prose - the weakest dimension drives the one suggestion given. */
    private String feedbackFor(double clarity, double depth, double relevance) {
        double min = Math.min(clarity, Math.min(depth, relevance));
        if (min >= 0.75) {
            return "Strong answer across clarity, depth, and relevance.";
        }
        if (min == relevance) {
            return "Stay tighter on what was actually asked - this answer drifts from the question.";
        }
        if (min == depth) {
            return "Go deeper - add specifics, tradeoffs, or reasoning rather than staying at a general level.";
        }
        return "Tighten the structure - lead with the key point, then support it, rather than meandering.";
    }

    /** {@code followUpQuestion} is filled in by the controller, not here - this service only judges. */
    public record AnswerFeedback(double clarity, double depth, double relevance, String suggestion,
            double needsFollowUpProbability, String followUpQuestion) {

        public AnswerFeedback withFollowUpQuestion(String followUpQuestion) {
            return new AnswerFeedback(clarity, depth, relevance, suggestion, needsFollowUpProbability, followUpQuestion);
        }
    }
}
