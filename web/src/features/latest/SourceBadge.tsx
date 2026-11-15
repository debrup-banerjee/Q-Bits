const COLOURS = [
  'bg-badge-1',
  'bg-badge-2',
  'bg-badge-3',
  'bg-badge-4',
  'bg-badge-5',
  'bg-badge-6',
  'bg-badge-7',
  'bg-badge-8',
] as const;

/** Stable colour index for a source name, so a source always gets the same badge. */
export function badgeColour(name: string): (typeof COLOURS)[number] {
  let hash = 0;
  for (const ch of name) {
    hash = (hash * 31 + ch.codePointAt(0)!) >>> 0;
  }
  return COLOURS[hash % COLOURS.length]!;
}

/** A letter in a circle standing in for the source. Never a logo or image (spec 004 R4.1). */
export function SourceBadge({ name }: { name: string }) {
  const letter = (name.trim()[0] ?? '?').toUpperCase();
  return (
    <span
      aria-hidden="true"
      data-testid="source-badge"
      className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-base font-bold text-white shadow-sm ${badgeColour(name)}`}
    >
      {letter}
    </span>
  );
}
