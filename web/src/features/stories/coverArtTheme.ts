/**
 * Choices behind a story's cover art (spec 009 R1): the section's colours, a topic glyph picked
 * from the story's own words, and a seed so each story's pattern is different but stable. Pure, so
 * the same story always gets the same art.
 */

export type Topic = 'chip' | 'robot' | 'chart' | 'globe' | 'flask' | 'shield' | 'code' | 'spark';

const TOPICS: [Topic, RegExp][] = [
  ['chip', /\b(gpu|chip|semiconductor|nvidia|data ?cent(er|re)|compute|server)/],
  ['robot', /\b(robot|humanoid|drone|autonomous|self-driving)/],
  ['shield', /\b(security|privacy|deepfake|safety|scam|fraud|regulat|law|policy|ban)/],
  ['chart', /\b(funding|invest|revenue|market|shares?|stock|valuation|acqui|ipo|billion|layoff)/],
  ['flask', /\b(research|paper|study|scientist|lab|universit|breakthrough|medical|health)/],
  ['code', /\b(open[- ]source|code|developer|github|api|agent|coding)/],
  ['globe', /\b(global|world|countr|india|government|nation|international)/],
];

/** Gradient stops per section; every stop is dark enough for the white glyph. */
const PALETTES: Record<string, [string, string]> = {
  'global-ai-tech': ['oklch(0.36 0.14 262)', 'oklch(0.52 0.2 250)'],
  'new-releases': ['oklch(0.38 0.15 20)', 'oklch(0.56 0.19 38)'],
  'world-business': ['oklch(0.34 0.09 165)', 'oklch(0.52 0.13 150)'],
  'india-ai': ['oklch(0.38 0.11 50)', 'oklch(0.58 0.15 68)'],
  'innovations-research': ['oklch(0.34 0.15 300)', 'oklch(0.52 0.2 325)'],
};
const DEFAULT_PALETTE: [string, string] = ['oklch(0.3 0.09 262)', 'oklch(0.45 0.22 305)'];

export function topicOf(text: string): Topic {
  const lower = text.toLowerCase();
  return TOPICS.find(([, pattern]) => pattern.test(lower))?.[0] ?? 'spark';
}

export function paletteOf(sectionSlug: string): [string, string] {
  return PALETTES[sectionSlug] ?? DEFAULT_PALETTE;
}

/** A small, stable pseudo-random sequence from a string (mulberry32 over an FNV-1a hash). */
export function seeded(key: string): () => number {
  let h = 2166136261;
  for (let i = 0; i < key.length; i++) {
    h = Math.imul(h ^ key.charCodeAt(i), 16777619);
  }
  let a = h >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
