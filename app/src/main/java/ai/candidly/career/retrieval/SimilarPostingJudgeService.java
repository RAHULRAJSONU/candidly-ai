package ai.candidly.career.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.ScreeningDecision;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeQuestion;
import ai.candidly.career.typesafe.TypeSafeResponse;

/**
 * Backend "Similar Jobs" for a reference posting - replaces what {@code JobDiscovery.tsx}'s
 * client-side {@code findSimilarJobs} used to do as a plain skill/domain-overlap count over
 * the already-loaded postings list (a deliberate MVP cut, documented in that function's own
 * comment as "not a new backend endpoint or judgment call").
 *
 * <p>A cheap deterministic overlap count still runs first, purely to shrink the field to
 * {@link #CANDIDATE_POOL_SIZE} plausible postings - the same "cheap filter before the
 * TypeSafe call" discipline {@code retrieval.CrossEncoderRerankService} and
 * {@code api.FuzzyDuplicateJobPostingService} both already apply, so a Noul call doesn't fire
 * once per row in the whole job-posting table. Only then does one Noul per remaining
 * candidate - fanned into a single request, same fan-out shape as {@code
 * CrossEncoderRerankService} - ask whether it's a substantively similar role, not just a
 * shared-keyword match, which is what the old client-side count could never tell apart (e.g.
 * a "Senior Backend Engineer, Payments" and a "Backend Engineer, Data Platform" posting can
 * share several skill tags while being quite different roles). Only structured,
 * already-screened fields go into the judgment - never {@link JobPosting#getRawDescription()}
 * - per docs/02 §1 C-1.
 */
@Service
public class SimilarPostingJudgeService {

    private static final int CANDIDATE_POOL_SIZE = 20;
    private static final int RESULT_LIMIT = 5;
    private static final double SIMILARITY_THRESHOLD = 0.5;

    private final TypeSafeClient typeSafeClient;

    public SimilarPostingJudgeService(TypeSafeClient typeSafeClient) {
        this.typeSafeClient = typeSafeClient;
    }

    /** @return up to {@link #RESULT_LIMIT} postings from {@code allPostings} judged substantively
     * similar to {@code reference}, most similar first. */
    public List<JobPosting> findSimilar(JobPosting reference, List<JobPosting> allPostings) {
        Set<String> referenceSkills = union(reference.getMandatorySkillIds(), reference.getPreferredSkillIds());

        record Overlapped(JobPosting job, int score) {
        }

        List<JobPosting> pool = allPostings.stream()
                .filter(job -> !job.getId().equals(reference.getId()))
                .filter(job -> job.getScreeningDecision() != ScreeningDecision.BLOCK)
                .map(job -> {
                    Set<String> jobSkills = union(job.getMandatorySkillIds(), job.getPreferredSkillIds());
                    long overlap = jobSkills.stream().filter(referenceSkills::contains).count();
                    int domainBonus = reference.getDomain() != null && reference.getDomain().equals(job.getDomain()) ? 2 : 0;
                    return new Overlapped(job, (int) overlap + domainBonus);
                })
                .filter(o -> o.score() > 0)
                .sorted(Comparator.comparingInt(Overlapped::score).reversed())
                .limit(CANDIDATE_POOL_SIZE)
                .map(Overlapped::job)
                .toList();

        if (pool.isEmpty()) {
            return List.of();
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("reference_posting", postingFields(reference));

        List<Map<String, Object>> candidates = pool.stream().map(this::postingFields).toList();
        state.put("candidate_postings", candidates);

        Map<String, TypeSafeQuestion> questions = new LinkedHashMap<>();
        for (int i = 0; i < pool.size(); i++) {
            questions.put("similar_" + i, TypeSafeQuestion.noul(
                    "Is `candidate_postings[" + i + "]` a substantively similar role to "
                            + "`reference_posting` - comparable seniority, domain, and core skill profile, "
                            + "not just a few shared skill tags?",
                    "A substantively similar role the candidate would view as an alternative to the reference posting",
                    "A meaningfully different role despite any surface-level keyword overlap"));
        }

        TypeSafeResponse response = typeSafeClient.ask(state, questions);

        record Scored(JobPosting job, double similarity) {
        }

        return IntStream.range(0, pool.size())
                .mapToObj(i -> new Scored(pool.get(i), response.answers().get("similar_" + i).noul()))
                .filter(s -> s.similarity() >= SIMILARITY_THRESHOLD)
                .sorted(Comparator.comparingDouble(Scored::similarity).reversed())
                .limit(RESULT_LIMIT)
                .map(Scored::job)
                .toList();
    }

    private Map<String, Object> postingFields(JobPosting job) {
        return Map.of(
                "title", job.getTitle(),
                "company", job.getCompany(),
                "domain", job.getDomain() == null ? "" : job.getDomain(),
                "mandatory_skills", job.getMandatorySkillIds(),
                "preferred_skills", job.getPreferredSkillIds());
    }

    private Set<String> union(Set<String> a, Set<String> b) {
        Set<String> result = new java.util.HashSet<>(a);
        result.addAll(b);
        return result;
    }
}
