import { editionLabel, nextEditionText, timeOf } from './editionText';

// Tests run in the machine's time zone; build local dates so they hold anywhere.
const now = new Date(2026, 9, 4, 9, 0); // 4 Oct 2026, 09:00 local

it('labels today, yesterday and older editions', () => {
  expect(editionLabel(new Date(2026, 9, 4, 6, 42).toISOString(), now)).toBe("Today's digest");
  expect(editionLabel(new Date(2026, 9, 3, 6, 42).toISOString(), now)).toBe("Yesterday's digest");
  expect(editionLabel(new Date(2026, 9, 1, 6, 42).toISOString(), now)).toMatch(/^Digest of 1 Oct/);
});

it('says when the next edition is due', () => {
  expect(nextEditionText(new Date(2026, 9, 5, 6, 0).toISOString(), now)).toBe(
    'Next edition around 6:00 am tomorrow',
  );
  expect(nextEditionText(new Date(2026, 9, 4, 18, 0).toISOString(), now)).toBe(
    'Next edition around 6:00 pm today',
  );
});

it('formats times in lower case', () => {
  expect(timeOf(new Date(2026, 9, 4, 6, 42).toISOString())).toBe('6:42 am');
});
