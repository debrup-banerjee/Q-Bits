-- Spec 005: open-source links found in feed entries. Only URLs, names and statuses are stored.

create table item_resources (
  id            bigserial primary key,
  item_id       uuid not null references items(id) on delete cascade,
  type          text not null check (type in ('CODE', 'MODEL', 'DATASET', 'PAPER')),
  host          text not null check (host in ('GITHUB', 'GITLAB', 'HUGGINGFACE', 'ARXIV')),
  url           text not null check (char_length(url) <= 300),
  name          text not null check (char_length(name) <= 200),
  origin        text not null check (origin in ('ENTRY_LINK', 'DESCRIPTION', 'CONTENT')),
  status        text not null default 'PENDING'
                  check (status in ('PENDING', 'VERIFIED', 'NOT_FOUND', 'CHECK_FAILED')),
  attempts      int not null default 0,
  next_check_at timestamptz not null,
  checked_at    timestamptz,
  unique (item_id, url)
);
create index item_resources_due_idx on item_resources (status, next_check_at);
create index item_resources_item_idx on item_resources (item_id);

create table link_check_cache (
  url        text primary key,
  status     text not null check (status in ('VERIFIED', 'NOT_FOUND')),
  canonical  text,
  checked_at timestamptz not null
);
