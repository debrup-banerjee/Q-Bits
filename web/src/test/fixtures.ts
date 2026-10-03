import type { SectionView, StoryPage, StoryView } from '@qbits/api-client';
import { http, HttpResponse } from 'msw';
import { API } from './render';

export const SECTIONS: SectionView[] = [
  { slug: 'global-ai-tech', name: 'Global AI Tech', description: 'Models.', storyCount: 2 },
  { slug: 'world-business', name: 'World Business', description: 'Money.', storyCount: 1 },
  { slug: 'india-ai', name: 'India AI', description: 'India.', storyCount: 1 },
  {
    slug: 'innovations-research',
    name: 'Innovations & Research',
    description: 'Labs.',
    storyCount: 0,
  },
];

export function story(overrides: Partial<StoryView> = {}): StoryView {
  return {
    id: '0192f0c4-0000-7000-8000-000000000001',
    section: { slug: 'india-ai', name: 'India AI' },
    headline: 'India opens a public AI computing hub for start-ups',
    summary: 'According to The Hindu, start-ups can now rent powerful chips at low prices.',
    keyTerms: [{ term: 'GPU', meaning: 'A chip that does many small sums at once.' }],
    source: { name: 'The Hindu', homepage: 'https://www.thehindu.com/' },
    originalUrl: 'https://www.thehindu.com/sci-tech/ai-hub',
    publishedAt: '2026-10-03T04:00:00Z',
    dateEstimated: false,
    attribution: "Summary written from The Hindu's headline and teaser",
    resources: [],
    ...overrides,
  };
}

export function page(stories: StoryView[], nextCursor: string | null = null): StoryPage {
  return { data: stories, nextCursor };
}

export const sectionsHandler = http.get(`${API}/api/v1/sections`, () =>
  HttpResponse.json(SECTIONS, { headers: { 'X-Data-As-Of': '2026-10-03T04:00:00Z' } }),
);
