# 006 — Daily digest: Design

## Overview
Ingestion is unchanged. A new `EditionJob` runs on a schedule. At the cut-off it creates an `editions` row, picks the edition's items, and submits a Message Batch through a new `BatchStoryWriter`. While the batch runs, the job polls it every 10 minutes. When it ends, results go through the existing `StoryValidator`. Valid stories are saved but held back; invalid ones form a retry batch with feedback. When every round has ended, `EditionPublisher` flips all valid items to `PUBLISHED` in one transaction and stamps the edition. The catalog anchors its 24- and 72-hour windows to the latest published edition's cut-off.

```
 every 30 min                    06:00 IST (cut-off)                  every 10 min            all rounds ended
 ───────────────                 ──────────────────                   ────────────            ────────────────
 IngestionJob ─▶ items PENDING ─▶ EditionJob: create edition ─▶ submit batch ─▶ poll ─▶ results ─▶ validate
                                                                                             │ invalid
                                                                                             ▼
                                                                                   retry batch (feedback)
                                                                                             │
                                                                                             ▼
                                                                  EditionPublisher: PUBLISH all at once
```

## Components
| Component | Package | Responsibility | Requirements |
|---|---|---|---|
| `EditionRepository` | `stories.persistence` | Editions table: create, find latest published, find open, update status/counts. | R1.4 |
| `EditionSchedule` | `stories.domain` | Pure: next cut-off from config time and zone; window start for an edition. | R1.1, R4 |
| `BatchStoryWriter` | `stories` | Interface: `submit(List<BatchRequest>) → batchId`, `status(batchId)`, `results(batchId) → List<BatchResult>`. | R2 |
| `AnthropicBatchStoryWriter` | `stories.anthropic` | SDK `messages().batches()` create, retrieve, results; builds each request with the same params as `AnthropicStoryWriter` (shared builder). | R2.1, R2.6 |
| `FakeBatchStoryWriter` | test | Scripted batches for tests. | — |
| `EditionJob` | `stories` | Scheduled (every 5 min, ShedLock): cut a new edition when due; submit; poll; process results; retry round; publish; deadline handling. | R1–R3, R6.1, R6.2 |
| `EditionPublisher` | `stories` | One transaction: items → `PUBLISHED`, edition → `PUBLISHED` with counts. | R3.1 |
| `StoryService` (002) | `stories` | Split: `applyDraft(item, input, draft, attempt)` reused by both realtime and batch paths. | R2.4 |
| `CatalogService` / `StoryQueryRepository` (003/004) | `catalog` | Window start = latest cut-off − hours (fallback now); only stories with status `PUBLISHED`. | R4 |
| `EditionController` | `api` | `GET /api/v1/edition`; `X-Data-As-Of` from edition. | R5.1, R5.2 |
| `StoryJob` (002) | `stories` | Runs only when `mode=realtime`. | R6.4 |
| `EditionHealthIndicator` | `stories` | Actuator detail. | R6.5 |
| Web `LatestPage` | `web/features/latest` | Edition line, next edition time, late notice; remove `NewStoriesButton` and `useNewStories`. | R5.3–R5.5 |

## Data model (migration `V5__editions.sql`)
```sql
create table editions (
  id              uuid primary key,
  cutoff_at       timestamptz not null unique,
  status          text not null check (status in
                    ('COLLECTING','SUBMITTED','RETRYING','PUBLISHED','FAILED')),
  batch_id        text,
  retry_batch_id  text,
  submitted       int not null default 0,
  published       int not null default 0,
  not_ai          int not null default 0,
  rejected        int not null default 0,
  created_at      timestamptz not null,
  published_at    timestamptz
);
alter table items add column edition_id uuid references editions(id);
create index items_edition_idx on items (edition_id);
-- Items written but not yet live wait in a new status:
alter table items drop constraint items_story_status_check;
alter table items add constraint items_story_status_check
  check (story_status in ('PENDING','WRITTEN','PUBLISHED','NOT_AI','REJECTED','EXPIRED'));
```
`WRITTEN` = story saved and valid, waiting for its edition to publish (R3.2).

