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
 * S_dom via TypeSafe's Score primitive (composite-scoring pattern, typesafe-ai skill /
 * docs/typesafe/TYPESAFE_REFERENCE.md §7-8), replacing a plain embedding-cosine proxy.
 *
 * <p>"Has this candidate actually worked in this domain, and how deeply" is a judgment
 * call about a career history, not a similarity between two short strings - cosine
 * similarity between a pooled experience vector and an embedded domain word conflates
 * "mentions fintech-adjacent vocabulary" with "has real domain depth". A Score question
 * over the candidate's actual employer/title/narrative history is a better fit for what
 * this dimension is supposed to measure, per the primitive-choice table in
 * docs/typesafe/TYPESAFE_REFERENCE.md §2 ("Score: a position on an ordered, describable
 * spectrum").
 *
 * <p>Levels are concrete situations, not abstract degrees, per the Score primitive's
 * guidance - each is judged independently, so the model never sees "level 2" as a label,
 * only the description.
 */
@Service
public class DomainFitJudgeService {

    private static final List<String> LEVELS = List.of(
            "No experience in or adjacent to this domain anywhere in their history.",
            "Peripheral exposure only - e.g. built general-purpose tools or infrastructure "
                    + "for an organization in this domain, without the role itself being "
                    + "domain-specific work.",
            "At least one role with direct, hands-on responsibility for work specific to "
                    + "this domain.",
            "Multiple roles, or several years within one role, of direct hands-on "
                    + "responsibility for work specific to this domain.",
            "This domain has been the primary focus of most of their career - deep, "
                    + "specialist-level familiarity with its concerns.");

    private final TypeSafeClient typeSafeClient;

    public DomainFitJudgeService(TypeSafeClient typeSafeClient) {
        this.typeSafeClient = typeSafeClient;
    }

    /** @return a 0-1 normalized domain-fit score, or 0.0 if the job states no domain. */
    public double judge(List<CandidateExperience> experiences, JobPosting job) {
        if (job.getDomain() == null || job.getDomain().isBlank()) {
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
        state.put("target_domain", job.getDomain());
        state.put("target_role", job.getTitle());
        state.put("candidate_history", history);

        TypeSafeResponse response = typeSafeClient.ask(state, Map.of(
                "domain_fit", TypeSafeQuestion.score(
                        "How deeply has this candidate actually worked in the `target_domain` "
                                + "industry/domain, based on their `candidate_history`? Judge real, "
                                + "hands-on domain responsibility, not just proximity or keyword overlap.",
                        LEVELS)));

        double score = response.answers().get("domain_fit").score();
        return score / (LEVELS.size() - 1);
    }
}
