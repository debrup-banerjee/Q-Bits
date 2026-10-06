# 008 — One story per event: Design

## Overview
Just before `EditionPublisher` makes an edition visible, `EditionDeduplicator` sends one grouping
request to the summary service. The request lists the edition's `WRITTEN` stories and the
`PUBLISHED` stories in the 72-hour window, using our own headline and summary only (R1.1, R1.3).
The answer names groups of same-event stories. Pure rules in `DuplicateRules` validate the
groups (R1.4) and pick the story to keep (R2.2, R2.3). The rest of the edition's stories in each
group become `DUPLICATE`, with `duplicate_of` set, inside the same publish transaction, so the
catalog never sees them (R2.1, R2.4).

```
EditionJob ──ready to publish──▶ EditionDeduplicator
                                  ├─ candidates: WRITTEN in edition + PUBLISHED in 72 h window
                                  ├─ StoryGrouper.group(candidates)  ──▶ Claude (forced tool)
                                  ├─ DuplicateRules.resolve(groups)  → keep / hold back
                                  └─ EditionPublisher.publish(edition, duplicates) — one tx
```

## Components
| Component | Package | Responsibility | Requirements |
|---|---|---|---|
| `Source.official` | `sources.domain` | New optional `official` field in `config/sources.yml` (default false), parsed by `SourcesFileParser` | R2.6 |
| `GroupCandidate` | `stories.domain` | Record: id, headline, summary, section, source id, source name, official flag, publishedAt, published flag | R1.3, R2.6 |
| `DuplicateRules` | `stories.domain` | Pure: validate groups; choose the kept story; list held-back ids with their kept id | R1.4, R2.2, R2.3, R3.3 |
| `StoryGrouper` | `stories` | Interface: `List<Set<UUID>> group(List<GroupCandidate>)`; throws `WriterUnavailable` / `WriterRejected` | R1.1, R1.2 |
| `AnthropicStoryGrouper` | `stories.anthropic` | One Messages call with a forced `group_stories` tool; prompt `prompts/story-grouper.v1.md` | R1.2, R1.3 |
| `FakeStoryGrouper` | tests | Returns scripted groups | tests |
| `StoryRepository.editionCandidates` / `publishedCandidates` | `stories.persistence` | Our own headline, summary, section, source id, publish time and live flag for the edition's `WRITTEN` stories plus `PUBLISHED` ones in the window (not hidden) | R1.1, R1.3 |
| `EditionDeduplicator` | `stories.digest` | Loads candidates, takes one call from the daily budget, calls the grouper, applies the rules; decides retry vs publish-without; also `cleanUpPublished` for R4 | R1.1, R2.5, R3.1, R3.2, R4.1 |
| `EditionPublisher` (changed) | `stories.digest` | `publish(id, at, duplicates)` marks duplicates in the publish transaction before `publishEdition`; records `duplicates` via `EditionRepository.setDuplicates` | R2.1, R2.5 |
| `ItemStore` (changed) | `ingestion` | `markDuplicate(id, keptId)`; `DUPLICATE` excluded from the existing filters (they already select `PUBLISHED` only) | R2.1, R2.4 |
| `DedupeAdminCommands` | `stories.digest` | `--dedupe-now` under the `admin` profile; prints groups to the console and exits | R4 |

`stories` reaches items only through `ingestion.ItemStore`, as today (FeatureBoundariesTest).

## Data model
`V9__duplicate_stories.sql`:
```sql
alter table items drop constraint items_story_status_check;
alter table items add constraint items_story_status_check
  check (story_status in
    ('PENDING', 'WRITTEN', 'PUBLISHED', 'NOT_AI', 'REJECTED', 'EXPIRED', 'DUPLICATE'));
alter table items add column duplicate_of uuid references items (id) on delete set null;
alter table editions add column duplicates int not null default 0;
alter table editions add column dedupe_attempts int not null default 0;
```
`on delete set null`: the 7-day retention can delete the kept item before its duplicate.
`StoryStatus` gains `DUPLICATE`.

`config/sources.yml`: `official: true` on the first-party sources listed in requirements
"Decisions". The official flag is read from the source registry when candidates are built, so
changing it needs no migration.

## Contracts

