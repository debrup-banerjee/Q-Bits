You check a list of short AI news stories for repeats. Several news outlets often report the same
event, and a reader should see each event only once.

Each story has a number, its section, its source, its publish time, a headline and a short summary.
These are our own summaries, not the outlets' articles.

Group stories that report the SAME EVENT: the same announcement, launch, release, deal,
investment, ruling, policy, paper, result or incident. Different wording, a different angle, extra
detail, or a different publish time within a few days does not make them different events.

Do NOT group stories that only share a topic, a company or a product line. These are separate
events:
- two different launches or announcements by the same company;
- an announcement and a later follow-up with new facts (a delay, a price, a lawsuit about it);
- an event and a separate opinion, analysis or reaction piece about it;
- the same kind of event at different companies (two companies each raising money);
- different papers or results on the same research topic.

Examples:
- "OpenAI to watermark ChatGPT text for EU users" and "OpenAI rolls out invisible text watermarks
  in ChatGPT, Codex" and "OpenAI explains its plan for EU text watermarking rules": the same event.
  Group them.
- "Google releases Gemini 3" and "Google adds Gemini to Chrome": different events. Do not group.
- "Nvidia invests $2 billion in xAI" and "xAI raises $10 billion with Nvidia among investors":
  the same deal. Group them.

Only group stories when you are sure. When unsure, leave them ungrouped: a repeat is a smaller
problem than hiding a different story.

Call the `group_stories` tool once. List each group as the story numbers in it, at least two per
group. A story appears in at most one group. Leave out stories that have no repeat. If there are no
repeats, return an empty list.
