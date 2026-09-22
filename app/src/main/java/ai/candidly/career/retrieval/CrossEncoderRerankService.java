package ai.candidly.career.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import ai.candidly.career.domain.CandidateExperience;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeQuestion;
import ai.candidly.career.typesafe.TypeSafeResponse;

/**
 * Stage 5's cross-encoder rerank (docs/03 §1, docs/01 §2: "RRF fusion + cross-encoder
 * rerank"), implemented as a TypeSafe fan-out (typesafe-ai skill / cookbook
 * `rerank_typesafe`: one Noul per candidate against the same query state, in one
 * request) rather than a dedicated cross-encoder model - the judgment is exactly the
 * kind of narrow relevance call System One is built for, and it's already the model
 * this repo standardizes on for judgments.
 *
 * <p>The point of reranking here is cost, not just order: {@code
 * matching.DomainFitJudgeService} makes one TypeSafe call per posting it scores, so
 * reranking down to a tighter shortlist before the full deterministic gate+composite
 * pipeline runs means that per-posting judgment only fires for postings this cheaper,
 * batched check already thinks are plausible - not the whole RRF-fused oversample.
 */
@Service
public class CrossEncoderRerankService {

    private final TypeSafeClient typeSafeClient;

    public CrossEncoderRerankService(TypeSafeClient typeSafeClient) {
        this.typeSafeClient = typeSafeClient;
    }

    /** @return {@code candidates} reordered by relevance probability, most relevant first. */
    public List<JobPosting> rerank(List<CandidateExperience> candidateProfile, List<JobPosting> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("candidate_profile", candidateProfile.stream()
                .map(exp -> Map.of(
                        "title", exp.getTitle(),
                        "employer", exp.getEmployer(),
                        "summary", exp.getNarrative(),
                        "skills", exp.getSkillIds()))
                .toList());

        List<Map<String, Object>> jobs = new ArrayList<>();
        for (JobPosting job : candidates) {
            jobs.add(Map.of(
                    "title", job.getTitle(),
                    "company", job.getCompany(),
                    "domain", job.getDomain() == null ? "" : job.getDomain(),
                    "mandatory_skills", job.getMandatorySkillIds(),
                    "preferred_skills", job.getPreferredSkillIds()));
        }
        state.put("jobs", jobs);

        Map<String, TypeSafeQuestion> questions = new LinkedHashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            questions.put("relevant_" + i, TypeSafeQuestion.noul(
                    "Given `candidate_profile`, is `jobs[" + i + "]` a posting this candidate would be "
                            + "a strong, qualified match for - comparable seniority and real skill overlap, "
                            + "not just topical similarity?",
                    "A strong, qualified match worth the candidate's time to pursue",
                    "A weak or mismatched fit: wrong seniority, domain, or skill set"));
        }

        TypeSafeResponse response = typeSafeClient.ask(state, questions);

        record Scored(JobPosting job, double relevance) {
        }

        return IntStream.range(0, candidates.size())
                .mapToObj(i -> new Scored(candidates.get(i), response.answers().get("relevant_" + i).noul()))
                .sorted(Comparator.comparingDouble(Scored::relevance).reversed())
                .map(Scored::job)
                .toList();
    }
}
