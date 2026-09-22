package ai.candidly.career.api;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.candidly.career.audit.AuditEvent;
import ai.candidly.career.audit.AuditEventRepository;
import ai.candidly.career.audit.AuditEventType;
import ai.candidly.career.audit.AuditLedgerService;
import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateExperience;
import ai.candidly.career.domain.CandidateExperienceRepository;
import ai.candidly.career.domain.CandidateRepository;
import ai.candidly.career.domain.MatchScorecard;
import ai.candidly.career.domain.MatchScorecardRepository;
import ai.candidly.career.domain.TailoredArtifact;
import ai.candidly.career.domain.TailoredArtifactRepository;
import ai.candidly.career.domain.TailoringJob;
import ai.candidly.career.domain.TailoringJobRepository;
import ai.candidly.career.demographics.CandidateDemographics;
import ai.candidly.career.demographics.CandidateDemographicsRepository;
import ai.candidly.career.pipeline.Interview;
import ai.candidly.career.pipeline.InterviewRepository;
import ai.candidly.career.pipeline.ManualApplication;
import ai.candidly.career.pipeline.ManualApplicationRepository;
import ai.candidly.career.pipeline.Offer;
import ai.candidly.career.pipeline.OfferRepository;
import ai.candidly.career.vault.Achievement;
import ai.candidly.career.vault.AchievementRepository;
import ai.candidly.career.vault.Certification;
import ai.candidly.career.vault.CertificationRepository;
import ai.candidly.career.vault.Education;
import ai.candidly.career.vault.EducationRepository;
import ai.candidly.career.autopilot.AutopilotSettingsRepository;
import ai.candidly.career.emailintake.EmailIntakeRecord;
import ai.candidly.career.emailintake.EmailIntakeRecordRepository;

/**
 * Candidate data-subject rights (docs/02 §4.1 GDPR/UK GDPR): export (portability) and
 * erasure, both operating on the same set of tables - everything a candidate ID owns
 * across {@code candidate_experience} (embeddings included, per the go-live checklist's
 * "incl. vectors"), {@code match_scorecard}, {@code tailored_artifact},
 * {@code tailoring_job}, and {@code candidate_demographics} (voluntary self-ID is still
 * personal data - GDPR Art. 9 special-category, in fact - so it's exported and erased
 * with everything else even though {@code matching}/{@code tailoring} never read it; see
 * {@code demographics.CandidateDemographics}'s javadoc), and the Career Vault/pipeline
 * tables added later ({@code achievement}, {@code education}, {@code certification},
 * {@code interview}, {@code job_offer}, {@code manual_application}, {@code
 * email_intake_record}, {@code autopilot_settings}) - anything with a {@code
 * candidate_id} FK gets the same treatment, on the same principle, not just the tables
 * that existed when this service was first written.
 *
 * <p>Erasure deliberately does NOT touch {@code audit_event} rows where {@code subjectId}
 * is this candidate's ID. Docs/02 asks for two things that are in direct tension for a
 * candidate who has been matched or tailored for: §4.1 erasure ("hard delete with
 * cascade") and §4.3/§5 tamper-evident, append-only audit history (the very ledger this
 * repo built specifically so no row can be deleted without every later row's hash
 * failing to verify - see AuditLedgerService). Resolving that in the ledger's favor is
 * deliberate: {@code AuditEvent.subjectId} is a bare UUID with no FK and no join back to
 * PII once the candidate row is gone, and {@code AuditEvent.details} is already built
 * from screening/reason-code strings, not name/email/free-form candidate data (the one
 * documented exception is a human reviewer's free-text note on
 * TAILORED_ARTIFACT_REVIEWED - see TailoringReviewService - which is an accepted residual
 * risk this slice doesn't scrub). A dangling audit trail pointing at a UUID with no
 * remaining candidate record is the intended post-erasure state, not a bug.
 *
 * <p>Deletion order matches the FK graph, most-dependent first: TailoringJob (has an FK
 * to TailoredArtifact via resultArtifact) before TailoredArtifact, then the
 * candidate-owned tables with no incoming FKs (MatchScorecard, CandidateExperience),
 * then Candidate itself. Each step is flushed before the next - the same defensive
 * flush-per-step discipline as the delete-then-save flush-ordering sharp edge elsewhere
 * in this repo (CLAUDE.md), applied here to delete-then-delete instead.
 */
