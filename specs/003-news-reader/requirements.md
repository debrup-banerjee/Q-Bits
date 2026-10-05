# 003 — News reader (API + responsive web): Requirements

## Summary
The website and, later, the mobile app are called **Q-Bits**.

Show published stories from the last 72 hours in four sections, on a responsive web app that works on phone, tablet and desktop, through a versioned API the mobile app will reuse later. Each story card has a plain headline, the story summary, words to know, the source and a clear link to the original article.

## In scope
- Read-only REST API: sections, stories list, single story, sources
- React web app: home, section pages, story view, about page
- 72-hour window, empty, loading and error states, accessibility

## Out of scope
- Mobile app (later spec, same API)
- Search, accounts, bookmarks, notifications
- Analytics or tracking

## Behaviours and acceptance criteria

### R1 — Sections API
- **R1.1** `GET /api/v1/sections` SHALL return the four sections in this order: Global AI Tech, World Business, India AI, Innovations & Research, each with `slug`, `name`, `description` and `storyCount` for the last 72 hours.

### R2 — Stories API
- **R2.1** `GET /api/v1/stories?section=<slug>&cursor=&limit=` SHALL return only stories with status `PUBLISHED`, not hidden, and `publishedAt` within the last 72 hours, newest first.
- **R2.2** Without `section`, it SHALL return stories from all sections.
- **R2.3** Each story SHALL include: `id`, `section` (slug and name), `headline`, `summary`, `keyTerms`, `source` (`name`, `homepage`), `originalUrl`, `publishedAt`, `dateEstimated`, `attribution` ("Summary written from <Source>'s headline and teaser").
- **R2.4** Responses SHALL NOT include the source title or source excerpt.
- **R2.5** An unknown section slug SHALL return 400 Problem Details with code `UNKNOWN_SECTION`.
- **R2.6** `GET /api/v1/stories/{id}` SHALL return one story, or 404 `STORY_NOT_FOUND` if missing, hidden or older than 72 hours.
- **R2.7** Responses SHALL carry `Cache-Control: public, max-age=60` and an `X-Data-As-Of` timestamp of the latest published story.

### R3 — Sources API
- **R3.1** `GET /api/v1/sources` SHALL list enabled sources with `name` and `homepage` only.

### R4 — Home page
- **R4.1** The home page SHALL show the four sections, each with its 5 newest stories and a "See all <Section>" link.
- **R4.2** The page SHALL show "AI news from the last 72 hours" and "Updated <relative time>" from `X-Data-As-Of`.
- **R4.3** Section navigation SHALL be a sticky tab bar on phones and a top navigation bar on wider screens.

### R5 — Section page
- **R5.1** `/section/<slug>` SHALL list all stories of that section from the last 72 hours, newest first, loading more as the reader scrolls (cursor pagination).
- **R5.2** WHEN a section has no stories THE page SHALL say "No <Section> news in the last 72 hours. Check back soon." 

### R6 — Story card
- **R6.1** A card SHALL show the headline, section label, source name, relative time ("5 hours ago"; "about 5 hours ago" if `dateEstimated`) and the summary.
- **R6.2** A card SHALL show **Words to know** with each key term and its meaning, collapsed to the terms' names on phones and expandable with one tap.
- **R6.3** A card SHALL show a prominent "Read the full story at <Source>" link to `originalUrl` that opens in a new tab with `rel="noopener noreferrer"`.
- **R6.4** A card SHALL show the attribution line in small text.
- **R6.5** Cards SHALL show no images, logos or embedded content.
- **R6.6** `/story/<id>` SHALL show a single card and be shareable.

### R7 — About page
- **R7.1** `/about` SHALL explain in plain words how stories are made (picked from trusted outlets, written in our own words from the headline and teaser, always linking to the original).
- **R7.2** It SHALL list the enabled sources with links to their homepages.
- **R7.3** It SHALL give the contact address `debrup28.nitdgp@gmail.com` (from config `qbits.contact-email`) for publishers to ask for corrections or removal.

### R8 — States and errors
- **R8.1** While loading, pages SHALL show skeleton cards, not spinners over blank pages.
- **R8.2** IF the API fails THEN the page SHALL show "We couldn't load the news. Try again." with a retry button; no technical details.

### R9 — Responsive and accessible
- **R9.1** Every page SHALL work without horizontal scrolling from 360px to 1440px wide; one column on phones, two on tablets, up to three on desktops for the home section grids.
- **R9.2** Text SHALL be at least 16px for summaries, line length at most ~75 characters.
- **R9.3** All interactive elements SHALL be keyboard reachable with visible focus; colour contrast SHALL meet WCAG AA; the page SHALL support light and dark mode from system settings.

## Non-functional
- Home page interactive in under 2.5 s on a mid-range phone over 4G.
- No cookies, analytics or third-party scripts.
- The web app talks to the backend only through `packages/api-client`.

## Principles touched
Content and legal (link out, attribution, no images, 72 hours), Architecture (clients hold no rules; versioned API), no personal data.

## Open questions
- Should the home page also show a "Words of the day" strip gathering key terms from the last 72 hours?

## Approved changes (2026-10-04)
Approved by Deb in session after the spec reviews. Where this section and the text above differ, this section wins.

- **R6.1** (wording) ("5 hours ago"; "about 5 hours ago", or "yesterday (estimated)", if `dateEstimated`).
