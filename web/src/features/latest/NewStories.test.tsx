import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { AppRoutes } from '../../app/AppRoutes';
import { page, sectionsHandler, story } from '../../test/fixtures';
import { API, renderAt } from '../../test/render';
import { server } from '../../test/server';

afterEach(() => vi.useRealTimers());

it('offers new stories without moving the feed, then adds them on request', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  let hasNew = false;
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories`, () =>
      HttpResponse.json(
        page(
          hasNew
            ? [
                story({ id: 'n1', headline: 'Brand new story' }),
                story({ id: 'a', headline: 'Older story' }),
              ]
            : [story({ id: 'a', headline: 'Older story' })],
        ),
      ),
    ),
  );
  renderAt(<AppRoutes />, '/');
  await screen.findByRole('heading', { name: 'Older story' });
  expect(screen.queryByRole('button', { name: /new stor/ })).not.toBeInTheDocument();

  hasNew = true;
  await act(async () => {
    await vi.advanceTimersByTimeAsync(120_000);
  });

  const button = await screen.findByRole('button', { name: /1 new story/ });
  expect(button.closest('[aria-live="polite"]')).not.toBeNull();
  const headlinesBefore = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent);
  expect(headlinesBefore).toEqual(['Older story']);

  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  await user.click(button);

  await screen.findByRole('heading', { name: 'Brand new story' });
  const feed = screen.getAllByRole('article');
  expect(within(feed[0]!).getByRole('heading', { level: 2 })).toHaveTextContent('Brand new story');
  expect(screen.queryByRole('button', { name: /new stor/ })).not.toBeInTheDocument();
});
