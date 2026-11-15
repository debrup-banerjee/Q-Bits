import type { TopStoryView } from '@qbits/api-client';
import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { story } from '../../test/fixtures';
import { API, renderAt } from '../../test/render';
import { server } from '../../test/server';
import { TopStories } from './TopStories';

function top(id: string, headline: string, coverage: number): TopStoryView {
  return { ...story({ id, headline }), coverage };
}

async function settle() {
  await act(async () => {
    await new Promise((r) => setTimeout(r, 50));
  });
}

function serve(items: TopStoryView[]) {
  server.use(http.get(`${API}/api/v1/stories/top`, () => HttpResponse.json(items)));
}

// 009 R3.1, R3.2, R3.3
it('shows each top story as a labelled slide with its coverage', async () => {
  serve([top('1', 'First big story', 4), top('2', 'Second big story', 3), top('3', 'Third', 2)]);
  renderAt(<TopStories />);

  expect(await screen.findByRole('heading', { name: 'Top stories' })).toBeInTheDocument();
  expect(screen.getByRole('region', { name: 'Top stories' })).toHaveAttribute(
    'aria-roledescription',
    'carousel',
  );
  const slides = screen.getAllByRole('group');
  expect(slides.map((s) => s.getAttribute('aria-label'))).toEqual(['1 of 3', '2 of 3', '3 of 3']);
  expect(within(slides[0]!).getByRole('link', { name: 'First big story' })).toHaveAttribute(
    'href',
    '/story/1',
  );
  expect(within(slides[0]!).getByText('Covered by 4 outlets')).toBeInTheDocument();
  expect(within(slides[0]!).getByText('The Hindu')).toBeInTheDocument();
  expect(within(slides[0]!).getByRole('link', { name: 'India AI' })).toBeInTheDocument();
});

// 009 R3.3
it('moves with the next and previous buttons and never autoplays', async () => {
  serve([top('1', 'One', 3), top('2', 'Two', 2)]);
  renderAt(<TopStories />);
  const next = await screen.findByRole('button', { name: 'Next story' });
  const prev = screen.getByRole('button', { name: 'Previous story' });

  expect(prev).toBeDisabled();
  await userEvent.click(next);
  expect(next).toBeDisabled();
  expect(prev).toBeEnabled();
  await userEvent.click(prev);
  expect(prev).toBeDisabled();
});

// 009 R3.4
it('renders nothing when no story qualifies or the request fails', async () => {
  serve([]);
  const empty = renderAt(<TopStories />);
  await settle();
  expect(empty.container.querySelector('section')).toBeNull();
  empty.unmount();

  server.use(http.get(`${API}/api/v1/stories/top`, () => HttpResponse.json({}, { status: 500 })));
  const failed = renderAt(<TopStories />);
  await settle();
  expect(failed.container.querySelector('section')).toBeNull();
});

// 009 R3.5
it('has no images, embeds or buttons for a single story', async () => {
  serve([top('1', 'Only one', 2)]);
  const { container } = renderAt(<TopStories />);

  await screen.findByRole('heading', { name: 'Top stories' });
  expect(container.querySelector('img, iframe, svg')).toBeNull();
  expect(screen.queryByRole('button')).toBeNull();
});
