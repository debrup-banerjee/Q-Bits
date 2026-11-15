-- Spec 009: coverage counts the DUPLICATE stories that point at a published story.
create index items_duplicate_of_idx on items (duplicate_of) where duplicate_of is not null;
