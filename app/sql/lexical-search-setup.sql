-- Stage 2 lexical filter (docs/03 §1: "PostgreSQL FTS (BM25-style) + hard gates").
-- Run once against `candidly` after the app has created job_posting at least once:
--
--   psql -h localhost -p 5432 -U postgres -d candidly -f sql/lexical-search-setup.sql
--
-- Generated column + GIN index so ts_rank / websearch_to_tsquery queries
-- (retrieval.LexicalSearchService) don't recompute to_tsvector per row per query.

ALTER TABLE job_posting
  ADD COLUMN IF NOT EXISTS search_vector tsvector
  GENERATED ALWAYS AS (
    to_tsvector('english',
      coalesce(title, '') || ' ' ||
      coalesce(company, '') || ' ' ||
      coalesce(domain, '') || ' ' ||
      coalesce(raw_description, ''))
  ) STORED;

CREATE INDEX IF NOT EXISTS idx_job_posting_search_vector_gin
  ON job_posting USING gin (search_vector);
