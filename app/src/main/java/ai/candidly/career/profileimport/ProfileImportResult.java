package ai.candidly.career.profileimport;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Best-effort structured draft extracted from a resume or pasted LinkedIn text - every
 * field is nullable/possibly-empty since extraction from free text is inherently
 * partial. Shaped like {@link ai.candidly.career.api.CandidateRequest} so the frontend
 * can pre-fill the onboarding wizard directly from this response, but nothing here is
 * persisted - the candidate reviews and edits every field before the existing
 * {@code POST /api/candidates} submits anything.
 */
public record ProfileImportResult(String fullName, String email, String location, String professionalSummary,
        Set<String> rawSkillMentions, List<ExperienceDraft> experiences) {

    public record ExperienceDraft(String employer, String title, LocalDate startDate, LocalDate endDate,
            String narrative, Set<String> rawSkillMentions) {
    }
}
