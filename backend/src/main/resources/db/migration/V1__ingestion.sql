-- Spec 001: feed ingestion.
-- items stores metadata only. No column can hold an article body (principles: content and legal).

create table sources_state (
  source_id            text primary key,
  etag                 text,
  last_modified        text,
  last_fetched_at      timestamptz,
  last_success_at      timestamptz,
  consecutive_failures int  not null default 0,
  health               text not null default 'OK' check (health in ('OK', 'DEGRADED'))
);

create table robots_cache (
  host        text primary key,
  body        text,
  status_code int not null,
  fetched_at  timestamptz not null
);

create table items (
  id               uuid primary key,
  source_id        text not null,
  canonical_url    text not null unique,
  source_title     text not null check (char_length(source_title) <= 500),
  source_excerpt   text check (char_length(source_excerpt) <= 300),
  title_key        text not null,
  published_at     timestamptz not null,
  date_estimated   boolean not null default false,
  fetched_at       timestamptz not null,
  prefilter_score  numeric(4,3) not null,
  story_status     text not null default 'PENDING',
  hidden           boolean not null default false
);
create index items_source_title_idx on items (source_id, title_key, published_at);
create index items_status_idx       on items (story_status, published_at);
create index items_published_idx    on items (published_at);

create table source_fetch_log (
  id            bigserial primary key,
  source_id     text not null,
  started_at    timestamptz not null,
  duration_ms   int not null,
  status        text not null check (status in
                  ('OK', 'NOT_MODIFIED', 'BLOCKED_BY_ROBOTS', 'ROBOTS_UNAVAILABLE', 'FAILED')),
  http_status   int,
  items_new     int not null default 0,
  items_dup     int not null default 0,
  items_not_ai  int not null default 0,
  items_bad     int not null default 0,
  items_too_old int not null default 0,
  error         text check (char_length(error) <= 500)
);
create index source_fetch_log_source_idx on source_fetch_log (source_id, started_at desc);

create table shedlock (
  name       varchar(64)  primary key,
  lock_until timestamp    not null,
  locked_at  timestamp    not null,
  locked_by  varchar(255) not null
);
