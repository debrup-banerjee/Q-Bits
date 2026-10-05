import { QueryClient } from '@tanstack/react-query';
import { page, story } from '../test/fixtures';
import { INITIAL_DATA_ID, seedInitialData } from './initial-data';

function docWith(content: string | null): Document {
  const doc = document.implementation.createHTMLDocument('test');
  if (content !== null) {
    const el = doc.createElement('script');
    el.id = INITIAL_DATA_ID;
    el.type = 'application/json';
    el.textContent = content;
    doc.head.append(el);
  }
  return doc;
}

const STORY_PATH = `/story/${story().id}`;

function storyData(path = STORY_PATH) {
  return JSON.stringify({
    path,
    queries: [
      {
        key: ['story', story().id],
        infinite: false,
        data: story(),
        dataAsOf: '2026-10-03T04:00:00Z',
      },
    ],
  });
}

// 007 R4.2
it('seeds a single query in the shape the hooks cache', () => {
  const client = new QueryClient();

  const seeded = seedInitialData(client, docWith(storyData()), STORY_PATH);

  expect(seeded).toBe(1);
  expect(client.getQueryData(['story', story().id])).toEqual({
    data: story(),
    dataAsOf: '2026-10-03T04:00:00Z',
  });
});

// 007 R4.2
it('seeds an infinite query as its first page', () => {
  const client = new QueryClient();
  const content = JSON.stringify({
    path: '/',
    queries: [
      {
        key: ['stories', 'all', 20, 24],
        infinite: true,
        data: page([story()], 'c1'),
        dataAsOf: null,
      },
    ],
  });

  seedInitialData(client, docWith(content), '/');

  expect(client.getQueryData(['stories', 'all', 20, 24])).toEqual({
    pages: [{ data: page([story()], 'c1'), dataAsOf: null }],
    pageParams: [undefined],
  });
});

// 007 R4.4
it('treats seeded data as just fetched', () => {
  const client = new QueryClient();
  const before = Date.now();

  seedInitialData(client, docWith(storyData()), STORY_PATH);

  const state = client.getQueryState(['story', story().id]);
  expect(state?.dataUpdatedAt).toBeGreaterThanOrEqual(before);
});

// 007 R4.3
it.each([
  ['no element', null, STORY_PATH],
  ['another page', storyData('/story/other'), STORY_PATH],
  ['broken JSON', '{"path":', STORY_PATH],
  [
    'the wrong shape',
    JSON.stringify({ path: STORY_PATH, queries: [{ key: 'story' }] }),
    STORY_PATH,
  ],
])('ignores %s', (_name, content, path) => {
  const client = new QueryClient();

  expect(seedInitialData(client, docWith(content), path)).toBe(0);
  expect(client.getQueryCache().getAll()).toHaveLength(0);
});
