package ai.candidly.career.retrieval;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Stage 2 lexical filter (docs/03 §1: "PostgreSQL FTS (BM25-style) + hard gates"),
 * sibling to {@link VectorSearchService}'s dense retrieval. Postgres's own
 * {@code ts_rank}/{@code websearch_to_tsquery} stand in for a dedicated BM25 index -
 * good enough at this scale, and it's the same database rather than a second system to
 * keep in sync. See {@code sql/lexical-search-setup.sql} for the generated
 * {@code search_vector} column + GIN index this relies on.
 */
@Service
public class LexicalSearchService {

    private final JdbcTemplate jdbcTemplate;

    public LexicalSearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Job postings whose lexical content matches {@code queryText}, best rank first. */
    public List<UUID> topByLexicalRank(String queryText, int limit) {
        return jdbcTemplate.query(
                "SELECT id FROM job_posting "
                        + "WHERE search_vector @@ websearch_to_tsquery('english', ?) "
                        + "AND screening_decision <> 'BLOCK' "
                        + "ORDER BY ts_rank(search_vector, websearch_to_tsquery('english', ?)) DESC "
                        + "LIMIT ?",
                (rs, rowNum) -> UUID.fromString(rs.getString("id")),
                queryText, queryText, limit);
    }
}
