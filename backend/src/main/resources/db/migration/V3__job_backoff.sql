-- Shared back-off state for background jobs, so every app instance respects it (spec 002 R9.2).
create table job_backoff (
  name            text primary key,
  next_attempt_at timestamptz not null,
  backoff_seconds int not null
);
