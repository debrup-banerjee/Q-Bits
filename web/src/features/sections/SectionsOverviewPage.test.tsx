import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse, delay } from 'msw';
import { page, sectionsHandler, story } from '../../test/fixtures';
import { API, renderAt } from '../../test/render';
import { server } from '../../test/server';
import { SectionsOverviewPage } from './SectionsOverviewPage';

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
            section: { slug: 'global-ai-tech', name: 'AI Wire' },
          }),
          story({
            id: 'b',
            headline: 'Model B',
            section: { slug: 'global-ai-tech', name: 'AI Wire' },
          }),
        ]),
      );
    }
    return HttpResponse.json(page([]));
  });
}

// 003 R4.1
it('shows the five sections with their newest stories', async () => {
  server.use(sectionsHandler, storiesHandler());
  renderAt(<SectionsOverviewPage />);

  expect(screen.getByRole('heading', { level: 1, name: 'Browse by section' })).toBeInTheDocument();
  const india = await screen.findByRole('region', { name: 'India AI' });
  expect(await within(india).findByRole('heading', { name: story().headline })).toBeInTheDocument();
  expect(within(india).getByRole('link', { name: 'See all India AI' })).toHaveAttribute(
    'href',
    '/section/india-ai',
  );

  const tech = screen.getByRole('region', { name: 'AI Wire' });
  expect(await within(tech).findAllByRole('article')).toHaveLength(2);
});

// 003 R5.2
it('shows an empty message for a quiet section', async () => {
  server.use(sectionsHandler, storiesHandler());
  renderAt(<SectionsOverviewPage />);

  const research = await screen.findByRole('region', { name: 'AI Innovations' });
  expect(
    await within(research).findByText(
      'No AI Innovations news in the last 72 hours. Check back soon.',
    ),
  ).toBeInTheDocument();
});

// 003 R4.2
it('shows the 72-hour line together with when the news was last updated', async () => {
  server.use(sectionsHandler, storiesHandler());
  renderAt(<SectionsOverviewPage />);

  const updated = await screen.findByText(/^Updated/);
  const line = updated.closest('p');
  expect(line).toHaveTextContent(/^AI news from the last 72 hours, sorted into five sections\./);
  expect(line).toHaveTextContent(/Updated .+/);
});

// 003 R4.2
it('shows the 72-hour line before the data has loaded', () => {
  server.use(
    http.get(`${API}/api/v1/sections`, async () => {
      await delay('infinite');
      return HttpResponse.json([]);
    }),
  );
  renderAt(<SectionsOverviewPage />);

  expect(screen.getByText(/^AI news from the last 72 hours/)).toBeInTheDocument();
  expect(screen.queryByText(/^Updated/)).not.toBeInTheDocument();
});

// 003 R8.1
it('shows skeleton cards while loading', async () => {
  server.use(
    http.get(`${API}/api/v1/sections`, async () => {
      await delay('infinite');
      return HttpResponse.json([]);
    }),
  );
  renderAt(<SectionsOverviewPage />);

  expect(screen.getByRole('status', { name: 'Loading news' })).toBeInTheDocument();
  expect(screen.getAllByTestId('skeleton-card').length).toBeGreaterThan(0);
});

// 003 R8.2
it('shows a plain error with a working retry', async () => {
  let fail = true;
  server.use(
    http.get(`${API}/api/v1/sections`, () =>
      fail ? HttpResponse.json({ code: 'INTERNAL' }, { status: 500 }) : HttpResponse.json([]),
    ),
  );
  renderAt(<SectionsOverviewPage />);

  const alert = await screen.findByRole('alert');
  expect(alert).toHaveTextContent("We couldn't load the news. Try again.");
  expect(alert).not.toHaveTextContent('INTERNAL');

  fail = false;
  await userEvent.click(within(alert).getByRole('button', { name: 'Try again' }));
  await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
  expect(screen.getByText(/^AI news from the last 72 hours/)).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});
