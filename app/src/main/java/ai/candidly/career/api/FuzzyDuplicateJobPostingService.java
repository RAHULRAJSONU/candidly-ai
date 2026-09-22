package ai.candidly.career.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.JobPostingRepository;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeQuestion;
import ai.candidly.career.typesafe.TypeSafeResponse;

/**
 * Catches the dedupe gap {@code JobPostingIngestionService}'s exact SHA-256 hash can't:
 * the same underlying opening re-posted with a reworded title, a slightly different
 * location string, or cross-posted to another board under a new {@code sourceId} -
 * common in practice since discovery pulls from a live Greenhouse board that itself
 * aggregates postings a company may also submit by hand. Whether two postings describe
 * "the same job" is a judgment call about intent, not a string-distance threshold, so
 * this is a TypeSafe Noul fan-out (one per same-company candidate, typesafe-ai skill /
 * `rerank_typesafe` cookbook pattern - same shape as {@code CrossEncoderRerankService})
 * rather than fuzzy string matching.
 *
 * <p>Candidate generation is exact-company-match only (no fuzzy company matching, no
 * cross-company search) to keep the fan-out small and the judgment meaningful - "is this
 * the same opening" only makes sense once the employer is already known to match.
 * Candidates are capped at {@link #MAX_CANDIDATES} for the same cost reason
 * {@code CrossEncoderRerankService} caps its shortlist.
 *
 * <p>State deliberately excludes {@code rawDescription} of both the target and every
 * candidate: the incoming posting hasn't been screened yet at the point this runs
 * (dedupe happens before the prompt-injection screen in {@code JobPostingIngestionService
 * .ingest}), so its raw text is still attacker-controlled per docs/02 §1 C-1 - only the
 * structured fields a submitter/adapter fills in are compared.
 *
 * <p>{@link #DUPLICATE_THRESHOLD} (and the fields fed into the judgment) are this
 * implementation's own starting point, not a spec value - calibrate against real
 * discovered postings per docs/03 §3 before trusting it to silently merge postings
 * unattended.
 */
@Service
public class FuzzyDuplicateJobPostingService {

    private static final int MAX_CANDIDATES = 10;
    private static final double DUPLICATE_THRESHOLD = 0.75;

    private final JobPostingRepository jobPostingRepository;
    private final TypeSafeClient typeSafeClient;

    public FuzzyDuplicateJobPostingService(JobPostingRepository jobPostingRepository, TypeSafeClient typeSafeClient) {
        this.jobPostingRepository = jobPostingRepository;
        this.typeSafeClient = typeSafeClient;
    }

    /** @return the existing posting this request is a re-post/cross-post of, if any. */
    public Optional<JobPosting> findDuplicate(JobPostingRequest request) {
        List<JobPosting> candidates = jobPostingRepository.findByCompanyIgnoreCase(request.company()).stream()
                .limit(MAX_CANDIDATES)
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("target", postingFields(request.title(), request.location(), request.remote(),
                request.mandatorySkillMentions(), request.preferredSkillMentions(), request.domain(),
                request.minYearsExperience()));

        List<Map<String, Object>> candidateFields = new ArrayList<>();
        for (JobPosting candidate : candidates) {
            candidateFields.add(postingFields(candidate.getTitle(), candidate.getLocation(), candidate.isRemote(),
                    candidate.getMandatorySkillIds(), candidate.getPreferredSkillIds(), candidate.getDomain(),
                    candidate.getMinYearsExperience()));
        }
        state.put("candidates", candidateFields);

        Map<String, TypeSafeQuestion> questions = new LinkedHashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            questions.put("same_opening_" + i, TypeSafeQuestion.noul(
                    "`target` and `candidates[" + i + "]` are both postings already known to be from the same "
                            + "company. Do they describe the SAME underlying job opening - a re-post, an edit, or "
                            + "a cross-post of the identical role - rather than two genuinely different openings "
                            + "(different team, seniority, or scope) that merely share an employer?",
                    "The same opening, posted or edited more than once",
                    "A genuinely different opening at the same company"));
        }

        TypeSafeResponse response = typeSafeClient.ask(state, questions);

        record Scored(JobPosting job, double probability) {
        }

        return IntStream.range(0, candidates.size())
                .mapToObj(i -> new Scored(candidates.get(i), response.answers().get("same_opening_" + i).noul()))
                .max(java.util.Comparator.comparingDouble(Scored::probability))
                .filter(scored -> scored.probability() >= DUPLICATE_THRESHOLD)
                .map(Scored::job);
    }

    private Map<String, Object> postingFields(String title, String location, boolean remote,
            java.util.Set<String> mandatorySkills, java.util.Set<String> preferredSkills, String domain,
            int minYearsExperience) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("title", title);
        fields.put("location", location);
        fields.put("remote", remote);
        fields.put("mandatory_skills", mandatorySkills == null ? java.util.Set.of() : mandatorySkills);
        fields.put("preferred_skills", preferredSkills == null ? java.util.Set.of() : preferredSkills);
        fields.put("domain", domain == null ? "" : domain);
        fields.put("min_years_experience", minYearsExperience);
        return fields;
    }
}
