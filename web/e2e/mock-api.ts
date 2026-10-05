import type { Page } from '@playwright/test';

const SECTIONS = [
  {
    slug: 'global-ai-tech',
    name: 'AI Wire',
    description: 'Ongoing AI tech coverage: capabilities, updates and what is changing.',
    storyCount: 2,
  },
  {
    slug: 'new-releases',
    name: 'New Releases',
    description: 'Brand-new model launches and major AI-relevant hardware.',
    storyCount: 1,
  },
  {
    slug: 'world-business',
    name: 'AI in Business',
    description: 'Money, companies, chips and jobs around the world.',
    storyCount: 1,
  },
  { slug: 'india-ai', name: 'India AI', description: 'Everything AI in India.', storyCount: 1 },
  {
    slug: 'innovations-research',
    name: 'AI Innovations',
    description: 'New ideas from labs.',
    storyCount: 0,
  },
];

function story(id: string, slug: string, name: string, headline: string, hoursAgo = 3) {
  return {
    id,
    section: { slug, name },
    headline,
    summary:
      'According to The Hindu, start-ups in India can now rent powerful computer chips called graphics processing units (GPUs) at low prices. That matters because training an AI system needs a lot of computing power, which has been costly for small teams. Students and researchers can apply too. Watch for the first projects built on it in the coming months.',
    keyTerms: [
      { term: 'GPU', meaning: 'A chip that does many small sums at once, ideal for AI.' },
      { term: 'Training', meaning: 'Teaching an AI system by showing it lots of examples.' },
    ],
    source: { name: 'The Hindu', homepage: 'https://www.thehindu.com/' },
    originalUrl: `https://www.thehindu.com/sci-tech/${id}`,
    publishedAt: new Date(Date.now() - hoursAgo * 3600_000).toISOString(),
    dateEstimated: false,
    attribution: "Summary written from The Hindu's headline and teaser",
    resources: [] as { type: string; label: string; url: string; name: string }[],
  };
}

function withLinks<T extends { resources: unknown[] }>(s: T): T {
  return {
    ...s,
    resources: [
      {
        type: 'code',
        label: 'Code on GitHub',
        url: 'https://github.com/example-lab/bookshelf',
        name: 'example-lab/bookshelf',
      },
      {
        type: 'model',
        label: 'Model on Hugging Face',
        url: 'https://huggingface.co/example-lab/bookshelf-8b',
        name: 'example-lab/bookshelf-8b',
      },
      {
        type: 'paper',
        label: 'Paper on arXiv',
        url: 'https://arxiv.org/abs/2410.01234',
        name: '2410.01234',
      },
    ],
  };
}

export const LINKED_STORY_ID = '0192f0c4-0000-7000-8000-000000000011';

const STORIES: Record<string, ReturnType<typeof story>[]> = {
  'global-ai-tech': [
    withLinks(
      story(
        '0192f0c4-0000-7000-8000-000000000011',
        'global-ai-tech',
        'AI Wire',
        'A new AI model can read a whole bookshelf at once',
      ),
    ),
    story(
      '0192f0c4-0000-7000-8000-000000000012',
      'global-ai-tech',
      'AI Wire',
      'Phones get an AI helper that works without the internet',
    ),
  ],
  'new-releases': [
    story(
      '0192f0c4-0000-7000-8000-000000000041',
      'new-releases',
      'New Releases',
      'A lab launches its next-generation AI model',
    ),
  ],
  'world-business': [
    story(
      '0192f0c4-0000-7000-8000-000000000021',
      'world-business',
      'AI in Business',
      'Chipmaker plans a $20 billion factory for AI chips',
    ),
  ],
  'india-ai': [
    story(
      '0192f0c4-0000-7000-8000-000000000031',
      'india-ai',
      'India AI',
      'India opens a public AI computing hub for start-ups',
    ),
  ],
  'innovations-research': [],
};

const json = (body: unknown) => ({
  status: 200,
  contentType: 'application/json',
  headers: {
    'X-Data-As-Of': new Date(Date.now() - 600_000).toISOString(),
    'Access-Control-Expose-Headers': 'X-Data-As-Of',
  },
  body: JSON.stringify(body),
});

export const BREAKING = story(
  '0192f0c4-0000-7000-8000-000000000099',
  'global-ai-tech',
  'AI Wire',
  'Breaking: a lab shares a new open model',
  0,
);

/**
 * Serves a small, fixed news set for every /api/v1 call. `state.late` marks today's edition as
 * running late (spec 006).
 */
export async function mockApi(page: Page, state: { breaking?: boolean; late?: boolean } = {}) {
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (path === '/api/v1/sections') return route.fulfill(json(SECTIONS));
    if (path === '/api/v1/edition') {
      const published = new Date(Date.now() - 2 * 3600_000);
      return route.fulfill(
        json({
          id: 'e1',
          cutoffAt: new Date(published.getTime() - 30 * 60_000).toISOString(),
          publishedAt: published.toISOString(),
          storyCount: 4,
          nextCutoffAt: new Date(Date.now() + 22 * 3600_000).toISOString(),
          late: state.late ?? false,
        }),
      );
    }
    if (path === '/api/v1/sources')
      return route.fulfill(json([{ name: 'The Hindu', homepage: 'https://www.thehindu.com/' }]));
    if (path === '/api/v1/site')
      return route.fulfill(json({ name: 'Q-Bits', contactEmail: 'debrup28.nitdgp@gmail.com' }));
    if (path === '/api/v1/stories') {
      const section = url.searchParams.get('section');
      const hours = Number(url.searchParams.get('hours') ?? '72');
      const cutoff = Date.now() - hours * 3600_000;
      const all = [...(state.breaking ? [BREAKING] : []), ...Object.values(STORIES).flat()];
      const data = (section ? all.filter((s) => s.section.slug === section) : all)
        .filter((s) => Date.parse(s.publishedAt) >= cutoff)
        .sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt));
      return route.fulfill(json({ data, nextCursor: null }));
    }
    const match = path.match(/^\/api\/v1\/stories\/(.+)$/);
    if (match) {
      const found = Object.values(STORIES)
        .flat()
        .find((s) => s.id === match[1]);
      return found
        ? route.fulfill(json(found))
        : route.fulfill({
            status: 404,
            contentType: 'application/problem+json',
            body: JSON.stringify({ code: 'STORY_NOT_FOUND' }),
          });
    }
    return route.fulfill({ status: 404, body: '{}' });
  });
}

export const FIRST_INDIA_STORY = STORIES['india-ai']![0]!;
