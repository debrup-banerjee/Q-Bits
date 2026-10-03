import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { AppRoutes } from '../../app/AppRoutes';
import { page, sectionsHandler, story } from '../../test/fixtures';
import { API, renderAt } from '../../test/render';
import { server } from '../../test/server';

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
  expect(screen.getByText(/Updated/)).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: 'Load more' }));

  expect(await screen.findByRole('heading', { name: 'Second story' })).toBeInTheDocument();
  expect(seen[0]).toEqual({ hours: '24', cursor: null });
  expect(seen[1]).toEqual({ hours: '24', cursor: 'c1' });
  expect(screen.getByText(/That's everything from the last 24 hours/)).toBeInTheDocument();
});

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
