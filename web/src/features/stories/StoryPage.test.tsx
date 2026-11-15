import { screen } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { AppRoutes } from '../../app/AppRoutes';
import { sectionsHandler, story } from '../../test/fixtures';
import { API, renderAt, testQueryClient } from '../../test/render';
import { server } from '../../test/server';

// 003 R6.6
it('shows one story as the page heading', async () => {
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories/:id`, () => HttpResponse.json(story())),
  );
  renderAt(<AppRoutes />, `/story/${story().id}`);

  expect(
    await screen.findByRole('heading', { level: 1, name: story().headline }),
  ).toBeInTheDocument();
});

// 003 R6.6
it('explains when a story is gone', async () => {
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories/:id`, () =>
      HttpResponse.json({ code: 'STORY_NOT_FOUND', detail: 'No such story.' }, { status: 404 }),
    ),
  );
  renderAt(<AppRoutes />, '/story/old-one');

  expect(
    await screen.findByRole('heading', { name: "This story isn't available." }),
  ).toBeInTheDocument();
  expect(screen.getByText('Q-Bits only keeps stories from the last 72 hours.')).toBeInTheDocument();
});

// 003 R8.1
it('shows a skeleton card while the story loads', async () => {
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories/:id`, async () => {
      await delay('infinite');
      return HttpResponse.json(story());
    }),
  );
  renderAt(<AppRoutes />, `/story/${story().id}`);

  expect(await screen.findByRole('status', { name: 'Loading news' })).toBeInTheDocument();
  expect(screen.getAllByTestId('skeleton-card')).toHaveLength(1);
});

// 007 R4.2: with the server's initial data, the story shows on the first render, with no
// skeleton and no request for it (MSW fails any unhandled request).
it('renders a seeded story at once', () => {
  server.use(sectionsHandler);
  const client = testQueryClient();
  client.setQueryData(['story', story().id], { data: story(), dataAsOf: null });

  renderAt(<AppRoutes />, `/story/${story().id}`, client);

  expect(screen.getByRole('heading', { level: 1, name: story().headline })).toBeInTheDocument();
  expect(screen.queryByTestId('skeleton-card')).not.toBeInTheDocument();
});

// 007 R5.1
it('titles the page with the headline', async () => {
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories/:id`, () => HttpResponse.json(story())),
  );
  renderAt(<AppRoutes />, `/story/${story().id}`);

  await screen.findByRole('heading', { level: 1, name: story().headline });
  expect(document.title).toBe(`${story().headline} | Q-Bits`);
});

// 007 R5.1
it('titles a missing story', async () => {
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories/:id`, () =>
      HttpResponse.json({ code: 'STORY_NOT_FOUND', detail: 'No such story.' }, { status: 404 }),
    ),
  );
  renderAt(<AppRoutes />, '/story/old-one');

  await screen.findByRole('heading', { name: "This story isn't available." });
  expect(document.title).toBe('Story not available | Q-Bits');
});
