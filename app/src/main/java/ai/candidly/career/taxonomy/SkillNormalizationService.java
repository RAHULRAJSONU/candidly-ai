package ai.candidly.career.taxonomy;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import ai.candidly.career.ai.JinaEmbeddingClient;
import ai.candidly.career.ai.VectorMath;
import ai.candidly.career.typesafe.TypeSafeAnswer;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeQuestion;
import ai.candidly.career.typesafe.TypeSafeResponse;

/**
 * "Select instead of generate" (typesafe-ai skill, pattern: value extraction): code
 * retrieves the plausible candidates via embedding similarity, TypeSafe's Choice
 * primitive only disambiguates among them. The model can never introduce a skill id
 * that wasn't already a real taxonomy entry.
 */
@Service
public class SkillNormalizationService {

    private static final int TOP_K_CANDIDATES = 5;
    private static final String NO_MATCH_OPTION = "none_of_the_above";

    private final SkillTaxonomy taxonomy;
    private final JinaEmbeddingClient embeddingClient;
    private final TypeSafeClient typeSafeClient;

    private Map<String, float[]> taxonomyEmbeddings;

    public SkillNormalizationService(SkillTaxonomy taxonomy, JinaEmbeddingClient embeddingClient,
            TypeSafeClient typeSafeClient) {
        this.taxonomy = taxonomy;
        this.embeddingClient = embeddingClient;
        this.typeSafeClient = typeSafeClient;
    }

    /**
     * Lazy, not @PostConstruct: this avoids a live embedding-API call at application
     * startup (so the context can come up without valid provider keys/network, e.g. in
     * a plain unit-test slice), at the cost of the first normalize() call paying the
     * taxonomy-embedding latency.
     */
    private synchronized Map<String, float[]> taxonomyEmbeddings() {
        if (taxonomyEmbeddings == null) {
            List<SkillTaxonomyEntry> entries = taxonomy.entries();
            List<float[]> vectors = embeddingClient.embedAll(entries.stream().map(SkillTaxonomyEntry::description).toList());
            Map<String, float[]> byId = new LinkedHashMap<>();
            for (int i = 0; i < entries.size(); i++) {
                byId.put(entries.get(i).id(), vectors.get(i));
            }
            taxonomyEmbeddings = byId;
        }
        return taxonomyEmbeddings;
    }

    /** @return the canonical taxonomy skill id, or empty if nothing plausible matched. */
    public Optional<String> normalize(String rawSkillMention) {
        float[] mentionVector = embeddingClient.embed(rawSkillMention);
        Map<String, float[]> embeddings = taxonomyEmbeddings();

        List<SkillTaxonomyEntry> candidates = taxonomy.entries().stream()
                .sorted(Comparator.comparingDouble(
                        (SkillTaxonomyEntry e) -> VectorMath.cosineSimilarity(mentionVector, embeddings.get(e.id())))
                        .reversed())
                .limit(TOP_K_CANDIDATES)
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Map<String, String> options = new LinkedHashMap<>();
        for (SkillTaxonomyEntry candidate : candidates) {
            options.put(candidate.id(), candidate.canonicalName() + " - " + candidate.description());
        }
        options.put(NO_MATCH_OPTION, "None of the above genuinely denote the same skill as the mention");

        TypeSafeResponse response = typeSafeClient.ask(
                Map.of("raw_skill_mention", rawSkillMention),
                Map.of("matching_taxonomy_entry", TypeSafeQuestion.choice(
                        "Which taxonomy entry, if any, denotes the same skill as `raw_skill_mention`? "
                                + "Match on meaning (e.g. \"Postgres\" and \"PostgreSQL\" are the same skill), not exact spelling.",
                        options)));

        TypeSafeAnswer answer = response.answers().get("matching_taxonomy_entry");
        if (answer == null || answer.choice() == null || NO_MATCH_OPTION.equals(answer.choice())) {
            return Optional.empty();
        }
        return Optional.of(answer.choice());
    }
}
