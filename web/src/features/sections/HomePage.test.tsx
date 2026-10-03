import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse, delay } from 'msw';
import { page, sectionsHandler, story } from '../../test/fixtures';
import { API, renderAt } from '../../test/render';
import { server } from '../../test/server';
import { HomePage } from './HomePage';

function storiesHandler() {
  return http.get(`${API}/api/v1/stories`, ({ request }) => {
    const url = new URL(request.url);
    const section = url.searchParams.get('section');
    expect(url.searchParams.get('limit')).toBe('5');
    if (section === 'india-ai') {
      return HttpResponse.json(page([story()]));
    }
    if (section === 'global-ai-tech') {
      return HttpResponse.json(
        page([
          story({
            id: 'a',
            headline: 'Model A',
            section: { slug: 'global-ai-tech', name: 'Global AI Tech' },
          }),
          story({
            id: 'b',
            headline: 'Model B',
            section: { slug: 'global-ai-tech', name: 'Global AI Tech' },
          }),
        ]),
      );
    }
    return HttpResponse.json(page([]));
  });
}

it('shows the four sections with their newest stories', async () => {
  server.use(sectionsHandler, storiesHandler());
  renderAt(<HomePage />);

  expect(
    screen.getByRole('heading', { level: 1, name: 'AI news from the last 72 hours' }),
  ).toBeInTheDocument();
  const india = await screen.findByRole('region', { name: 'India AI' });
  expect(await within(india).findByRole('heading', { name: story().headline })).toBeInTheDocument();
  expect(within(india).getByRole('link', { name: 'See all India AI' })).toHaveAttribute(
    'href',
    '/section/india-ai',
  );

  const tech = screen.getByRole('region', { name: 'Global AI Tech' });
  expect(await within(tech).findAllByRole('article')).toHaveLength(2);
});

it('shows an empty message for a quiet section', async () => {
  server.use(sectionsHandler, storiesHandler());
  renderAt(<HomePage />);

  const research = await screen.findByRole('region', { name: 'Innovations & Research' });
  expect(
    await within(research).findByText(
      'No Innovations & Research news in the last 72 hours. Check back soon.',
    ),
  ).toBeInTheDocument();
});

it('says when the news was last updated', async () => {
  server.use(sectionsHandler, storiesHandler());
  renderAt(<HomePage />);

  expect(await screen.findByText(/^Updated/)).toBeInTheDocument();
});

it('shows skeleton cards while loading', async () => {
  server.use(
    http.get(`${API}/api/v1/sections`, async () => {
      await delay('infinite');
      return HttpResponse.json([]);
    }),
  );
  renderAt(<HomePage />);

  expect(screen.getByRole('status', { name: 'Loading news' })).toBeInTheDocument();
  expect(screen.getAllByTestId('skeleton-card').length).toBeGreaterThan(0);
});

it('shows a plain error with a working retry', async () => {
  let fail = true;
  server.use(
    http.get(`${API}/api/v1/sections`, () =>
      fail ? HttpResponse.json({ code: 'INTERNAL' }, { status: 500 }) : HttpResponse.json([]),
    ),
  );
  renderAt(<HomePage />);

  const alert = await screen.findByRole('alert');
  expect(alert).toHaveTextContent("We couldn't load the news. Try again.");
  expect(alert).not.toHaveTextContent('INTERNAL');

  fail = false;
  await userEvent.click(within(alert).getByRole('button', { name: 'Try again' }));
  await screen.findByText('Explained in plain words, with links to every original story.');
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});
