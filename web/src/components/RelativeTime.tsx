const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['day', 86_400],
  ['hour', 3_600],
  ['minute', 60],
];

const format = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

/** "5 hours ago" from two instants. */
export function relativeTime(iso: string, now: Date): string {
  const seconds = Math.round((new Date(iso).getTime() - now.getTime()) / 1000);
  for (const [unit, size] of UNITS) {
    if (Math.abs(seconds) >= size) {
      return format.format(Math.round(seconds / size), unit);
    }
  }
  return 'just now';
}

/**
 * Relative time for a date the publisher did not give (the fetch time was used). Numbers read
 * "about 5 hours ago"; words such as "yesterday" read "yesterday (estimated)", never
 * "about yesterday".
 */
export function estimatedRelativeTime(iso: string, now: Date): string {
  const text = relativeTime(iso, now);
  return /\d/.test(text) ? `about ${text}` : `${text} (estimated)`;
}

/**
 * Relative publish time with the exact date on hover. Marks the time as approximate when the
 * publisher gave no date and the fetch time was used instead (spec 003 R6.1).
 */
export function RelativeTime({
  iso,
  estimated = false,
  now = new Date(),
}: {
  iso: string;
  estimated?: boolean;
  now?: Date;
}) {
  const text = estimated ? estimatedRelativeTime(iso, now) : relativeTime(iso, now);
  const exact = new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
  return (
    <time dateTime={iso} title={exact}>
      {text}
    </time>
  );
}
