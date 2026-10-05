# Product

## Name
**Q-Bits** — the website and the mobile app.

## What it is
Q-Bits shows the AI news that happened in the **last 72 hours, anywhere in the world**, explained in plain words. It gathers headlines from well-known, trusted news outlets and official company and research blogs, writes a short, story-style summary of each in its own words, sorts them into four sections, and links every story to the original article.

It opens on **AI Latest**: **today's AI digest**, every AI story from the past 24 hours, published once a day as a scrollable, newest-first feed like a social media timeline. When a story is about something open source, it also links straight to the code, model or paper.

The promise to the reader: *"In ten minutes, understand what happened in AI in the last three days, even if you're not a techie, and learn the words that matter along the way."*

## Who uses it
- **Curious non-technical readers** — managers, students, parents, professionals in any field — who hear about AI everywhere and want to understand it without wading through jargon.
- **Tech-savvy readers** — engineers, tech leads, founders — who want a fast, trustworthy scan of the last three days and will click through for depth.
- **First user:** the owner and his team. A public release is a later decision and will trigger a fresh legal review.

The writing must work for both groups at once: simple enough for the first, accurate and complete enough that the second never feels talked down to.

## Daily digest
Q-Bits publishes **one edition a day**. Feeds are still read every 30 minutes, so busy sources lose nothing, but the summaries for everything collected are written together in one batch at a fixed cut-off time and appear at the same moment. Batching halves the cost of writing summaries. All tabs change once a day, when a new edition is published.

## Tabs
The first tab is **AI Latest**, and the app opens on it. The four section tabs follow it.

| Tab | What it shows |
|---|---|
| **AI Latest** (first, default) | Today's edition of the daily digest: every story published in the 24 hours before the edition's cut-off, across all four sections, newest first, as a scrolling social-style feed. It is a view, not a fifth section: each story still belongs to exactly one section and shows that section's label. |
| Global AI Tech · World Business · India AI · Innovations & Research | One section each, last 72 hours. |

The section-by-section overview (each section's five newest stories) moves from the home page to `/sections`, linked from the bottom of the feed.

### What "social-style" means here
- Compact cards in one column, newest at the top, endless scroll.
- Each card leads with the source (a coloured letter badge, never a logo), the time ("12 min ago") and the section label, then the headline.
- The summary shows its first three lines; "Show more" expands it in place, with the words to know.
- The page says which edition it is ("Today's digest · published 6:42 am") and when the next one is due.
- No likes, comments, shares or images, and no tracking. This is the feel of a timeline, not a social network.

## Sections
Each story appears in exactly one section. **India first:** if a story has a real India angle it goes to India AI, whatever its topic. A passing mention (India as one of twenty launch countries) does not count.

| Section | What goes here | Examples |
|---|---|---|
| **Global AI Tech** | Core AI technology moves: new models and products, versions, capabilities and published technical specs. | A new Gemini or GPT model and what it can do; a new open-source model; a big context-window or speed jump. |
| **World Business** | Money, companies and industry moves with an AI impact, outside India. | A semiconductor plant opening; a funding round or acquisition; layoffs or hiring tied to AI; chip export rules; big partnership deals. |
| **India AI** | Everything AI-related with a real India angle: Indian companies and startups, global companies' launches, investments or plants in India, Indian government and policy, Indian research. India comes first: an India story goes here even if it is about business or research. |  IndiaAI Mission updates; an Indian language model launch; a data-centre investment in India; Indian AI policy. |
| **Innovations & Research** | Interesting research and upcoming innovations, mostly before they become products. | A new paper or lab result; robotics or medical AI breakthroughs; university and research-lab announcements. |

## What a story looks like
- A clear, plain-language headline in our own words.
- A short story-style summary (about 80–120 words) that says what happened, why it matters, and who it affects.
- **Key terms**: important AI or tech words that appear in the story, each with a one-line, everyday explanation (for example *"Context window — how much text the AI can read and keep in mind at once"*).
- Source name, publish time, and a prominent **"Read the full story at <Source>"** link.
- A small note that the summary was written from the publisher's headline and teaser.
- **Open-source links**, when the story is about something with public code, model weights, a dataset or a paper: up to three links such as "Code on GitHub", "Model on Hugging Face" or "Paper on arXiv". These come only from links the publisher put in its feed, and each one is checked to exist before it is shown. Q-Bits never guesses a link.

## Core jobs
1. "Show me what's happening in AI right now": the last 24 hours, as a feed I can scroll in a minute.
2. "Tell me what happened in AI in the last three days, in words I understand."
3. "Let me look at just one area": Global AI Tech, World Business, India AI, or Innovations & Research.
4. "Teach me the important terms so I can follow AI conversations."
5. "Get me to the original article in one tap when I want more."
6. "If it's open source, take me to the code or the model."

## Platforms
- **Now:** responsive web app that works well on phone, tablet and desktop.
- **Later:** mobile app (iOS and Android) using the same backend API.

## What it is not
- **Not a publisher or a copy of anyone's article.** Summaries are short, written in our own words from the publisher's headline and feed teaser only, and always link out.
- **Not a full-article summariser.** The app never fetches or reads article pages.
- **Not a reader view.** No iframes, in-app web views, extracted article text or "read here" mode.
- **Not a scraper.** Only feeds and official APIs from an approved list. No page scraping, no paywall or login workarounds.
- **Not an archive.** AI Latest shows today's edition (24 hours); the section tabs show the last three editions (72 hours).
- **Not live.** Q-Bits is a daily digest. Feeds are read through the day, but stories are written and published together once a day, which halves the summary cost.
- **Not a general news app.** Non-AI stories are filtered out.
- **Not opinion.** Summaries explain; they do not take sides, predict markets or give investment advice.
- **Not a social network.** AI Latest looks like a timeline, but there are no comments, likes, shares, followers or user-submitted links.
- **Not an image gallery.** No hosted images or publisher logos.
- **Not collecting personal data in v1.** No accounts, no tracking beyond basic server logs.

## Success looks like
- A non-technical reader can explain any story back in one sentence after reading its summary.
- A technical reader finds no factual errors and no missing key terms on spot checks.
- Under 5% of shown stories are "not really AI" or in the wrong section on spot checks.
- People come back: most readers return within three days.
- No publisher has reason to complain: every story is credited and linked, summaries are clearly ours and short, and takedown requests are honoured within 24 hours.
