import { screen } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { AppRoutes } from '../../app/AppRoutes';
import { sectionsHandler, story } from '../../test/fixtures';
import { API, renderAt } from '../../test/render';
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
