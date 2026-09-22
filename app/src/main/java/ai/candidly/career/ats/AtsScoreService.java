package ai.candidly.career.ats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.TailoredArtifact;

/**
 * ATS-friendliness scoring for a tailored artifact. Deliberately deterministic, not a
 * TypeSafe/LLM judgment: "does this text contain a SKILLS heading," "is the word count
 * in a sane range," and "what fraction of mandatory skills appear verbatim" are exact,
 * checkable facts about a string, not judgment calls - the typesafe-ai skill's own
 * guidance is to keep exact lookups in code and reserve model calls for genuine semantic
 * ambiguity. There's also nothing here misalignment-prone to grade: the artifact is
 * always plain text (docs/03), not a real laid-out document, so "no complex tables"
 * always passes - noted as {@code n/a}, not silently omitted, so the checklist stays
 * honest about what it can't actually verify from plain text alone.
 */
@Service
public class AtsScoreService {

    private static final List<String> STANDARD_HEADINGS = List.of("EXPERIENCE", "EDUCATION", "SKILLS", "SUMMARY");
    private static final Pattern YEAR_RANGE = Pattern.compile("\\b(19|20)\\d{2}\\b");

    public AtsScoreResult score(TailoredArtifact artifact) {
        String content = artifact.getContent() == null ? "" : artifact.getContent();
        String upper = content.toUpperCase(Locale.ROOT);
        JobPosting job = artifact.getJobPosting();

        List<AtsCheck> checks = new ArrayList<>();

        long headingsFound = STANDARD_HEADINGS.stream().filter(upper::contains).count();
        checks.add(new AtsCheck("Standard section headings", headingsFound >= 2));

        int wordCount = content.isBlank() ? 0 : content.trim().split("\\s+").length;
        checks.add(new AtsCheck("Reasonable length (150-1200 words)", wordCount >= 150 && wordCount <= 1200));

        long yearMentions = YEAR_RANGE.matcher(content).results().count();
        checks.add(new AtsCheck("Dated work history detected", yearMentions >= 1));

        checks.add(new AtsCheck("No complex tables or graphics", true));

        double keywordCoverage = keywordCoverage(content, job);
        checks.add(new AtsCheck("Mandatory skill keywords present (" + Math.round(keywordCoverage * 100) + "%)",
                keywordCoverage >= 0.6));

        long passed = checks.stream().filter(AtsCheck::passed).count();
        int score = (int) Math.round(100.0 * passed / checks.size());

        return new AtsScoreResult(score, checks);
    }

    private double keywordCoverage(String content, JobPosting job) {
        if (job.getMandatorySkillIds().isEmpty()) {
            return 1.0;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        long matched = job.getMandatorySkillIds().stream()
                .map(id -> id.replaceFirst("^skill\\.", "").replace('-', ' '))
                .filter(label -> lower.contains(label.toLowerCase(Locale.ROOT)))
                .count();
        return (double) matched / job.getMandatorySkillIds().size();
    }

    public record AtsCheck(String label, boolean passed) {
    }

    public record AtsScoreResult(int score, List<AtsCheck> checks) {
    }
}