@Service
public class CandidateDataSubjectService {

    private final CandidateRepository candidateRepository;
    private final CandidateExperienceRepository candidateExperienceRepository;
    private final MatchScorecardRepository matchScorecardRepository;
    private final TailoredArtifactRepository tailoredArtifactRepository;
    private final TailoringJobRepository tailoringJobRepository;
    private final CandidateDemographicsRepository candidateDemographicsRepository;
    private final AchievementRepository achievementRepository;
    private final EducationRepository educationRepository;
    private final CertificationRepository certificationRepository;
    private final InterviewRepository interviewRepository;
    private final OfferRepository offerRepository;
    private final ManualApplicationRepository manualApplicationRepository;
    private final EmailIntakeRecordRepository emailIntakeRecordRepository;
    private final AutopilotSettingsRepository autopilotSettingsRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditLedgerService auditLedgerService;

    public CandidateDataSubjectService(CandidateRepository candidateRepository,
            CandidateExperienceRepository candidateExperienceRepository,
            MatchScorecardRepository matchScorecardRepository,
            TailoredArtifactRepository tailoredArtifactRepository,
            TailoringJobRepository tailoringJobRepository,
            CandidateDemographicsRepository candidateDemographicsRepository,
            AchievementRepository achievementRepository,
            EducationRepository educationRepository,
            CertificationRepository certificationRepository,
            InterviewRepository interviewRepository,
            OfferRepository offerRepository,
            ManualApplicationRepository manualApplicationRepository,
            EmailIntakeRecordRepository emailIntakeRecordRepository,
            AutopilotSettingsRepository autopilotSettingsRepository,
            AuditEventRepository auditEventRepository,
            AuditLedgerService auditLedgerService) {
        this.candidateRepository = candidateRepository;
        this.candidateExperienceRepository = candidateExperienceRepository;
        this.matchScorecardRepository = matchScorecardRepository;
        this.tailoredArtifactRepository = tailoredArtifactRepository;
        this.tailoringJobRepository = tailoringJobRepository;
        this.candidateDemographicsRepository = candidateDemographicsRepository;
        this.achievementRepository = achievementRepository;
        this.educationRepository = educationRepository;
        this.certificationRepository = certificationRepository;
        this.interviewRepository = interviewRepository;
        this.offerRepository = offerRepository;
        this.manualApplicationRepository = manualApplicationRepository;
        this.emailIntakeRecordRepository = emailIntakeRecordRepository;
        this.autopilotSettingsRepository = autopilotSettingsRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditLedgerService = auditLedgerService;
    }

