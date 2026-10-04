const TIME = new Intl.DateTimeFormat('en-IN', { hour: 'numeric', minute: '2-digit' });
const DAY = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short' });

function sameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

function addDays(d: Date, days: number): Date {
  const copy = new Date(d);
  copy.setDate(copy.getDate() + days);
  return copy;
}

/** "Today's digest", "Yesterday's digest" or "Digest of 2 Oct" (spec 006 R5.3). */
export function editionLabel(publishedAt: string, now: Date): string {
  const published = new Date(publishedAt);
  if (sameDay(published, now)) return "Today's digest";
  if (sameDay(published, addDays(now, -1))) return "Yesterday's digest";
  return `Digest of ${DAY.format(published)}`;
}

export function timeOf(iso: string): string {
  return TIME.format(new Date(iso)).toLowerCase();
}

/** "Next edition around 6:00 am tomorrow" (spec 006 R5.3). */
export function nextEditionText(nextCutoffAt: string, now: Date): string {
  const next = new Date(nextCutoffAt);
  const when = sameDay(next, now)
    ? 'today'
    : sameDay(next, addDays(now, 1))
      ? 'tomorrow'
      : DAY.format(next);
  return `Next edition around ${timeOf(nextCutoffAt)} ${when}`;
}