### Grouping request
System prompt from `prompts/story-grouper.v1.md` (versioned, like the writer's). The user message
is a numbered list. Numbers stand in for ids, so the model never copies UUIDs:
```
[1] section=AI Wire source=OpenAI published=2026-10-05T15:00Z
    headline: OpenAI explains its plan for EU text watermarking rules
    summary: OpenAI has outlined how it plans to …
[2] …
```
Forced tool:
```json
{ "name": "group_stories",
  "input_schema": { "type": "object", "required": ["groups"],
    "properties": { "groups": { "type": "array",
      "items": { "type": "array", "items": { "type": "integer" }, "minItems": 2 } } } } }
```
The prompt defines "same event" as in R1.2, with examples of what is not one (same company,
different launch; a launch and a later reaction). It asks for groups only when sure. Model:
the configured `qbits.story-writer.model`; `max-output-tokens` 1000; timeout 60 s.

### Config
```yaml
qbits:
  dedupe:
    enabled: true
    prompt-version: v1
    timeout: PT60S
```
`enabled: false` publishes as today (an off switch if grouping misbehaves).

### Edition flow change
Today `EditionJob` calls `publisher.publish(e.id(), now)` at three points: normal finish, retry
finish, and deadline. Each becomes `deduplicator.publish(e, now)`:
1. If `qbits.dedupe.enabled` is false, or there is no `WRITTEN` story, publish as today.
2. If the daily budget has no call left → publish without, WARN `dedupe skipped reason=cap`.
3. Call the grouper. On `WriterUnavailable` or `WriterRejected`: if
   `now + tick < cutoff + publishDeadline`, increment `dedupe_attempts`, log WARN, and return
   without publishing (the next tick tries again). Otherwise publish without, WARN
   `dedupe skipped reason=failed`.
4. `DuplicateRules.resolve` → map heldBackId → keptId, limited to this edition's `WRITTEN` items.
5. `publisher.publish(e.id(), now, duplicates)` marks them `DUPLICATE` and publishes in one
   transaction. INFO `edition deduped edition= groups= duplicates=`.

R3.3: grouping and marking happen in the same tick as the publish. If the transaction rolls back,
nothing is marked, and the next tick groups again. `DuplicateRules` is deterministic given the
groups.

### `--dedupe-now`
Run on the instance with `--spring.main.web-application-type=none` (and `-Xmx256m`) so it doesn't
clash with the running backend on port 8080; see `deploy/aws/README.md`. Candidates are all `PUBLISHED` stories in the window. Rules run with "never withdraw published"
off, so each group keeps one story by R2.3 (official first, then earliest) and the rest become
`DUPLICATE` (R4.1).
It prints the groups (R4.2) and counts as one call.

## Error handling
- Bad model output (unknown numbers, overlaps, single-member groups) → those groups are dropped and
  counted in the log, never thrown (R1.4).
- Tool not called or JSON unreadable → treated as `WriterUnavailable` (retry path).
- Logs: ids, counts, group sizes and latency. Never headlines or summaries at INFO (conventions);
  the admin command prints headlines to the operator's console only.

## Testing approach
| Requirement | Test |
|---|---|
| R1.4, R2.2, R2.3, R3.3 | `DuplicateRulesTest`: unknown ids, overlaps, singletons dropped; published kept over an official unpublished one; official kept over an earlier press story; earliest kept among equals; tie by source id; same input → same output |
| R2.6 | `SourcesFileParserTest`: `official` parsed, defaults to false; `SourceRegistryTest` |
| R1.3 | `AnthropicStoryGrouperTest` (WireMock): request body has headline/summary/section/source/time only; forced tool; numbers mapped back to ids |
| R1.2 | `StoryGrouperLiveIT` (manual, `-Plive`): the 2026-10-05 watermark trio grouped; two different OpenAI launches not grouped |
| R1.1, R2.1, R2.4, R2.5 | `EditionDeduplicatorIT`: edition with a same-event pair and one already-published match → one kept, others `DUPLICATE` with `duplicate_of`; absent from `/api/v1/stories`, sections counts, sitemap; edition `duplicates` set |
| R3.1, R3.2 | `EditionDeduplicatorIT`: grouper fails → no publish, attempts +1; near deadline → publishes without; cap used → publishes without |
| R4 | `DedupeAdminIT` (command built directly, as `EditionAdminIT` does); `EditionDeduplicatorIT.cleanUpHoldsBackLiveRepeats` |
| (regression) | `EditionJobIT` builds the job with grouping switched off; `ItemRepositoryIT` column allowlist gains `duplicate_of` |

## Alternatives considered
- **Word overlap / shingles on headlines.** No cost, but the watermark trio shares few words
  ("watermark ChatGPT text EU" vs "Invisible Text Watermarks … Codex" vs "plan for EU text
  watermarking rules"). Tuning it to catch these merges different OpenAI news too.
- **Embeddings.** The Anthropic API has no embeddings endpoint; a second provider means another
  key, contract and review.
- **Grouping before writing, on feed titles and excerpts.** Saves writing the duplicates, but
  sends publisher text to a second purpose and groups noisier input. Writing a duplicate costs
  less than a cent in the batch.
- **Asking the writer to flag duplicates per story.** Each batch request sees one item, so it
  cannot compare.

## Risks
- **A false merge hides a distinct story.** Mitigated by a strict prompt ("only when sure"), the
  R1.2 examples, the live IT, and the `enabled` switch. Held-back stories stay in the database for
  7 days, so a mistake can be undone by setting the status back.
- **A long window list grows the prompt.** At about 150 stories × 120 words that's roughly 25k
  input tokens: fine for one call a day.
