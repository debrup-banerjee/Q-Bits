-- The New Releases section (added 2026-10-04 in Section.java) was never given a matching
-- migration, so every story Claude classified as NEW_RELEASES failed this check constraint and
-- silently killed the digest job's whole transaction on every tick since then.

alter table stories drop constraint stories_section_check;
alter table stories add constraint stories_section_check
  check (section in
    ('GLOBAL_AI_TECH', 'NEW_RELEASES', 'WORLD_BUSINESS', 'INDIA_AI', 'INNOVATIONS_RESEARCH'));
