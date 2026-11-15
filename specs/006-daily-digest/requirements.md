# 006 — Daily digest: Requirements

## Summary
Turn Q-Bits into a **daily digest** to halve the cost of writing summaries. Feeds keep being read every 30 minutes so nothing is missed, but instead of writing each summary as a story arrives, the backend writes all of them **once a day in a single batch** using the Anthropic Message Batches API (50% cheaper). When the batch is done, the whole edition goes live at once. AI Latest becomes "today's digest"; the section tabs show the last three editions.

## Changes to earlier specs
| Spec | What changes |
|---|---|
| 002 R1.1, R9.2–R9.4 | Stories are no longer processed continuously. They are written in a daily batch. Validation (R4–R8), the daily cap (R9.1) and expiry (R9.3) still apply. The one-at-a-time writer stays for operator rewrites (R10.2) and the live sample check (T7). |
| 003 R2.1, R2.7 | Windows are measured from the latest edition's cut-off, not from "now". `X-Data-As-Of` becomes the edition's publish time. |
| 004 R1.1, R3.1 | `hours=24` means "the 24 hours before the latest cut-off". The header shows the edition, not a rolling "updated" time. |
| 004 R5 | The 2-minute "N new stories" check and button are removed: nothing changes until the next edition. |

## In scope
- Editions: a daily cut-off, a batch per edition, publishing an edition all at once
- Batch submission, polling, results, validation and one retry round
- Windows anchored to the latest edition
- API and UI changes to show the edition and when the next one is due
- Operator commands and failure handling

## Out of scope
- Changing how often feeds are read (stays every 30 minutes; see product)
- Changing the summary rules, prompt or validator
- A live mode switch in the UI (a config switch back to one-at-a-time writing is kept for operators)

## Behaviours and acceptance criteria

### R1 — Editions
- **R1.1** THE SYSTEM SHALL create one edition a day at a configured cut-off time (default **06:00 IST**, `Asia/Kolkata`).
- **R1.2** An edition SHALL include every `PENDING` item published in the 24 hours before its cut-off, plus any `PENDING` items left over from a previous edition that are still within 72 hours.
- **R1.3** Items ingested after the cut-off SHALL wait for the next edition.
- **R1.4** Each edition SHALL record: id, cut-off time, status (`COLLECTING`, `SUBMITTED`, `RETRYING`, `PUBLISHED`, `FAILED`), batch ids, counts (submitted, published, not AI, rejected), and published time.

### R2 — Batch writing
- **R2.1** At the cut-off THE SYSTEM SHALL submit one Message Batch containing one request per item (custom id = item id), using exactly the same prompt, tool and model settings as single calls (002 design).
- **R2.2** The batch SHALL respect the daily call cap (002 R9.1). Items over the cap SHALL go into the next edition, newest first being included first.
- **R2.3** THE SYSTEM SHALL check the batch's status every 10 minutes until it ends.
- **R2.4** WHEN the batch ends THE SYSTEM SHALL validate each result with the existing validator: valid → story saved (not yet visible); not AI → `NOT_AI`; invalid → collected for one retry round; request error → `REJECTED` with the error.
- **R2.5** THE SYSTEM SHALL submit one retry batch for the invalid items, adding the failed rules as feedback (002 R8.2). After it ends, still-invalid items become `REJECTED`.
- **R2.6** Batch requests SHALL send only the allowed fields (002 R1.2).

### R3 — Publishing an edition
- **R3.1** WHEN all rounds of an edition have ended THE SYSTEM SHALL publish it in one database transaction: all its valid stories become `PUBLISHED` together and the edition gets its published time.
- **R3.2** Stories of an edition SHALL NOT be visible before the edition is published.
- **R3.3** IF an edition is not published within 6 hours of its cut-off THEN THE SYSTEM SHALL publish whatever is valid so far, mark the remaining items `PENDING` for the next edition, and log a warning.

### R4 — Windows anchored to the latest edition
- **R4.1** "Latest cut-off" SHALL mean the cut-off of the most recently published edition.
- **R4.2** AI Latest (`hours=24`) SHALL show stories published in the 24 hours before the latest cut-off; section tabs and the default (`hours=72`) the 72 hours before it.
- **R4.3** Before any edition has ever been published, the windows SHALL fall back to "now" (so a new install still shows something once stories exist).

### R5 — Showing the edition
- **R5.1** `GET /api/v1/edition` SHALL return the latest published edition: `{ id, cutoffAt, publishedAt, storyCount, nextCutoffAt }`, or 404 `NO_EDITION` before the first one.
- **R5.2** `X-Data-As-Of` SHALL carry the latest edition's `publishedAt`.
- **R5.3** AI Latest SHALL show the heading "AI Latest", the line "Today's digest · published <time>" (or "Yesterday's digest" when the latest edition is from an earlier day), and "Next edition around <time>".
- **R5.4** The 004 "N new stories" check and button SHALL be removed. The web app SHALL refresh data when the tab is reopened after the next cut-off.
- **R5.5** IF the current day's edition is more than 2 hours late THEN AI Latest SHALL show "Today's edition is running late. Here is the last one." above the previous edition.

### R6 — Failures and operations
- **R6.1** IF submitting a batch fails (service down, rate limit) THEN THE SYSTEM SHALL retry every 30 minutes, up to the R3.3 deadline.
- **R6.2** IF a batch expires or is cancelled THEN its unfinished items SHALL go back to `PENDING` for the next edition.
- **R6.3** THE SYSTEM SHALL provide an operator command `--run-edition-now` to cut and submit an edition immediately (for testing and recovery).
- **R6.4** A config switch `qbits.story-writer.mode` (`digest` | `realtime`, default `digest`) SHALL let an operator return to one-at-a-time writing (002 behaviour) without code changes.
- **R6.5** Actuator health SHALL show the latest edition's status, cut-off and published time.

## Non-functional
- Cost per story halves compared with one-at-a-time calls at the same model.
- An edition with up to 500 items is normally published within 1 hour of its cut-off.
- No test calls the real Batches API.

## Principles touched
Architecture (summaries written once a day in a batch; an edition becomes visible all at once; windows anchored to the edition), Content and legal (unchanged inputs and checks).

## Decisions (owner, 2026-10-04)
- Cut-off time: **06:00 IST**, every day.
- Section tabs change with the **daily** edition (no separate 36-hour refresh).
- Editions are published **every day**, weekends included.

## Approved changes (2026-10-04)
Approved by Deb in session after the spec reviews. Where this section and the text above differ, this section wins.

- **R1.1** (addition) If the first run of a day is more than 6 hours after the cut-off, that day's edition is cut at that time instead of 06:00 IST.
- **R6.4** (addition) In `realtime` mode the R4 windows end at now, `X-Data-As-Of` is the newest published story's time, and `GET /api/v1/edition` reports `late: false`.
- **R6.5** THE SYSTEM SHALL show the latest edition's status, cut-off and published time in Actuator health; it stays UP and adds a `warning` when the latest edition is `FAILED` or today's edition is late.
