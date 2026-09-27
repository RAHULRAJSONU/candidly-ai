package ai.candidly.career.autopilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ai.candidly.career.audit.AuditEventType;
import ai.candidly.career.audit.AuditLedgerService;
import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.MatchScorecard;
import ai.candidly.career.typesafe.TypeSafeAnswer;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeResponse;

@ExtendWith(MockitoExtension.class)
class AutopilotExclusionJudgeServiceTest {

    @Mock
    private TypeSafeClient typeSafeClient;
    @Mock
    private AuditLedgerService auditLedgerService;

    @Test
    void dropsPostingsTheModelJudgesConflictWithAStatedExclusionAndAuditLogsThem() {
        Candidate candidate = new Candidate("Ada", "ada@example.com", "Remote", Set.of("US"), 0, Set.of(), Set.of());
        MatchScorecard conflicting = scorecard(candidate, "business-dev", "Business Development Representative");
        MatchScorecard clean = scorecard(candidate, "backend-eng", "Backend Engineer");

        // "no sales" doesn't literally appear in "Business Development Representative" - this is
        // exactly the paraphrase the substring pre-filter in AutopilotService can't catch.
        when(typeSafeClient.ask(any(), anyMap())).thenReturn(new TypeSafeResponse("jev-1.13.0", Map.of(
                "conflicts_0", noul(0.88),
                "conflicts_1", noul(0.03))));

        UUID candidateId = UUID.randomUUID();
        List<MatchScorecard> kept = new AutopilotExclusionJudgeService(typeSafeClient, auditLedgerService)
                .filterConflicting(candidateId, Set.of("no sales"), List.of(conflicting, clean));

        assertThat(kept).containsExactly(clean);
        verify(auditLedgerService).record(eq(AuditEventType.AUTOPILOT_EXCLUSION_FILTERED), eq(candidateId), any());
    }

    @Test
    void emptyExclusionsShortCircuitsWithoutCallingTypeSafe() {
        Candidate candidate = new Candidate("Ada", "ada@example.com", "Remote", Set.of("US"), 0, Set.of(), Set.of());
        MatchScorecard scorecard = scorecard(candidate, "backend-eng", "Backend Engineer");

        List<MatchScorecard> kept = new AutopilotExclusionJudgeService(typeSafeClient, auditLedgerService)
                .filterConflicting(UUID.randomUUID(), Set.of(), List.of(scorecard));

        assertThat(kept).containsExactly(scorecard);
        verify(typeSafeClient, never()).ask(any(), anyMap());
    }

    private static TypeSafeAnswer noul(double probability) {
        return new TypeSafeAnswer("noul", probability, null, null, null, null, null);
    }

    private MatchScorecard scorecard(Candidate candidate, String sourceId, String title) {
        JobPosting job = new JobPosting(sourceId, "Acme", title, "Remote", true,
                null, null, Set.of("US"), Set.of(), Set.of(), "tech", 0, "raw description");
        return new MatchScorecard(candidate, job, true, 0.8, 0.8, 0.8, 0.8, 0.8, true, List.of());
    }
}
