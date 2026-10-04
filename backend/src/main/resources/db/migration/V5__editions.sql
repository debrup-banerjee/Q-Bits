-- Spec 006: daily digest editions.

create table editions (
  id              uuid primary key,
  cutoff_at       timestamptz not null unique,
  status          text not null check (status in
                    ('COLLECTING', 'SUBMITTED', 'RETRYING', 'PUBLISHED', 'FAILED')),
  batch_id        text,
  retry_batch_id  text,
  submitted       int not null default 0,
  published       int not null default 0,
  not_ai          int not null default 0,
  rejected        int not null default 0,
  created_at      timestamptz not null,
  last_polled_at  timestamptz,
  next_submit_at  timestamptz,
  published_at    timestamptz
);
create index editions_status_idx on editions (status, cutoff_at);

alter table items add column edition_id uuid references editions(id) on delete set null;
create index items_edition_idx on items (edition_id);

-- WRITTEN: story saved and valid, waiting for its edition to be published (R3.2).
alter table items drop constraint items_story_status_check;
alter table items add constraint items_story_status_check
  check (story_status in ('PENDING', 'WRITTEN', 'PUBLISHED', 'NOT_AI', 'REJECTED', 'EXPIRED'));

-- Items currently in an edition's retry round keep their failed rules here (as feedback).
alter table items add column retry_feedback text check (char_length(retry_feedback) <= 1000);
