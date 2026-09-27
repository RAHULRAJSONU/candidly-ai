package ai.candidly.career.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ai.candidly.career.audit.AuditEventRepository;
import ai.candidly.career.audit.AuditEventType;
import ai.candidly.career.audit.AuditLedgerService;
import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateExperience;
import ai.candidly.career.domain.CandidateExperienceRepository;
import ai.candidly.career.domain.CandidateRepository;
import ai.candidly.career.domain.CandidatePhotoRepository;
import ai.candidly.career.domain.CandidateResumeRepository;
import ai.candidly.career.domain.MatchScorecardRepository;
import ai.candidly.career.domain.TailoredArtifactRepository;
import ai.candidly.career.domain.TailoringJobRepository;
import ai.candidly.career.demographics.CandidateDemographicsRepository;
import ai.candidly.career.autopilot.AutopilotSettingsRepository;
import ai.candidly.career.emailintake.EmailIntakeRecordRepository;
import ai.candidly.career.pipeline.InterviewRepository;
import ai.candidly.career.pipeline.ManualApplicationRepository;
import ai.candidly.career.pipeline.OfferRepository;
import ai.candidly.career.settings.CandidateSettingsRepository;
import ai.candidly.career.vault.AchievementRepository;
import ai.candidly.career.vault.CandidateSkillProfileRepository;
import ai.candidly.career.vault.CertificationRepository;
import ai.candidly.career.vault.EducationRepository;
import ai.candidly.career.vault.ProjectRepository;

@ExtendWith(MockitoExtension.class)
class CandidateDataSubjectServiceTest {

    @Mock
    private CandidateRepository candidateRepository;
    @Mock
    private CandidateExperienceRepository candidateExperienceRepository;
    @Mock
    private MatchScorecardRepository matchScorecardRepository;
    @Mock
    private TailoredArtifactRepository tailoredArtifactRepository;
    @Mock
    private TailoringJobRepository tailoringJobRepository;
    @Mock
    private CandidateDemographicsRepository candidateDemographicsRepository;
    @Mock
    private AchievementRepository achievementRepository;
    @Mock
    private EducationRepository educationRepository;
    @Mock
    private CertificationRepository certificationRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private CandidateSkillProfileRepository skillProfileRepository;
    @Mock
    private InterviewRepository interviewRepository;
    @Mock
    private OfferRepository offerRepository;
    @Mock
    private ManualApplicationRepository manualApplicationRepository;
    @Mock
    private EmailIntakeRecordRepository emailIntakeRecordRepository;
    @Mock
    private AutopilotSettingsRepository autopilotSettingsRepository;
    @Mock
    private CandidateResumeRepository candidateResumeRepository;
    @Mock
    private CandidatePhotoRepository candidatePhotoRepository;
    @Mock
    private CandidateSettingsRepository candidateSettingsRepository;
    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private AuditLedgerService auditLedgerService;

    private CandidateDataSubjectService service() {
        return new CandidateDataSubjectService(candidateRepository, candidateExperienceRepository,
                matchScorecardRepository, tailoredArtifactRepository, tailoringJobRepository,
                candidateDemographicsRepository, achievementRepository, educationRepository, certificationRepository,
                projectRepository, skillProfileRepository, interviewRepository, offerRepository, manualApplicationRepository,
                emailIntakeRecordRepository, autopilotSettingsRepository, candidateResumeRepository,
                candidatePhotoRepository, candidateSettingsRepository, auditEventRepository, auditLedgerService);
    }

    @Test
    void eraseDeletesEveryOwnedTableAndRecordsAnAuditEventWithoutDeletingAuditHistory() {
        UUID candidateId = UUID.randomUUID();
        Candidate candidate = new Candidate("Ada Lovelace", "ada@example.com", "Remote", Set.of("US"), 0, Set.of(), Set.of());
        CandidateExperience experience = new CandidateExperience(candidate, "Acme", "Engineer",
                LocalDate.of(2020, 1, 1), null, "Built things.", Set.of(), Set.of(), Set.of());

        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(tailoringJobRepository.findByCandidateId(candidateId)).thenReturn(List.of());
        when(tailoredArtifactRepository.findByCandidateId(candidateId)).thenReturn(List.of());
        when(matchScorecardRepository.findByCandidateId(candidateId)).thenReturn(List.of());
        when(candidateExperienceRepository.findByCandidateId(candidateId)).thenReturn(List.of(experience));

        service().erase(candidateId);

        verify(tailoringJobRepository).deleteAll(List.of());
        verify(tailoredArtifactRepository).deleteAll(List.of());
        verify(matchScorecardRepository).deleteAll(List.of());
        verify(candidateExperienceRepository).deleteAll(List.of(experience));
        verify(candidateDemographicsRepository).deleteByCandidateId(candidateId);
        verify(skillProfileRepository).deleteByCandidateId(candidateId);
        verify(candidateRepository).delete(candidate);
        verify(auditLedgerService).record(eq(AuditEventType.CANDIDATE_DATA_ERASED), eq(candidateId), any());
        verify(auditEventRepository, never()).deleteAll();
    }

    @Test
    void eraseOfUnknownCandidateThrowsWithoutTouchingAnyTable() {
        UUID candidateId = UUID.randomUUID();
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().erase(candidateId)).isInstanceOf(IllegalArgumentException.class);

        verify(tailoringJobRepository, never()).deleteAll(any());
        verify(auditLedgerService, never()).record(any(), any(), any());
    }

    @Test
    void exportBundlesEveryOwnedTablePlusDecisionHistory() {
        UUID candidateId = UUID.randomUUID();
        Candidate candidate = new Candidate("Ada Lovelace", "ada@example.com", "Remote", Set.of("US"), 0, Set.of(), Set.of());

        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(candidateExperienceRepository.findByCandidateId(candidateId)).thenReturn(List.of());
        when(matchScorecardRepository.findByCandidateId(candidateId)).thenReturn(List.of());
        when(tailoredArtifactRepository.findByCandidateId(candidateId)).thenReturn(List.of());
        when(candidateDemographicsRepository.findByCandidateId(candidateId)).thenReturn(Optional.empty());
        when(auditEventRepository.findBySubjectIdOrderByOccurredAtAsc(candidateId)).thenReturn(List.of());

        var export = service().export(candidateId);

        assertThat(export.candidate()).isEqualTo(candidate);
        verify(auditEventRepository, times(1)).findBySubjectIdOrderByOccurredAtAsc(candidateId);
    }
}
