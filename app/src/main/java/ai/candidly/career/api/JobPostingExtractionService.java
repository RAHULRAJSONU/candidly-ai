package ai.candidly.career.api;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import ai.candidly.career.taxonomy.DomainTaxonomy;
import ai.candidly.career.taxonomy.SkillTaxonomy;
import ai.candidly.career.taxonomy.SkillTaxonomyEntry;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeQuestion;
import ai.candidly.career.typesafe.TypeSafeResponse;

/**
 * Fills {@code mandatorySkillIds}/{@code preferredSkillIds}/{@code domain} for a
 * discovered posting whose adapter can't supply them - previously a known gap
 * (see {@code DiscoveryScheduler}'s prior javadoc) that silently made
 * {@code EligibilityGateService}'s mandatory-skill-coverage gate and
 * {@code CompositeScoringService}'s skill-Jaccard score component no-ops for every
 * Greenhouse/Lever posting, since both only ever saw empty sets.
 *
 * <p>One TypeSafe request holds one Choice question per {@link SkillTaxonomy} entry
 * (required / preferred / not mentioned) plus one Choice question for domain -
 * "select instead of generate" (typesafe-ai skill): the model can only pick a taxonomy id
 * or domain label that already exists, never invent one, matching the pattern already
 * used by {@link ai.candidly.career.taxonomy.SkillNormalizationService} and the bounded
 * fan-out shape of {@link FuzzyDuplicateJobPostingService}/
 * {@link ai.candidly.career.retrieval.CrossEncoderRerankService}. Runs on already-screened
 * {@code rawDescription} in {@link JobPostingIngestionService} - the same trust boundary
 * {@code JinaEmbeddingClient}'s post-screening embed call already relies on.
 */
@Service
public class JobPostingExtractionService {

    private static final String NO_DOMAIN_OPTION = "none_stated";
    private static final String REQUIRED = "required";
    private static final String PREFERRED = "preferred";

    private final SkillTaxonomy skillTaxonomy;
    private final DomainTaxonomy domainTaxonomy;
    private final TypeSafeClient typeSafeClient;

    public JobPostingExtractionService(SkillTaxonomy skillTaxonomy, DomainTaxonomy domainTaxonomy,
            TypeSafeClient typeSafeClient) {
        this.skillTaxonomy = skillTaxonomy;
        this.domainTaxonomy = domainTaxonomy;
        this.typeSafeClient = typeSafeClient;
    }

    public ExtractedAttributes extract(String rawDescription) {
        List<SkillTaxonomyEntry> entries = skillTaxonomy.entries();

        Map<String, TypeSafeQuestion> questions = new LinkedHashMap<>();
        for (SkillTaxonomyEntry entry : entries) {
            questions.put("skill_" + entry.id(), TypeSafeQuestion.choice(
                    "Does `job_description` mention " + entry.canonicalName() + " (" + entry.description()
                            + ") as a required qualification, a preferred/nice-to-have qualification, or not at all?",
                    Map.of(
                            REQUIRED, "Stated as a required/mandatory qualification for this role",
                            PREFERRED, "Stated as a preferred, nice-to-have, or bonus qualification",
                            "not_mentioned", "Not mentioned as a qualification for this role")));
        }

        Map<String, String> domainOptions = new LinkedHashMap<>();
        for (String domain : domainTaxonomy.domains()) {
            domainOptions.put(domain, "The role's primary industry/domain is " + domain);
        }
        domainOptions.put(NO_DOMAIN_OPTION, "No clear single industry/domain is stated or implied");
        questions.put("domain", TypeSafeQuestion.choice(
                "Which industry/domain, if any, does `job_description` primarily belong to?", domainOptions));

        TypeSafeResponse response = typeSafeClient.ask(Map.of("job_description", rawDescription), questions);

        Set<String> mandatory = new HashSet<>();
        Set<String> preferred = new HashSet<>();
        for (SkillTaxonomyEntry entry : entries) {
            String choice = response.answers().get("skill_" + entry.id()).choice();
            if (REQUIRED.equals(choice)) {
                mandatory.add(entry.id());
            } else if (PREFERRED.equals(choice)) {
                preferred.add(entry.id());
            }
        }

        String domainChoice = response.answers().get("domain").choice();
        String domain = (domainChoice == null || NO_DOMAIN_OPTION.equals(domainChoice)) ? null : domainChoice;

        return new ExtractedAttributes(mandatory, preferred, domain);
    }

    public record ExtractedAttributes(Set<String> mandatorySkillIds, Set<String> preferredSkillIds, String domain) {
    }
}
