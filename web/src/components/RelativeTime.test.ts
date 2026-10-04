import { estimatedRelativeTime, relativeTime } from './RelativeTime';

const NOW = new Date('2026-10-03T09:00:00Z');

// 003 R6.1
it.each([
  ['2026-10-03T08:59:30Z', 'just now'],
  ['2026-10-03T08:45:00Z', '15 minutes ago'],
  ['2026-10-03T04:00:00Z', '5 hours ago'],
  ['2026-10-02T09:00:00Z', 'yesterday'],
  ['2026-10-01T09:00:00Z', '2 days ago'],
])('%s is %s', (iso, text) => {
  expect(relativeTime(iso, NOW)).toBe(text);
});

// 003 R6.1: an estimated date never reads "about yesterday"
it.each([
  ['2026-10-03T08:45:00Z', 'about 15 minutes ago'],
  ['2026-10-03T04:00:00Z', 'about 5 hours ago'],
  ['2026-10-02T09:00:00Z', 'yesterday (estimated)'],
  ['2026-10-01T21:00:00Z', 'yesterday (estimated)'],
  ['2026-10-03T08:59:30Z', 'just now (estimated)'],
  ['2026-10-01T09:00:00Z', 'about 2 days ago'],
])('estimated %s is %s', (iso, text) => {
  expect(estimatedRelativeTime(iso, NOW)).toBe(text);
});
