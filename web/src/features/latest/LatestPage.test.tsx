import { createQBitsApi } from '@qbits/api-client';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router';
import { AppRoutes } from '../../app/AppRoutes';
import { Providers } from '../../app/providers';
import { page, sectionsHandler, story } from '../../test/fixtures';
import { API, renderAt } from '../../test/render';
import { server } from '../../test/server';

// 004 R3.2
it('lists the last 24 hours and loads more with the cursor', async () => {
  const seen: { hours: string | null; cursor: string | null }[] = [];
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories`, ({ request }) => {
      const q = new URL(request.url).searchParams;
      seen.push({ hours: q.get('hours'), cursor: q.get('cursor') });
      return q.get('cursor') === null
        ? HttpResponse.json(page([story({ id: '1', headline: 'First story' })], 'c1'), {
            headers: { 'X-Data-As-Of': '2026-10-03T04:00:00Z' },
          })
        : HttpResponse.json(page([story({ id: '2', headline: 'Second story' })]));
    }),
  );
  renderAt(<AppRoutes />, '/');

  expect(screen.getByRole('heading', { level: 1, name: 'AI Latest' })).toBeInTheDocument();
  expect(
    screen.getByText(/Every AI story from the last 24 hours, newest first/),
  ).toBeInTheDocument();
  expect(await screen.findByRole('heading', { name: 'First story' })).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: 'Load more' }));

  expect(await screen.findByRole('heading', { name: 'Second story' })).toBeInTheDocument();
  expect(seen[0]).toEqual({ hours: '24', cursor: null });
  expect(seen[1]).toEqual({ hours: '24', cursor: 'c1' });
  expect(screen.getByText(/That's everything from the last 24 hours/)).toBeInTheDocument();
});

function editionHandler(over: Record<string, unknown> = {}) {
  const now = new Date();
  const published = new Date(now);
  published.setHours(6, 42, 0, 0);
  if (published > now) published.setDate(published.getDate() - 1);
  const next = new Date(published);
  next.setDate(next.getDate() + 1);
  next.setHours(6, 0, 0, 0);
  return http.get(`${API}/api/v1/edition`, () =>
    HttpResponse.json({
      id: 'e1',
      cutoffAt: published.toISOString(),
      publishedAt: published.toISOString(),
      storyCount: 1,
      nextCutoffAt: next.toISOString(),
      late: false,
      ...over,
    }),
  );
}

// 006 R5.3
it('shows which edition it is and when the next one is due', async () => {
  server.use(
    sectionsHandler,
    editionHandler(),
    http.get(`${API}/api/v1/stories`, () => HttpResponse.json(page([story()]))),
  );
  renderAt(<AppRoutes />, '/');

  expect(await screen.findByText(/digest · published 6:42 am/)).toBeInTheDocument();
  expect(screen.getByText(/Next edition around 6:00 am/)).toBeInTheDocument();
  expect(screen.queryByText(/running late/)).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /new stor/ })).not.toBeInTheDocument();
});

// 006 R5.3
it("says when today's edition is running late", async () => {
  server.use(
    sectionsHandler,
    editionHandler({ late: true }),
    http.get(`${API}/api/v1/stories`, () => HttpResponse.json(page([story()]))),
  );
  renderAt(<AppRoutes />, '/');

  expect(
    await screen.findByText("Today's edition is running late. Here is the last one."),
  ).toBeInTheDocument();
});

// 004 R3.3
it('shows the empty state with a way to browse sections', async () => {
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories`, () => HttpResponse.json(page([]))),
  );
  renderAt(<AppRoutes />, '/');

  expect(
    await screen.findByText('No AI news in the last 24 hours yet. Check back soon.'),
  ).toBeInTheDocument();
  expect(screen.getAllByRole('link', { name: 'Browse by section' })[0]).toHaveAttribute(
    'href',
    '/sections',
  );
});

// 004 R3.4
it('shows feed skeletons while loading', () => {
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories`, async () => {
      await delay('infinite');
      return HttpResponse.json(page([]));
    }),
  );
  renderAt(<AppRoutes />, '/');

  expect(screen.getByRole('status', { name: 'Loading news' })).toBeInTheDocument();
  expect(screen.getAllByTestId('feed-skeleton').length).toBeGreaterThan(0);
});

// 004 R3.4
it('shows a plain error with a working retry', async () => {
  let fail = true;
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories`, () =>
      fail
        ? HttpResponse.json({ code: 'INTERNAL' }, { status: 500 })
        : HttpResponse.json(page([story()])),
    ),
  );
  renderAt(<AppRoutes />, '/');

  const alert = await screen.findByRole('alert');
  expect(alert).toHaveTextContent("We couldn't load the news. Try again.");
  fail = false;
  await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
  expect(await screen.findByRole('heading', { name: story().headline })).toBeInTheDocument();
});

it('reloads the edition and the feed when the tab is reopened after the cut-off', async () => {
  // 006 R5.4
  let edition = 1;
  const requests = { edition: 0, stories: 0 };
  const yesterday = new Date(Date.now() - 24 * 60 * 60 * 1000);
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/edition`, () => {
      requests.edition += 1;
      const published = edition === 1 ? yesterday : new Date();
      return HttpResponse.json({
        id: `e${edition}`,
        cutoffAt: published.toISOString(),
        publishedAt: published.toISOString(),
        storyCount: 1,
        nextCutoffAt: new Date(published.getTime() + 24 * 60 * 60 * 1000).toISOString(),
        late: false,
      });
    }),
    http.get(`${API}/api/v1/stories`, () => {
      requests.stories += 1;
      return HttpResponse.json(
        page([story({ id: `s${edition}`, headline: `Story from edition ${edition}` })]),
      );
    }),
  );
  // The app's own query client, so its focus settings are what is tested.
  render(
    <Providers api={createQBitsApi(API)}>
      <MemoryRouter initialEntries={['/']}>
        <AppRoutes />
      </MemoryRouter>
    </Providers>,
  );
  expect(await screen.findByRole('heading', { name: 'Story from edition 1' })).toBeInTheDocument();
  expect(await screen.findByText(/Yesterday's digest/)).toBeInTheDocument();
  const before = { ...requests };

  vi.useFakeTimers({ toFake: ['Date'], now: Date.now() });
  try {
    setVisibility('hidden');
    edition = 2; // the next edition is published while the tab is in the background
    vi.setSystemTime(Date.now() + 3 * 60 * 60 * 1000);
    setVisibility('visible');

    expect(
      await screen.findByRole('heading', { name: 'Story from edition 2' }),
    ).toBeInTheDocument();
    expect(await screen.findByText(/Today's digest/)).toBeInTheDocument();
    expect(requests.edition).toBeGreaterThan(before.edition);
    expect(requests.stories).toBeGreaterThan(before.stories);
  } finally {
    vi.useRealTimers();
    setVisibility('visible');
  }
});

function setVisibility(state: DocumentVisibilityState) {
  Object.defineProperty(document, 'visibilityState', { configurable: true, value: state });
  fireEvent(window, new Event('visibilitychange'));
}
