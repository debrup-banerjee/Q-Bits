import type { StoryView } from '@qbits/api-client';
import { useId } from 'react';
import { paletteOf, seeded, topicOf, type Topic } from './coverArtTheme';

/** White line icons, drawn on a 48×48 grid. Our own shapes; no logos. */
const GLYPHS: Record<Topic, string> = {
  chip: 'M14 14h20v20H14zM19 19h10v10H19zM18 8v6M24 8v6M30 8v6M18 34v6M24 34v6M30 34v6M8 18h6M8 24h6M8 30h6M34 18h6M34 24h6M34 30h6',
  robot:
    'M12 18h24v18H12zM24 10v8M24 8a2 2 0 1 0 0.01 0M18 26a2 2 0 1 0 0.01 0M30 26a2 2 0 1 0 0.01 0M19 32h10M8 24v6M40 24v6',
  chart: 'M8 40h32M12 40V28M20 40V20M28 40V24M36 40V12M12 24l8-8 8 4 10-10',
  globe: 'M24 6a18 18 0 1 0 0.01 0M6 24h36M24 6c-6 6-6 30 0 36M24 6c6 6 6 30 0 36M9 14h30M9 34h30',
  flask: 'M19 6h10M21 6v12L10 38a3 3 0 0 0 3 4h22a3 3 0 0 0 3-4L27 18V6M14 30h20',
  shield: 'M24 6l15 6v10c0 10-7 17-15 20C16 39 9 32 9 22V12zM17 24l5 5 9-10',
  code: 'M18 14l-10 10 10 10M30 14l10 10-10 10M27 10l-6 28',
  spark:
    'M24 6l3 12 12 3-12 3-3 12-3-12-12-3 12-3zM38 32l1.5 5 5 1.5-5 1.5-1.5 5-1.5-5-5-1.5 5-1.5z',
};

/**
 * Generated cover art for a story with no safe picture (spec 009 R1). Drawn entirely from the
 * story's own section, words and id, so there is nothing to license. Decorative: hidden from
 * screen readers, since the headline already says what the story is about.
 */
export function CoverArt({ story, className = '' }: { story: StoryView; className?: string }) {
  const gid = useId().replace(/:/g, '');
  const [from, to] = paletteOf(story.section.slug);
  const topic = topicOf(
    [story.headline, ...story.keyTerms.map((t) => t.term), story.section.name].join(' '),
  );
  const rand = seeded(story.id);
  const circles = Array.from({ length: 5 }, () => ({
    cx: Math.round(rand() * 640),
    cy: Math.round(rand() * 320),
    r: Math.round(40 + rand() * 140),
    o: (0.05 + rand() * 0.1).toFixed(2),
  }));
  const tilt = Math.round(-25 + rand() * 50);
  const glyphX = Math.round(330 + rand() * 90);

  return (
    <svg
      aria-hidden="true"
      data-testid="cover-art"
      viewBox="0 0 640 320"
      preserveAspectRatio="xMidYMid slice"
      className={`story-img ${className}`}
    >
      <defs>
        <linearGradient id={`${gid}-bg`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" style={{ stopColor: from }} />
          <stop offset="1" style={{ stopColor: to }} />
        </linearGradient>
        <pattern id={`${gid}-dots`} width="22" height="22" patternUnits="userSpaceOnUse">
          <circle cx="2" cy="2" r="1.4" fill="white" fillOpacity="0.16" />
        </pattern>
      </defs>
      <rect width="640" height="320" fill={`url(#${gid}-bg)`} />
      <rect width="640" height="320" fill={`url(#${gid}-dots)`} />
      {circles.map((c, i) => (
        <circle key={i} cx={c.cx} cy={c.cy} r={c.r} fill="white" fillOpacity={c.o} />
      ))}
      <g transform={`rotate(${tilt} 320 160)`} stroke="white" strokeOpacity="0.12" strokeWidth="2">
        {Array.from({ length: 9 }, (_, i) => (
          <line key={i} x1={-200 + i * 110} y1="-200" x2={-200 + i * 110} y2="520" />
        ))}
      </g>
      <g
        transform={`translate(${glyphX} 70) scale(3.8)`}
        fill="none"
        stroke="white"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeOpacity="0.92"
      >
        <path d={GLYPHS[topic]} />
      </g>
    </svg>
  );
}