    @Transactional(readOnly = true)
    public CandidateDataExport export(UUID candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + candidateId));

        return new CandidateDataExport(
                candidate,
                candidateExperienceRepository.findByCandidateId(candidateId),
                matchScorecardRepository.findByCandidateId(candidateId),
                tailoredArtifactRepository.findByCandidateId(candidateId),
                candidateDemographicsRepository.findByCandidateId(candidateId).orElse(null),
                achievementRepository.findByCandidateId(candidateId),
                educationRepository.findByCandidateId(candidateId),
                certificationRepository.findByCandidateId(candidateId),
                interviewRepository.findByCandidateId(candidateId),
                offerRepository.findByCandidateId(candidateId),
                manualApplicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId),
                emailIntakeRecordRepository.findByCandidateIdOrderByClassifiedAtDesc(candidateId),
                autopilotSettingsRepository.findByCandidateId(candidateId).orElse(null),
                auditEventRepository.findBySubjectIdOrderByOccurredAtAsc(candidateId));
    }

    @Transactional
    public void erase(UUID candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + candidateId));

        List<TailoringJob> jobs = tailoringJobRepository.findByCandidateId(candidateId);
        tailoringJobRepository.deleteAll(jobs);
        tailoringJobRepository.flush();

        List<TailoredArtifact> artifacts = tailoredArtifactRepository.findByCandidateId(candidateId);
        tailoredArtifactRepository.deleteAll(artifacts);
        tailoredArtifactRepository.flush();

        List<MatchScorecard> scorecards = matchScorecardRepository.findByCandidateId(candidateId);
        matchScorecardRepository.deleteAll(scorecards);
        matchScorecardRepository.flush();

        List<CandidateExperience> experiences = candidateExperienceRepository.findByCandidateId(candidateId);
        candidateExperienceRepository.deleteAll(experiences);
        candidateExperienceRepository.flush();

        candidateDemographicsRepository.deleteByCandidateId(candidateId);
        candidateDemographicsRepository.flush();

        List<Achievement> achievements = achievementRepository.findByCandidateId(candidateId);
        achievementRepository.deleteAll(achievements);
        achievementRepository.flush();

        List<Education> education = educationRepository.findByCandidateId(candidateId);
        educationRepository.deleteAll(education);
        educationRepository.flush();

        List<Certification> certifications = certificationRepository.findByCandidateId(candidateId);
        certificationRepository.deleteAll(certifications);
        certificationRepository.flush();

        List<Interview> interviews = interviewRepository.findByCandidateId(candidateId);
        interviewRepository.deleteAll(interviews);
        interviewRepository.flush();

        List<Offer> offers = offerRepository.findByCandidateId(candidateId);
        offerRepository.deleteAll(offers);
        offerRepository.flush();

        List<ManualApplication> manualApplications = manualApplicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        manualApplicationRepository.deleteAll(manualApplications);
        manualApplicationRepository.flush();

        List<EmailIntakeRecord> emailIntakeRecords = emailIntakeRecordRepository.findByCandidateIdOrderByClassifiedAtDesc(candidateId);
        emailIntakeRecordRepository.deleteAll(emailIntakeRecords);
        emailIntakeRecordRepository.flush();

        autopilotSettingsRepository.findByCandidateId(candidateId).ifPresent(autopilotSettingsRepository::delete);
        autopilotSettingsRepository.flush();

        candidateRepository.delete(candidate);
        candidateRepository.flush();

        auditLedgerService.record(AuditEventType.CANDIDATE_DATA_ERASED, candidateId,
                ("erasure request: deleted %d tailoring job(s), %d tailored artifact(s), %d match scorecard(s), "
                        + "%d experience record(s), %d achievement(s), %d education record(s), %d certification(s), "
                        + "%d interview(s), %d offer(s), %d manual application(s), %d email intake record(s)")
                        .formatted(jobs.size(), artifacts.size(), scorecards.size(), experiences.size(),
                                achievements.size(), education.size(), certifications.size(), interviews.size(),
                                offers.size(), manualApplications.size(), emailIntakeRecords.size()));
    }

    public record CandidateDataExport(
            Candidate candidate,
            List<CandidateExperience> experiences,
            List<MatchScorecard> matchScorecards,
            List<TailoredArtifact> tailoredArtifacts,
            CandidateDemographics demographics,
            List<Achievement> achievements,
            List<Education> education,
            List<Certification> certifications,
            List<Interview> interviews,
            List<Offer> offers,
            List<ManualApplication> manualApplications,
            List<EmailIntakeRecord> emailIntakeRecords,
            ai.candidly.career.autopilot.AutopilotSettings autopilotSettings,
            List<AuditEvent> decisionHistory) {
    }
}
