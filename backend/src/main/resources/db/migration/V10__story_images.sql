-- Spec 009: a picture for every story, chosen only from sources that allow it.

-- The image a feed attached to an entry. Stored only for sources with a recorded image
-- permission in config/sources.yml; only the URL is kept, never the image itself.
create table item_feed_images (
  item_id  uuid primary key references items(id) on delete cascade,
  url      text not null check (url like 'https://%' and char_length(url) <= 600),
  found_at timestamptz not null
);

-- The picture chosen for a story. FOUND rows carry a hotlinked image and its credit; NONE means
-- no safe picture was found, so clients draw the story's own cover art. RETRY rows wait for
-- next_try_at after a failed lookup.
create table story_images (
  item_id      uuid primary key references items(id) on delete cascade,
  status       text not null check (status in ('FOUND', 'NONE', 'RETRY')),
  kind         text check (kind in ('PHOTO', 'PUBLISHER')),
  provider     text check (char_length(provider) <= 100),
  provider_id  text check (char_length(provider_id) <= 200),
  url          text check (url like 'https://%' and char_length(url) <= 1000),
  page_url     text check (char_length(page_url) <= 1000),
  alt          text check (char_length(alt) <= 300),
  credit       text check (char_length(credit) <= 200),
  credit_url   text check (char_length(credit_url) <= 1000),
  color        text check (color ~ '^#[0-9a-fA-F]{6}$'),
  query        text check (char_length(query) <= 120),
  attempts     int not null default 0,
  next_try_at  timestamptz,
  chosen_at    timestamptz not null,
  check (status <> 'FOUND' or (kind is not null and url is not null and credit is not null))
);
create index story_images_retry_idx on story_images (status, next_try_at);
create index story_images_provider_idx on story_images (provider, provider_id, chosen_at);
