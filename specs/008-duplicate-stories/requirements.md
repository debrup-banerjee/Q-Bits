# 008 — One story per event: Requirements

## Summary
Different sources often report the same event. Spec 001 removes repeats of the same URL and of
the same title from the same source, but not the same news from different publishers. On
2026-10-05 one edition published three stories about OpenAI's EU text watermarks: from OpenAI's
own blog, The Verge and TechCrunch. Readers see the same news three times and the feed feels
padded. This feature publishes one story per event and holds back the others.

## In scope
- Finding stories about the same event across sources, within an edition and against stories
  already live in the 72-hour window
- Choosing which one to publish; holding back the rest as duplicates
- A one-off operator command to clean up duplicates already live

## Out of scope
- Realtime mode (`qbits.story-writer.mode: realtime`); production runs in digest mode
- Merging stories or rewriting one summary from several sources
- Showing other outlets' links on the kept story (see Open questions)
- Changing spec 001's same-URL and same-title checks
- "Also covered by" links to the held-back outlets (owner: a possible follow-up spec)

## Behaviours and acceptance criteria

### R1 — Spot same-event stories
As a reader, I want each piece of news once, so that the feed isn't padded with repeats.

- **R1.1** WHEN an edition is about to be published THE SYSTEM SHALL group its written stories,
  together with the stories already published in the 72-hour window, into sets that report the
  same event.
- **R1.2** "Same event" SHALL mean the same announcement, launch, deal, ruling, paper or incident.
  Stories that are only on the same topic or about the same company (two different OpenAI
  launches, a launch and later analysis of it) SHALL NOT be grouped.
- **R1.3** Grouping SHALL use only our own headline and summary, the section, the source name and
  the publish time. No publisher title, excerpt or article text is sent.
- **R1.4** IF the grouping answer names an unknown story, puts a story in two groups, or has a
  group of fewer than two THEN THE SYSTEM SHALL ignore that group and keep the others.

### R2 — Publish one per event
- **R2.1** For each group, THE SYSTEM SHALL keep one story and mark every other story in this
  edition `DUPLICATE`, recording the story it duplicates.
- **R2.2** IF a group contains a story that is already published THEN a published one SHALL be
  kept (chosen by R2.3 among the published ones), and no published story SHALL be withdrawn by an
  edition.
- **R2.3** Otherwise THE SYSTEM SHALL keep, in this order of preference: a story from an
  official source (R2.6); then the earliest publish time; then the lower source id.
- **R2.6** A source is official when its entry in `config/sources.yml` has `official: true`: the
  company's, lab's, university's or government's own channel. Entries without the field are not
  official.
- **R2.4** Duplicates SHALL NOT appear in any API response, page, sitemap or section count.
- **R2.5** The edition SHALL record how many stories it held back as duplicates, and the run SHALL
  log the edition id, group count and duplicate count.

### R3 — Failures never block the edition
- **R3.1** IF the grouping call fails or times out THEN THE SYSTEM SHALL try again on the next
  edition tick, and publish without de-duplication once the edition's publish deadline is near
  (within one tick), logging WARN.
- **R3.2** The grouping call SHALL count against the daily call cap. IF the cap is used up THEN
  the edition SHALL publish without de-duplication and log WARN.
- **R3.3** Running the publish step twice SHALL NOT change which stories are kept.

### R4 — Clean up what is already live
- **R4.1** An operator command `--dedupe-now` (admin profile) SHALL group all published stories in
  the current 72-hour window and mark every story but the kept one in each group `DUPLICATE`,
  including published ones, choosing the kept story by R2.3.
- **R4.2** The command SHALL print each group (kept id, held-back ids, headlines) and the totals.

## Non-functional
- One extra summary-service call per edition (about 50–150 stories of our own short text).
- Adds under a minute to publishing; no reader-facing request triggers it.

## Principles touched
- "The only publisher text sent to the summary service is the feed's title and excerpt." Grouping
  sends none, only our own headlines and summaries (R1.3).
- "Readers never trigger a call to the summary service." The call runs in the edition job.
- "An edition becomes visible all at once, only after its batch has finished." Grouping happens
  before that single publish transaction.
- "Every story shows its source … and a 'Read the full story' link." Unchanged for the kept story.
- Conventions: "Summary service calls: Only through the `StoryWriter` interface. No other code
  calls the provider SDK." **Conflict, see Open questions.**

## Decisions (2026-10-06, owner)
- **Convention change:** a second interface, `StoryGrouper`, beside `StoryWriter`; the
  convention becomes "only through `StoryWriter` and `StoryGrouper`".
- **Which copy to keep:** prefer an official company or lab source, then the earliest (R2.3,
  R2.6). Official in this change: `openai-news`, `google-deepmind-blog`, `google-ai-blog`,
  `microsoft-ai-blog`, `nvidia-blog`, `huggingface-blog`, `sarvam-ai-blog`,
  `google-research-blog`, `mit-news-ai`, `pib-meity`.
- **"Also covered by" links:** not in this spec; a possible follow-up.
- **Clean-up:** run `--dedupe-now` once after deploy to hide the extra watermark stories.

## Open questions
- None.
