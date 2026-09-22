package ai.candidly.career.interviewprep;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateExperienceRepository;
import ai.candidly.career.domain.CandidateRepository;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.JobPostingRepository;
import jakarta.validation.Valid;

/**
 * Practice-only tool (docs/03 §3's "calibrate/practice against real outcomes" spirit,
 * applied to interview prep rather than scoring). Deliberately stateless - no
 * persisted attempt history - the same scope simplification {@code
 * ScoringConsistencyService} already makes for its own diagnostic tool; this isn't part
 * of the compliance-critical decision path, so it doesn't need an audit trail.
 * Personalized/follow-up question text round-trips through the client instead (see
 * {@link InterviewQuestion#generated}) rather than being persisted and looked up by id.
 */
@RestController
@RequestMapping("/api/interview-prep")
public class InterviewPrepController {

    /** Above this `needs_followup` probability, generate and return a follow-up question. */
    private static final double FOLLOW_UP_THRESHOLD = 0.6;
    private static final CareerLevel DEFAULT_CAREER_LEVEL = CareerLevel.MID;
    private static final InterviewStage DEFAULT_INTERVIEW_STAGE = InterviewStage.TECHNICAL_DEEP_DIVE;

    private final InterviewQuestionBank questionBank;
    private final MockInterviewAnswerJudgeService judgeService;
    private final PersonalizedQuestionGeneratorService questionGeneratorService;
    private final FollowUpQuestionGeneratorService followUpQuestionGeneratorService;
    private final CareerLevelEstimator careerLevelEstimator;
    private final CandidateRepository candidateRepository;
    private final CandidateExperienceRepository candidateExperienceRepository;
    private final JobPostingRepository jobPostingRepository;

    public InterviewPrepController(InterviewQuestionBank questionBank, MockInterviewAnswerJudgeService judgeService,
            PersonalizedQuestionGeneratorService questionGeneratorService,
            FollowUpQuestionGeneratorService followUpQuestionGeneratorService, CareerLevelEstimator careerLevelEstimator,
            CandidateRepository candidateRepository, CandidateExperienceRepository candidateExperienceRepository,
            JobPostingRepository jobPostingRepository) {
        this.questionBank = questionBank;
        this.judgeService = judgeService;
        this.questionGeneratorService = questionGeneratorService;
        this.followUpQuestionGeneratorService = followUpQuestionGeneratorService;
        this.careerLevelEstimator = careerLevelEstimator;
        this.candidateRepository = candidateRepository;
        this.candidateExperienceRepository = candidateExperienceRepository;
        this.jobPostingRepository = jobPostingRepository;
    }

    @GetMapping("/questions")
    public List<InterviewQuestion> questions(@RequestParam(required = false) InterviewCategory category,
            @RequestParam(required = false) UUID candidateId, @RequestParam(required = false) UUID jobPostingId,
            @RequestParam(required = false) CareerLevel careerLevel, @RequestParam(required = false) InterviewStage stage) {
        if (candidateId == null || jobPostingId == null) {
            return category == null ? questionBank.all() : questionBank.byCategory(category);
        }
        InterviewCategory resolvedCategory = category == null ? InterviewCategory.BEHAVIORAL : category;
        Candidate candidate = findCandidate(candidateId);
        JobPosting job = findJobPosting(jobPostingId);
        return questionGeneratorService.generate(candidate, candidateExperienceRepository.findByCandidateId(candidateId),
                job, careerLevel == null ? DEFAULT_CAREER_LEVEL : careerLevel,
                stage == null ? DEFAULT_INTERVIEW_STAGE : stage, resolvedCategory);
    }

    @GetMapping("/suggested-level")
    public SuggestedLevel suggestedLevel(@RequestParam UUID candidateId) {
        findCandidate(candidateId);
        var experience = candidateExperienceRepository.findByCandidateId(candidateId);
        return new SuggestedLevel(careerLevelEstimator.estimate(experience));
    }

    @PostMapping("/answers")
    public MockInterviewAnswerJudgeService.AnswerFeedback submitAnswer(@Valid @RequestBody AnswerRequest request) {
        String questionText = resolveQuestionText(request);
        JobPosting job = findJobPosting(request.jobPostingId());
        CareerLevel careerLevel = request.careerLevel() == null ? DEFAULT_CAREER_LEVEL : request.careerLevel();
        InterviewStage stage = request.interviewStage() == null ? DEFAULT_INTERVIEW_STAGE : request.interviewStage();

        MockInterviewAnswerJudgeService.AnswerFeedback feedback = judgeService.judge(questionText, request.answer(),
                job, careerLevel, stage);

        if (feedback.needsFollowUpProbability() > FOLLOW_UP_THRESHOLD) {
            String followUp = followUpQuestionGeneratorService.generate(questionText, request.answer(), job, careerLevel);
            if (followUp != null) {
                feedback = feedback.withFollowUpQuestion(followUp);
            }
        }
        return feedback;
    }

    private String resolveQuestionText(AnswerRequest request) {
        boolean hasId = request.questionId() != null && !request.questionId().isBlank();
        boolean hasText = request.questionText() != null && !request.questionText().isBlank();
        if (hasId == hasText) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Exactly one of questionId or questionText must be provided");
        }
        if (hasText) {
            return request.questionText();
        }
        try {
            return questionBank.get(request.questionId()).text();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), e.getMessage());
        }
    }

    private Candidate findCandidate(UUID candidateId) {
        return candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found"));
    }

    private JobPosting findJobPosting(UUID jobPostingId) {
        return jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Job posting not found"));
    }

    public record SuggestedLevel(CareerLevel level) {
    }
}
