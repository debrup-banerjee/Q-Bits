-- Spec 008: one story per event. DUPLICATE stories are held back; duplicate_of names the kept one.

alter table items drop constraint items_story_status_check;
alter table items add constraint items_story_status_check
  check (story_status in
    ('PENDING', 'WRITTEN', 'PUBLISHED', 'NOT_AI', 'REJECTED', 'EXPIRED', 'DUPLICATE'));

-- Retention (7 days) may delete the kept item before its duplicate.
alter table items add column duplicate_of uuid references items (id) on delete set null;

alter table editions add column duplicates int not null default 0;
alter table editions add column dedupe_attempts int not null default 0;
