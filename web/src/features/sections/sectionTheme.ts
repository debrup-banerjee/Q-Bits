/**
 * Each section gets its own colour, reused from the source-badge palette (already verified AA
 * for white text, light and dark). Colour marks wayfinding only -- never body text -- so
 * readability never depends on it (conventions: colourful but compliant).
 */
const SECTION_DOT: Record<string, string> = {
  'global-ai-tech': 'bg-badge-1',
  'new-releases': 'bg-badge-5',
  'world-business': 'bg-badge-3',
  'india-ai': 'bg-badge-4',
  'innovations-research': 'bg-badge-7',
};

const SECTION_PILL: Record<string, string> = {
  'global-ai-tech': 'bg-badge-1 text-white',
  'new-releases': 'bg-badge-5 text-white',
  'world-business': 'bg-badge-3 text-white',
  'india-ai': 'bg-badge-4 text-white',
  'innovations-research': 'bg-badge-7 text-white',
};

const DEFAULT_DOT = 'bg-accent';
const DEFAULT_PILL = 'bg-accent text-accent-ink';

/** A small decorative dot's background colour for this section (falls back to the brand accent). */
export function sectionDot(slug: string): string {
  return SECTION_DOT[slug] ?? DEFAULT_DOT;
}

/** A solid pill's background + text colour for this section (falls back to the brand accent). */
export function sectionPill(slug: string): string {
  return SECTION_PILL[slug] ?? DEFAULT_PILL;
}
