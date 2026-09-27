package ai.candidly.career.positioning;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import ai.candidly.career.domain.CandidateExperience;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeQuestion;
import ai.candidly.career.typesafe.TypeSafeResponse;
import ai.candidly.career.vault.Achievement;

/**
 * "What career seniority level does this candidate's demonstrated experience actually
 * read as" - a TypeSafe Score judgment (typesafe-ai skill primitive-choice table: a
 * degree along an ordered dimension), not a years-of-experience formula, because scope
 * of ownership and technical leadership signals vary independently of tenure (a person
 * with 8 years but narrow scope reads differently than one with 6 years and broad
 * cross-team ownership).
 *
 * <p>State is built only from the candidate's own self-authored Career Vault data
 * (experience history + achievements they entered themselves) - unlike job-posting text,
 * this is first-party data the candidate controls, so the C-1 "never see raw third-party
 * text" discipline that applies to tailoring/matching doesn't apply here.
 */
@Service
public class SeniorityLevelJudgeService {

    private static final List<String> LEVELS = List.of(
            "Early-career - individual contributor still building foundational skills, "
                    + "typically under 2 years of professional experience, limited independent ownership.",
            "Mid-level - independently owns well-scoped features or components, roughly "
                    + "2-5 years of experience, some technical decision-making within a defined area.",
            "Senior - owns significant systems end-to-end and mentors others, roughly "
                    + "5-9 years of experience, technical decisions affect a team's roadmap.",
            "Staff / Lead - influences technical direction across multiple teams or a whole "
                    + "product area, roughly 8-14 years of experience, sets technical standards others follow.",
            "Principal / Distinguished - shapes technical strategy at an organizational level, "
                    + "recognized cross-team authority, typically 12+ years with a track record of "
                    + "high-leverage architectural decisions.");

    private static final List<String> LEVEL_LABELS = List.of("ENTRY", "MID", "SENIOR", "STAFF", "PRINCIPAL");

    private final TypeSafeClient typeSafeClient;

    public SeniorityLevelJudgeService(TypeSafeClient typeSafeClient) {
        this.typeSafeClient = typeSafeClient;
    }

    /** @return empty if there's nothing to judge (no experience and no achievements on file). */
    public Optional<Assessment> judge(List<CandidateExperience> experiences, List<Achievement> achievements) {
        if (experiences.isEmpty() && achievements.isEmpty()) {
            return Optional.empty();
        }

        List<Map<String, Object>> history = experiences.stream()
                .map(exp -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("employer", exp.getEmployer());
                    row.put("title", exp.getTitle());
                    row.put("summary", exp.getNarrative());
                    row.put("verified_metrics", exp.getVerifiedMetrics());
                    return row;
                })
                .toList();

        List<Map<String, Object>> achievementRows = achievements.stream()
                .map(a -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("title", a.getTitle());
                    row.put("description", a.getDescription());
                    return row;
                })
                .toList();

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("candidate_history", history);
        state.put("candidate_achievements", achievementRows);

        TypeSafeResponse response = typeSafeClient.ask(state, Map.of(
                "seniority", TypeSafeQuestion.score(
                        "Based on `candidate_history` (their work experience, roles, and narrative "
                                + "descriptions) and `candidate_achievements` (standalone accomplishments), "
                                + "what career seniority level does this candidate's demonstrated experience "
                                + "most closely match? Judge by scope of ownership, technical leadership "
                                + "signals, and breadth of impact actually described - not job titles alone, "
                                + "since titles vary a lot by company.",
                        LEVELS)));

        var answer = response.answers().get("seniority");
        int index = Math.round(answer.score().floatValue());
        index = Math.max(0, Math.min(LEVEL_LABELS.size() - 1, index));
        double confidence = answer.confidence() == null ? 0.0 : answer.confidence();
        return Optional.of(new Assessment(LEVEL_LABELS.get(index), confidence));
    }

    public record Assessment(String level, double confidence) {
    }
}
