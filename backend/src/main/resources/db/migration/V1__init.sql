-- Corpus of court decisions + chunks with pgvector embeddings and fulltext index.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE TABLE decision (
  id                 UUID PRIMARY KEY,           -- uuid from rozhodnuti.justice.cz
  ecli               TEXT,
  case_number        TEXT NOT NULL,
  court              TEXT NOT NULL,
  court_code         TEXT,
  decided_on         DATE,
  published_on       DATE,
  subject            TEXT,
  keywords           TEXT[] NOT NULL DEFAULT '{}',
  provisions         TEXT[] NOT NULL DEFAULT '{}',   -- zminenaUstanoveni, e.g. "§ 2991 z. č. 89/2012 Sb."
  result_types       TEXT[] NOT NULL DEFAULT '{}',   -- caseResultType
  verdict_text       TEXT,
  justification_text TEXT,
  source_url         TEXT NOT NULL,
  ingested_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX decision_published_on_idx ON decision (published_on);
CREATE INDEX decision_provisions_idx ON decision USING gin (provisions);

-- unaccent() is not IMMUTABLE, so wrap it for use in a generated column.
CREATE OR REPLACE FUNCTION immutable_unaccent(text) RETURNS text
  LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
  AS $$ SELECT public.unaccent('public.unaccent', $1) $$;

CREATE TABLE chunk (
  id          BIGSERIAL PRIMARY KEY,
  decision_id UUID NOT NULL REFERENCES decision(id) ON DELETE CASCADE,
  seq         INT NOT NULL,
  section     TEXT NOT NULL,                 -- verdict | justification
  content     TEXT NOT NULL,
  embedding   vector(1024),                  -- bge-m3 via Ollama
  tsv         tsvector GENERATED ALWAYS AS (to_tsvector('simple', immutable_unaccent(content))) STORED
);
CREATE INDEX chunk_decision_idx ON chunk (decision_id);
CREATE INDEX chunk_embedding_idx ON chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX chunk_tsv_idx ON chunk USING gin (tsv);