## Contracts
**Config**
```yaml
qbits:
  story-writer:
    mode: digest                 # digest | realtime
  digest:
    cutoff-time: "06:00"
    zone: Asia/Kolkata
    poll-every: PT10M
    submit-retry-every: PT30M
    publish-deadline: PT6H
    late-notice-after: PT2H
```

**API**
```
GET /api/v1/edition
200 { "id": "…", "cutoffAt": "2026-10-04T00:30:00Z", "publishedAt": "2026-10-04T01:12:00Z",
      "storyCount": 214, "nextCutoffAt": "2026-10-05T00:30:00Z" }
404 { "code": "NO_EDITION" }
```

**Batch request** (per item, same as a single call): `custom_id` = item UUID; `params` = model, max tokens, system prompt v1, user message (allowed fields as JSON), tool `write_story`, `tool_choice` forced.

## Flow (EditionJob tick, every 5 minutes)
1. If no open edition and `now ≥ next cut-off`: create edition (`COLLECTING`), assign items (R1.2) newest first up to the cap remaining today, submit batch → `SUBMITTED`. On submit failure keep `COLLECTING` and retry after 30 min (R6.1).
2. If `SUBMITTED` and last poll ≥ 10 min ago: retrieve status. When ended, process results: valid → save story, item `WRITTEN`; not AI → `NOT_AI`; invalid → retry list; errored/expired → back to `PENDING`, `edition_id` cleared (R6.2).
3. If the retry list is non-empty: submit retry batch with feedback → `RETRYING`; process its results the same way, with invalid → `REJECTED`.
4. When all rounds have ended: `EditionPublisher` publishes (R3.1).
5. If `now > cut-off + 6 h` and not published: publish what is `WRITTEN`, put the rest back to `PENDING` (R3.3).

## Web changes
- `LatestPage`: fetch `/api/v1/edition` (new client hook `useEdition`), show "Today's digest · published 6:42 am · Next edition around 6:00 am tomorrow" (local time formatting, `en-IN`), late notice per R5.5.
- Remove `NewStoriesButton`, `useNewStories`, `countNewer` and their tests (004 R5 retired).
- TanStack Query: `refetchOnWindowFocus` stays on, so reopening the tab after a new edition shows it.

## Testing approach
- `EditionScheduleTest`: next cut-off across midnight and time zones; window starts.
- `EditionJobIT` with `FakeBatchStoryWriter` and a fixed clock: cut at 06:00 IST, items before/after cut-off, cap leaves items for next edition, polling interval, valid/not-AI/invalid/errored results, retry round with feedback, publish all at once (nothing visible before), 6-hour deadline, submit failure retries.
- `AnthropicBatchStoryWriterTest` with WireMock: create request body (custom ids, forced tool, allowed fields only), status polling, results parsing (succeeded, errored, expired).
- `CatalogControllerIT`: windows anchored to edition cut-off; fallback before first edition; `/api/v1/edition` and `X-Data-As-Of`.
- Web: `LatestPage.test.tsx` edition line, "Yesterday's digest", late notice, no new-stories button; Playwright update.

## Cost
Same tokens per story as before at half price: about **$5–15 a month on Haiku 4.5** or **$10–30 on Sonnet 5.5** for 100–300 stories a day; at most about $24 / $48 at the 500-a-day cap (prices from the official pricing page, October 2026; estimates, to be confirmed by the 002 T7 sample run).

## Alternatives considered
- Fetch feeds only once a day: same cost, misses stories that scroll off busy feeds.
- Publish stories as each batch result arrives: readers would see a half-built edition; one transaction keeps editions whole.
- Separate 36-hour refresh for section tabs: no saving, staler tabs (open question in requirements).

## Risks
- Batch results can take up to 24 hours in the worst case; the 6-hour deadline and late notice keep the site usable.
- A day with a problem shows yesterday's edition; the late notice says so plainly.
