# 004 — AI Latest feed: Requirements

## Summary
Add **AI Latest** as the first tab and the page the app opens on: every published AI story from the last 24 hours, across all four sections, newest first, shown as a scrolling social-style feed. It is a view over existing stories, not a new section. The four-section overview that was the home page (003 R4) moves to `/sections`.

## In scope
- A 24-hour window on the stories API
- The AI Latest page, its feed card, and the "new stories" button
- Moving the section overview from `/` to `/sections`, and the new tab order

## Out of scope
- Likes, comments, shares, follows, notifications, images (product: not a social network)
- Personalised ranking; the feed is newest first for everyone
- Open-source links on cards (spec 005 adds them to this card and the story card)

## Behaviours and acceptance criteria

### R1 — 24-hour window in the API
- **R1.1** `GET /api/v1/stories` SHALL accept `hours` from 1 to 72, default 72, and return only stories published within that many hours, newest first.
- **R1.2** IF `hours` is outside 1–72 or not a whole number THEN the API SHALL return 400 Problem Details with code `INVALID_HOURS`.
- **R1.3** `hours` SHALL combine with `section`, `cursor` and `limit`. A cursor stays valid only with the same `hours` and `section`.
- **R1.4** `GET /api/v1/sections` SHALL keep returning 72-hour counts. AI Latest is not listed there.

### R2 — Tabs and routes
- **R2.1** The navigation SHALL show, in order: **AI Latest**, Global AI Tech, World Business, India AI, Innovations & Research. "AI Latest" is a fixed label in the client; section names still come from the API.
- **R2.2** `/` SHALL show AI Latest, and its tab SHALL be marked current there.
- **R2.3** The section overview (003 R4) SHALL move to `/sections`, linked as "Browse by section" at the end of the feed and from the footer.
- **R2.4** Existing routes `/section/:slug`, `/story/:id` and `/about` SHALL keep working unchanged.

### R3 — The feed
As a reader, I want to scroll today's AI news like a timeline, so that I can catch up in a minute.

- **R3.1** The page SHALL show the heading "AI Latest", the line "Every AI story from the last 24 hours, newest first", and "Updated <relative time>" from `X-Data-As-Of`.
- **R3.2** The page SHALL list stories from `hours=24` in a single column, newest first, loading 20 at a time and more as the reader nears the end (with a "Load more" button as fallback).
- **R3.3** WHEN there are no stories in the last 24 hours THE page SHALL say "No AI news in the last 24 hours yet. Check back soon." and link to "Browse by section".
- **R3.4** Loading SHALL show skeleton feed cards; errors SHALL show the 003 R8.2 error with a retry.

### R4 — Feed card
- **R4.1** Each card SHALL show, in this order: a source badge (a circle with the source's first letter and a colour derived from the source name, never a logo or image), the source name, the relative time, the section label (linking to the section), then the headline (linking to `/story/:id`).
- **R4.2** The summary SHALL show its first three lines, with a "Show more" button that expands it in place and also reveals Words to know. "Show less" collapses it. The button SHALL expose `aria-expanded`.
- **R4.3** Each card SHALL show the "Read the full story at <Source>" link (003 R6.3) and the attribution line (003 R6.4).
- **R4.4** Cards SHALL contain no images, embeds, like/share/comment controls or counters.
- **R4.5** The card's text SHALL stay readable at 360px: headline at least 17px, summary at least 16px, no horizontal scroll.

### R5 — New stories while reading
- **R5.1** While the page is open and visible, THE SYSTEM SHALL check for newer stories every 2 minutes without changing what is on screen.
- **R5.2** WHEN newer stories exist THE page SHALL show a sticky button "N new stories" (or "1 new story") at the top of the feed.
- **R5.3** WHEN the reader activates it THE page SHALL add the new stories at the top and scroll to the top of the feed.
- **R5.4** Checks SHALL pause while the browser tab is hidden and run once when it becomes visible again.
- **R5.5** The button SHALL be announced to screen readers through a polite live region.

## Non-functional
- AI Latest is interactive in under 2.5 s on a mid-range phone over 4G (same bar as 003).
- Background checks request one page of at most 20 stories; they never fetch more than that.
- Only one new request every 2 minutes per open tab.

## Principles touched
Content and legal (24-hour window, link out, attribution, no images), Architecture (window lives in the backend; clients only display).

## Open questions
- Should the four-section overview stay at all, now that AI Latest covers everything? Current plan: keep it at `/sections` and link to it.
- Would you like a one-line "quick take" field (under 25 words) written with each story for even faster scanning? It would need a new summary prompt version and a rewrite of current stories. Not in this spec.
