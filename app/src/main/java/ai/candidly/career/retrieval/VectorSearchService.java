package ai.candidly.career.retrieval;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.pgvector.PGvector;

/**
 * Stage 3 dense retrieval (docs/03 §1, docs/01 §4): approximate-nearest-neighbor search
 * over pgvector's HNSW index via the cosine-distance operator {@code <=>}, run as plain
 * JDBC rather than through Hibernate/JPQL. Hibernate has no clean way to bind a vector
 * literal into a native-query {@code ORDER BY} expression's operand; a direct
 * {@link JdbcTemplate} call is simpler and more honest about this being a hot-path SQL
 * operation, not an entity load - callers still go through
 * {@code MatchOrchestratorService} afterward for the actual gate+score decision, this
 * only narrows the candidate set.
 */
@Service
public class VectorSearchService {

    private final JdbcTemplate jdbcTemplate;

    public VectorSearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Nearest job postings to {@code queryVector} by cosine distance, closest first. */
    public List<UUID> nearestJobPostingIds(float[] queryVector, int limit) {
        return jdbcTemplate.query(
                "SELECT id FROM job_posting WHERE embedding IS NOT NULL AND screening_decision <> 'BLOCK' "
                        + "ORDER BY embedding <=> ? LIMIT ?",
                (rs, rowNum) -> UUID.fromString(rs.getString("id")),
                new PGvector(queryVector), limit);
    }

    /** Nearest candidate-experience rows to {@code queryVector} by cosine distance, closest first. */
    public List<UUID> nearestCandidateExperienceIds(float[] queryVector, int limit) {
        return jdbcTemplate.query(
                "SELECT id FROM candidate_experience WHERE embedding IS NOT NULL "
                        + "ORDER BY embedding <=> ? LIMIT ?",
                (rs, rowNum) -> UUID.fromString(rs.getString("id")),
                new PGvector(queryVector), limit);
    }
}
