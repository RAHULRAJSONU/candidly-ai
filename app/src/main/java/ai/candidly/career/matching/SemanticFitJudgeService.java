package ai.candidly.career.matching;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import ai.candidly.career.domain.CandidateExperience;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeQuestion;
import ai.candidly.career.typesafe.TypeSafeResponse;

/**
 * S_sem via TypeSafe's Score primitive (composite-scoring pattern, typesafe-ai skill /
 * docs/typesafe/TYPESAFE_REFERENCE.md §7-8), replacing the embedding-cosine proxy noted
 * as an open gap in CompositeScoringService's javadoc.
 *
 * <p>"Does the overall shape of this career read like this job" is a judgment about fit
 * in scope, seniority, and the nature of day-to-day work - not the explicit skill-keyword
 * overlap S_skill already covers, and not the industry/domain depth S_dom already covers
 * (see DomainFitJudgeService). Cosine similarity between a pooled experience vector and a
 * job-description vector conflates all of that into one number with no way to see which
 * dimension drove it; a Score question that names the dimension explicitly ("scope,
 * seniority, and nature of work", holding skills and domain constant) is a better fit for
 * what S_sem is supposed to isolate, per the primitive-choice table in
 * docs/typesafe/TYPESAFE_REFERENCE.md §2.
 *
 * <p>State deliberately excludes {@code JobPosting.rawDescription} (attacker-controlled,
 * only screened for hazards TypeSafe's Noul battery was built to catch - docs/02 §1
 * C-1) in favor of the already-screened, structured title/domain/skill fields, the same
 * discipline {@code GroundedResumeGenerator} follows for the tailoring critic.
 */
@Service
public class SemanticFitJudgeService {

    private static final List<String> LEVELS = List.of(
            "No meaningful alignment - the scope, seniority, and day-to-day nature of "
                    + "their roles have little in common with what this role actually requires.",
            "Loose alignment - some surface similarity in function, but meaningful gaps in "
                    + "scope, seniority, or the nature of the work.",
            "Reasonable alignment - the roles are recognizably similar in scope and nature "
                    + "of work, with minor gaps.",
            "Strong alignment - the career trajectory closely matches what this role calls "
                    + "for in scope, seniority, and day-to-day nature of work.",
            "Excellent alignment - this candidate's career reads like a natural, "
                    + "ready-made fit for exactly this kind of role.");

    private final TypeSafeClient typeSafeClient;

    public SemanticFitJudgeService(TypeSafeClient typeSafeClient) {
        this.typeSafeClient = typeSafeClient;
    }

    /** @return a 0-1 normalized holistic fit score, or 0.0 if the candidate has no experience history. */
    public double judge(List<CandidateExperience> experiences, JobPosting job) {
        if (experiences.isEmpty()) {
            return 0.0;
        }

        List<Map<String, String>> history = experiences.stream()
                .map(exp -> {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("employer", exp.getEmployer());
                    row.put("title", exp.getTitle());
                    row.put("summary", exp.getNarrative());
                    return row;
                })
                .toList();

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("target_title", job.getTitle());
        state.put("target_domain", job.getDomain());
        state.put("target_mandatory_skills", job.getMandatorySkillIds());
        state.put("target_preferred_skills", job.getPreferredSkillIds());
        state.put("target_min_years_experience", job.getMinYearsExperience());
        state.put("candidate_history", history);

        TypeSafeResponse response = typeSafeClient.ask(state, Map.of(
                "semantic_fit", TypeSafeQuestion.score(
                        "Holding named skills and industry/domain aside (those are judged "
                                + "elsewhere), how well does the overall SHAPE of this candidate's "
                                + "career - scope of responsibility, seniority level, and the nature "
                                + "of their day-to-day work per `candidate_history` - align with what "
                                + "someone in the `target_title` role described by `target_domain`, "
                                + "`target_mandatory_skills`/`target_preferred_skills`, and "
                                + "`target_min_years_experience` would actually be doing?",
                        LEVELS)));

        double score = response.answers().get("semantic_fit").score();
        return score / (LEVELS.size() - 1);
    }
}
