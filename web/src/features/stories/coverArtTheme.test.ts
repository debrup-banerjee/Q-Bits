import { paletteOf, seeded, topicOf } from './coverArtTheme';

// 009 R1.2
it('picks a topic glyph from the story words', () => {
  expect(topicOf('Nvidia unveils a faster GPU for data centres')).toBe('chip');
  expect(topicOf('Humanoid robot learns to fold laundry')).toBe('robot');
  expect(topicOf('Startup raises $40 million funding round')).toBe('chart');
  expect(topicOf('A new paper on reasoning')).toBe('flask');
  expect(topicOf('Something else entirely')).toBe('spark');
});

// 009 R1.1
it('is stable for the same story and differs between stories', () => {
  const a1 = seeded('story-a');
  const a2 = seeded('story-a');
  const b = seeded('story-b');
  const first = [a1(), a1(), a1()];
  expect([a2(), a2(), a2()]).toEqual(first);
  expect([b(), b(), b()]).not.toEqual(first);
  expect(paletteOf('india-ai')).not.toEqual(paletteOf('world-business'));
  expect(paletteOf('unknown')).toHaveLength(2);
});
