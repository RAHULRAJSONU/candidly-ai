package ai.candidly.career.ats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.TailoredArtifact;
import ai.candidly.career.domain.TailoredArtifactStatus;

class AtsScoreServiceTest {

    private final AtsScoreService service = new AtsScoreService();

    @Test
    void wellFormedResumeScoresHighAndCoversMandatoryKeywords() {
        JobPosting job = new JobPosting("hash-1", "Acme", "Backend Engineer", "Remote", true, null, null, Set.of(),
                Set.of("skill.java", "skill.spring"), Set.of(), "fintech", 5, "raw");
        Candidate candidate = new Candidate("Test", "t@example.com", "Remote", Set.of("US"), 0, Set.of(), Set.of());
        String content = "SUMMARY\nExperienced engineer.\n\nEXPERIENCE\nAcme, 2020-2024, built systems using Java and Spring.\n"
                + "Delivered scalable services and mentored engineers across several teams. "
                + ("padding word ".repeat(100))
                + "\n\nEDUCATION\nSKILLS\nJava, Spring";
        TailoredArtifact artifact = new TailoredArtifact(candidate, job, content, 1, true, List.of(),
                TailoredArtifactStatus.PENDING_APPROVAL);

        var result = service.score(artifact);

        assertThat(result.score()).isGreaterThanOrEqualTo(80);
        assertThat(result.checks()).extracting(AtsScoreService.AtsCheck::passed).doesNotContain(false);
    }

    @Test
    void emptyContentFailsMostChecks() {
        JobPosting job = new JobPosting("hash-2", "Acme", "Backend Engineer", "Remote", true, null, null, Set.of(),
                Set.of("skill.java"), Set.of(), "fintech", 5, "raw");
        Candidate candidate = new Candidate("Test", "t@example.com", "Remote", Set.of("US"), 0, Set.of(), Set.of());
        TailoredArtifact artifact = new TailoredArtifact(candidate, job, "", 0, false, List.of(),
                TailoredArtifactStatus.NEEDS_HUMAN_REVIEW);

        var result = service.score(artifact);

        assertThat(result.score()).isLessThan(60);
    }
}
