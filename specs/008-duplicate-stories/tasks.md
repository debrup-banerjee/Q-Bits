# 008 — One story per event: Tasks

Each task is 2–4 hours, leaves the full check green, and can be tested on its own.

## T1 — Schema and rules
- **Covers:** R1.4, R2.2, R2.3, R2.6, R3.3
- **Depends on:** none
- **Do:** `V9__duplicate_stories.sql`; `StoryStatus.DUPLICATE`; `official` field on `Source` and
  in `config/sources.yml`; `GroupCandidate`; pure `DuplicateRules`; `qbits.dedupe.*` config
  record.
- **Tests:** `DuplicateRulesTest`, `SourcesFileParserTest` additions; migration applies on top of
  V8 (`ApplicationStartsIT`).
- **Done when:** `mvn -f backend/pom.xml verify` passes.

## T2 — Grouper
- **Covers:** R1.2, R1.3
- **Depends on:** T1
- **Do:** `StoryGrouper` interface; `AnthropicStoryGrouper` with the forced `group_stories` tool
  and `prompts/story-grouper.v1.md`; `FakeStoryGrouper` wired in `TestBeans`.
- **Tests:** `AnthropicStoryGrouperTest` (WireMock); `StoryGrouperLiveIT` (manual, `-Plive`).
- **Done when:** tests pass with no real key.

## T3 — Edition de-duplication
- **Covers:** R1.1, R2.1, R2.4, R2.5, R3.1, R3.2
- **Depends on:** T1, T2
- **Do:** `EditionDeduplicator`; `EditionPublisher.publish(…, duplicates)`;
  `ItemStore.markDuplicate`; replace the three `publisher.publish` calls in `EditionJob`.
- **Tests:** `EditionDeduplicatorIT`; existing `EditionJobIT` still passes.
- **Done when:** a scripted edition publishes one story per event.

## T4 — Operator clean-up
- **Covers:** R4.1, R4.2
- **Depends on:** T3
- **Do:** `--dedupe-now` under the `admin` profile; README operator note. After deploy, the
  operator runs it once to hide the extra 2026-10-05 watermark stories.
- **Tests:** `DedupeAdminIT`.
- **Done when:** the command hides the later copies of a live duplicate group.

## T5 — Conventions
- **Covers:** Open question "Convention change"
- **Depends on:** approval
- **Do:** Update `steering/conventions.md` "Summary service calls" for `StoryGrouper` (or as
  decided).
- **Tests:** none (docs).
- **Done when:** steering matches what was built.
