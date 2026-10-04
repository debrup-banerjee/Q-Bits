-- Earliest time a source may be fetched again after the publisher sent Retry-After on a 429 or
-- 503 (spec 001 R7.1). Null when no such limit applies.
alter table sources_state add column retry_not_before_at timestamptz;
