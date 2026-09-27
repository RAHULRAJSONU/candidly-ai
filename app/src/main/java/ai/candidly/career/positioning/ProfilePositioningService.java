package ai.candidly.career.positioning;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.taxonomy.SkillTaxonomy;
import ai.candidly.career.taxonomy.SkillTaxonomyEntry;
import ai.candidly.career.vault.CareerVaultService;
import ai.candidly.career.vault.CareerVaultService.CareerVaultView;

/**
 * Dynamically derives a candidate's target-role positioning from their own Career Vault
 * data + preferences, instead of requiring them to hand-type job-title keywords into
 * Autopilot settings (the ChatGPT-plan "define the target profile" step, scoped to what
 * this backend can judge without fabricating anything). Two genuine TypeSafe judgments
 * ({@link SeniorityLevelJudgeService}, {@link TitleFitJudgeService}) supply the semantic
 * calls; keyword suggestions are a deterministic lookup against the existing
 * {@link SkillTaxonomy} (no model call needed for a fact already on file), and the
 * rationale string is assembled in code from those outputs rather than a separate
 * free-text generation call - so nothing here is invented text, only a composition of
 * judgments already grounded in the candidate's own data.
 *
 * <p>This is advisory only: the result is a suggestion the candidate applies to their
 * own Autopilot settings (see {@code ProfilePositioningController}); it never writes to
 * {@code AutopilotSettings} itself, keeping the human-in-the-loop boundary the same shape
 * as everywhere else in this codebase.
 */
@Service
public class ProfilePositioningService {

    private final CareerVaultService careerVaultService;
    private final SeniorityLevelJudgeService seniorityLevelJudgeService;
    private final TitleFitJudgeService titleFitJudgeService;
    private final SkillTaxonomy skillTaxonomy;

    public ProfilePositioningService(CareerVaultService careerVaultService,
            SeniorityLevelJudgeService seniorityLevelJudgeService, TitleFitJudgeService titleFitJudgeService,
            SkillTaxonomy skillTaxonomy) {
        this.careerVaultService = careerVaultService;
        this.seniorityLevelJudgeService = seniorityLevelJudgeService;
        this.titleFitJudgeService = titleFitJudgeService;
        this.skillTaxonomy = skillTaxonomy;
    }

    public ProfilePositioningView derive(UUID candidateId) {
        CareerVaultView vault = careerVaultService.get(candidateId);
        Candidate candidate = vault.candidate();

        Set<String> skillNames = canonicalSkillNames(candidate.getSkillIds());

        boolean hasEnoughData = !vault.experiences().isEmpty() || !vault.achievements().isEmpty() || !skillNames.isEmpty();
        if (!hasEnoughData) {
            return new ProfilePositioningView(false, null, 0.0, List.of(), List.of(),
                    "Add some work experience, achievements, or skills to your Career Vault first - "
                            + "there's nothing yet to base a positioning suggestion on.");
        }

        Optional<SeniorityLevelJudgeService.Assessment> seniority = seniorityLevelJudgeService.judge(
                vault.experiences(), vault.achievements());
        List<TitleFitJudgeService.TitleSuggestion> titleSuggestions = titleFitJudgeService.judge(
                vault.experiences(), vault.achievements(), skillNames);

        List<String> suggestedKeywords = skillNames.stream().sorted().limit(12).toList();

        List<ProfilePositioningView.TitleSuggestion> titles = titleSuggestions.stream()
                .map(s -> new ProfilePositioningView.TitleSuggestion(s.title(), s.confidence()))
                .toList();

        String rationale = buildRationale(seniority, titles, suggestedKeywords, vault.completeness());

        return new ProfilePositioningView(true, seniority.map(SeniorityLevelJudgeService.Assessment::level).orElse(null),
                seniority.map(SeniorityLevelJudgeService.Assessment::confidence).orElse(0.0), titles, suggestedKeywords,
                rationale);
    }

    private Set<String> canonicalSkillNames(Set<String> skillIds) {
        Map<String, String> byId = skillTaxonomy.entries().stream()
                .collect(Collectors.toMap(SkillTaxonomyEntry::id, SkillTaxonomyEntry::canonicalName));
        return skillIds.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String buildRationale(Optional<SeniorityLevelJudgeService.Assessment> seniority,
            List<ProfilePositioningView.TitleSuggestion> titles, List<String> keywords, double completeness) {
        StringBuilder sb = new StringBuilder();
        if (seniority.isPresent()) {
            sb.append("Based on your Career Vault, your demonstrated experience reads as ")
                    .append(seniority.get().level()).append(" level. ");
        } else {
            sb.append("Not enough experience or achievements on file yet to judge seniority level. ");
        }
        if (!titles.isEmpty()) {
            String topTitles = titles.stream().limit(3)
                    .map(ProfilePositioningView.TitleSuggestion::title)
                    .collect(Collectors.joining(", "));
            sb.append("Your strongest-matching target roles are: ").append(topTitles).append(". ");
        } else {
            sb.append("No target-role suggestions clear the confidence bar yet - add more experience detail "
                    + "or achievements for a better read. ");
        }
        if (!keywords.isEmpty()) {
            sb.append("Suggested keywords, from your own listed skills: ")
                    .append(String.join(", ", keywords)).append(". ");
        }
        if (completeness < 1.0) {
            sb.append("Your Career Vault is ").append(Math.round(completeness * 100))
                    .append("% complete - filling in more sections will sharpen these suggestions.");
        }
        return sb.toString().trim();
    }
}
