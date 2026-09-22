package ai.candidly.career.emailintake;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.candidly.career.audit.AuditEventType;
import ai.candidly.career.audit.AuditLedgerService;
import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.MatchScorecard;
import ai.candidly.career.domain.MatchScorecardRepository;
import ai.candidly.career.pipeline.Interview;
import ai.candidly.career.pipeline.InterviewMode;
import ai.candidly.career.pipeline.InterviewRepository;
import ai.candidly.career.pipeline.InterviewSource;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeQuestion;
import ai.candidly.career.typesafe.TypeSafeResponse;

/**
 * Classifies a candidate-pasted recruiter/ATS email and, when it's an interview
 * invitation about a posting the candidate is already known to be matched against,
 * creates an {@link Interview} record automatically. Deliberately manual paste, not a
 * real IMAP/Gmail OAuth integration - the honest-automation discipline this repo
 * already applies to discovery (real public APIs, no scraping past what's designed for
 * this use) cuts the other way for a private inbox: reading someone's email
 * automatically needs a real OAuth grant and consent flow this slice doesn't build, so
 * a human pastes the text in instead of the app silently reading their mail.
 *
 * <p>The email body is third-party text, same trust status as a job posting
 * (docs/02 §1 C-1) - no tool access (TypeSafe's primitives never carry any in this
 * repo), and only structured, closed-set judgments (classification, which known
 * application this is about, interview mode) go to the model. The one open-ended fact
 * this needs - an exact date/time - is deliberately NOT asked of TypeSafe at all:
 * {@link HeuristicDateTimeExtractor} handles it as plain code, per the typesafe-ai
 * skill's "select instead of generate" and "keep exact lookups in code" guidance, and
 * the result is always presented as an editable suggestion, never auto-confirmed.
 */
@Service
public class EmailIntakeService {

    private final MatchScorecardRepository matchScorecardRepository;
    private final InterviewRepository interviewRepository;
    private final TypeSafeClient typeSafeClient;
    private final EmailIntakeRecordRepository recordRepository;
    private final EmailReplyDraftService replyDraftService;
    private final AuditLedgerService auditLedgerService;

    public EmailIntakeService(MatchScorecardRepository matchScorecardRepository, InterviewRepository interviewRepository,
            TypeSafeClient typeSafeClient, EmailIntakeRecordRepository recordRepository,
            EmailReplyDraftService replyDraftService, AuditLedgerService auditLedgerService) {
        this.matchScorecardRepository = matchScorecardRepository;
        this.interviewRepository = interviewRepository;
        this.typeSafeClient = typeSafeClient;
        this.recordRepository = recordRepository;
        this.replyDraftService = replyDraftService;
        this.auditLedgerService = auditLedgerService;
    }

    @Transactional
    public EmailIntakeResult classify(Candidate candidate, String rawEmailText) {
        List<MatchScorecard> knownApplications = matchScorecardRepository.findByCandidateId(candidate.getId());

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("email_body", rawEmailText);

        Map<String, TypeSafeQuestion> questions = new LinkedHashMap<>();
        questions.put("email_type", TypeSafeQuestion.choice(
                "What kind of message is `email_body`, from a job candidate's perspective?",
                Map.of(
                        "INTERVIEW_INVITATION", "Inviting the candidate to interview or scheduling one",
                        "REJECTION", "Declining the candidate's application",
                        "FOLLOW_UP", "A status update, follow-up, or request for more information",
                        "OTHER", "Anything else - not application-related, or unclear")));
        questions.put("mode", TypeSafeQuestion.choice(
                "If `email_body` describes an interview, what format is indicated? Answer UNKNOWN if not stated or not applicable.",
                Map.of(
                        "VIRTUAL", "A video call (Zoom, Teams, Meet, etc.)",
                        "ONSITE", "In-person at an office",
                        "PHONE", "A phone call",
                        "UNKNOWN", "Not stated, or the email isn't about an interview")));

        Map<String, String> applicationOptions = new LinkedHashMap<>();
        for (MatchScorecard scorecard : knownApplications) {
            JobPosting job = scorecard.getJobPosting();
            applicationOptions.put(job.getId().toString(), job.getCompany() + " - " + job.getTitle());
        }
        applicationOptions.put("NONE", "Doesn't clearly match any of the listed applications");
        if (!knownApplications.isEmpty()) {
            state.put("candidate_known_applications", applicationOptions.values());
            questions.put("related_application", TypeSafeQuestion.choice(
                    "Which of `candidate_known_applications` (if any) is `email_body` about?", applicationOptions));
        }

        TypeSafeResponse response = typeSafeClient.ask(state, questions);

        EmailType emailType = EmailType.valueOf(response.answers().get("email_type").choice());
        InterviewMode mode = InterviewMode.valueOf(response.answers().get("mode").choice());
        String relatedKey = questions.containsKey("related_application")
                ? response.answers().get("related_application").choice()
                : "NONE";

        Optional<Instant> suggestedTime = HeuristicDateTimeExtractor.extract(rawEmailText);

        UUID createdInterviewId = null;
        UUID relatedJobPostingId = null;
        JobPosting relatedJob = null;
        String draftReply = null;
        EmailReviewStatus reviewStatus = EmailReviewStatus.NO_REPLY_NEEDED;
        if (emailType == EmailType.INTERVIEW_INVITATION && !"NONE".equals(relatedKey)) {
            UUID targetJobPostingId = UUID.fromString(relatedKey);
            relatedJobPostingId = targetJobPostingId;
            relatedJob = knownApplications.stream()
                    .map(MatchScorecard::getJobPosting)
                    .filter(j -> j.getId().equals(targetJobPostingId))
                    .findFirst()
                    .orElse(null);
            if (relatedJob != null) {
                Interview interview = interviewRepository.save(new Interview(candidate, relatedJob, suggestedTime.orElse(null),
                        mode, InterviewSource.EMAIL_EXTRACTED, "Extracted from pasted email"));
                createdInterviewId = interview.getId();
                draftReply = replyDraftService.draftInterviewReply(candidate, relatedJob, mode, suggestedTime.orElse(null));
                reviewStatus = EmailReviewStatus.NEEDS_REVIEW;
            }
        }

        EmailIntakeRecord record = recordRepository.save(new EmailIntakeRecord(candidate, relatedJob, rawEmailText, emailType,
                mode, suggestedTime.orElse(null), createdInterviewId, draftReply, reviewStatus));
        auditLedgerService.record(AuditEventType.EMAIL_CLASSIFIED, candidate.getId(),
                "record=%s type=%s relatedJob=%s".formatted(record.getId(), emailType, relatedJobPostingId));

        return new EmailIntakeResult(emailType, relatedJobPostingId, mode, suggestedTime.orElse(null), createdInterviewId,
                record.getId(), draftReply);
    }

    public record EmailIntakeResult(
            EmailType emailType,
            UUID relatedJobPostingId,
            InterviewMode mode,
            Instant suggestedScheduledAt,
            UUID createdInterviewId,
            UUID recordId,
            String draftReplyText) {
    }
}
