-- Run once against the `candidly` database after the app has booted at least once
-- (Hibernate ddl-auto=update creates the `vector(1024)` columns but has no concept of
-- pgvector's HNSW index type, so it never creates these). docs/01 §4.2: m=16,
-- ef_construction=64 is the reasonable build default; ef_search is set per-connection
-- via spring.datasource.hikari.connection-init-sql in application.yml instead, since
-- that's a per-session GUC, not an index property.
--
--   psql -h localhost -p 5432 -U postgres -d candidly -f sql/pgvector-indexes.sql

CREATE EXTENSION IF NOT EXISTS vector;

CREATE INDEX IF NOT EXISTS idx_candidate_experience_embedding_hnsw
  ON candidate_experience USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS idx_job_posting_embedding_hnsw
  ON job_posting USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);
