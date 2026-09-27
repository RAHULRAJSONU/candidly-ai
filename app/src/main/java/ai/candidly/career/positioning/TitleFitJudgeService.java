package ai.candidly.career.positioning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import ai.candidly.career.domain.CandidateExperience;
import ai.candidly.career.taxonomy.JobTitleTaxonomy;
import ai.candidly.career.taxonomy.JobTitleTaxonomyEntry;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeQuestion;
import ai.candidly.career.typesafe.TypeSafeResponse;
import ai.candidly.career.vault.Achievement;

/**
 * "Which target-role archetypes would this candidate credibly be searching for" as a
 * TypeSafe fan-out - one Noul per {@link JobTitleTaxonomy} entry against the same
 * candidate-profile state, in one request (the retrieval package's
 * CrossEncoderRerankService cookbook pattern). Selecting from the fixed taxonomy, rather
 * than letting a model generate title strings freely, means the result can never contain
 * an invented or implausible title (typesafe-ai skill: "select instead of generate").
 */
@Service
public class TitleFitJudgeService {

    /** Below this probability a title isn't surfaced as a suggestion at all. */
    private static final double MIN_CONFIDENCE = 0.55;
    private static final int MAX_SUGGESTIONS = 8;

    private final TypeSafeClient typeSafeClient;
    private final JobTitleTaxonomy jobTitleTaxonomy;

    public TitleFitJudgeService(TypeSafeClient typeSafeClient, JobTitleTaxonomy jobTitleTaxonomy) {
        this.typeSafeClient = typeSafeClient;
        this.jobTitleTaxonomy = jobTitleTaxonomy;
    }

    public List<TitleSuggestion> judge(List<CandidateExperience> experiences, List<Achievement> achievements,
            java.util.Set<String> skillCanonicalNames) {
        if (experiences.isEmpty() && achievements.isEmpty() && skillCanonicalNames.isEmpty()) {
            return List.of();
        }

        List<JobTitleTaxonomyEntry> titles = jobTitleTaxonomy.entries();

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("candidate_history", experiences.stream()
                .map(exp -> Map.of(
                        "employer", exp.getEmployer(),
                        "title", exp.getTitle(),
                        "summary", exp.getNarrative()))
                .toList());
        state.put("candidate_achievements", achievements.stream()
                .map(a -> Map.of("title", a.getTitle(), "description", a.getDescription()))
                .toList());
        state.put("candidate_skills", skillCanonicalNames);

        List<Map<String, String>> titleRows = new ArrayList<>();
        for (JobTitleTaxonomyEntry entry : titles) {
            titleRows.add(Map.of("name", entry.canonicalName(), "description", entry.description()));
        }
        state.put("target_titles", titleRows);

        Map<String, TypeSafeQuestion> questions = new LinkedHashMap<>();
        for (int i = 0; i < titles.size(); i++) {
            questions.put("fit_" + i, TypeSafeQuestion.noul(
                    "Given `candidate_history`, `candidate_achievements`, and `candidate_skills`, "
                            + "would `target_titles[" + i + "]` be a credible, well-matched target role for "
                            + "this candidate to search for and apply to today - matching their demonstrated "
                            + "seniority and skill area, not just surface keyword overlap?",
                    "A credible, well-matched target role for this candidate right now",
                    "Not a good match - wrong seniority level or skill area"));
        }

        TypeSafeResponse response = typeSafeClient.ask(state, questions);

        return IntStream.range(0, titles.size())
                .mapToObj(i -> new TitleSuggestion(titles.get(i).canonicalName(), response.answers().get("fit_" + i).noul()))
                .filter(s -> s.confidence() >= MIN_CONFIDENCE)
                .sorted(Comparator.comparingDouble(TitleSuggestion::confidence).reversed())
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    public record TitleSuggestion(String title, double confidence) {
    }
}
