-- Spec 002: plain-language stories written from each item's title and feed excerpt.

create table stories (
  item_id        uuid primary key references items(id) on delete cascade,
  section        text not null check (section in
                   ('GLOBAL_AI_TECH', 'WORLD_BUSINESS', 'INDIA_AI', 'INNOVATIONS_RESEARCH')),
  headline       text not null check (char_length(headline) <= 120),
  summary        text not null check (char_length(summary) <= 1000),
  key_terms      jsonb not null default '[]',
  model          text not null,
  prompt_version text not null,
  written_at     timestamptz not null,
  input_tokens   int not null,
  output_tokens  int not null,
  attempts       int not null
);
create index stories_section_idx on stories (section);

alter table items add column story_note text check (char_length(story_note) <= 300);
alter table items add constraint items_story_status_check
  check (story_status in ('PENDING', 'PUBLISHED', 'NOT_AI', 'REJECTED', 'EXPIRED'));

create table writer_budget (
  day   date primary key,
  calls int not null default 0
);
