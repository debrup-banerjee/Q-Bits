import { relativeTime } from './RelativeTime';

const NOW = new Date('2026-10-03T09:00:00Z');

it.each([
  ['2026-10-03T08:59:30Z', 'just now'],
  ['2026-10-03T08:45:00Z', '15 minutes ago'],
  ['2026-10-03T04:00:00Z', '5 hours ago'],
  ['2026-10-02T09:00:00Z', 'yesterday'],
  ['2026-10-01T09:00:00Z', '2 days ago'],
])('%s is %s', (iso, text) => {
  expect(relativeTime(iso, NOW)).toBe(text);
});
