import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { story } from '../../test/fixtures';
import { renderAt } from '../../test/render';
import { FeedCard } from './FeedCard';

const NOW = new Date('2026-10-03T04:12:00Z');

// 004 R4.1
it('shows the parts in timeline order', () => {
  renderAt(<FeedCard story={story()} now={NOW} />);

  const card = screen.getByRole('article');
  const text = card.textContent ?? '';
  const order = ['T', 'The Hindu', '12 minutes ago', 'India AI', story().headline].map((part) =>
    text.indexOf(part),
  );
  expect(order).toEqual([...order].sort((a, b) => a - b));
  expect(within(card).getByRole('link', { name: 'India AI' })).toHaveAttribute(
    'href',
    '/section/india-ai',
  );
  expect(within(card).getByRole('link', { name: story().headline })).toHaveAttribute(
    'href',
    `/story/${story().id}`,
  );
});

// 004 R4.2
it('clamps the summary and expands it with the words to know', async () => {
  renderAt(<FeedCard story={story()} now={NOW} />);
  const button = screen.getByRole('button', { name: 'Show more' });

  expect(screen.getByTestId('feed-summary')).toHaveClass('line-clamp-3');
  expect(button).toHaveAttribute('aria-expanded', 'false');
  expect(screen.queryByText('Words to know')).not.toBeInTheDocument();

  await userEvent.click(button);

  expect(screen.getByTestId('feed-summary')).not.toHaveClass('line-clamp-3');
  expect(screen.getByRole('button', { name: 'Show less' })).toHaveAttribute(
    'aria-expanded',
    'true',
  );
  expect(screen.getByText(/A chip that does many small sums at once/)).toBeVisible();

  await userEvent.click(screen.getByRole('button', { name: 'Show less' }));
  expect(screen.getByTestId('feed-summary')).toHaveClass('line-clamp-3');
});

// 004 R4.3
it('links out safely and shows the attribution', () => {
  renderAt(<FeedCard story={story()} now={NOW} />);

  const out = screen.getByRole('link', {
    name: 'Read the full story at The Hindu (opens in a new tab)',
  });
  expect(out).toHaveAttribute('target', '_blank');
  expect(out).toHaveAttribute('rel', 'noopener noreferrer');
  expect(
    screen.getByText("Summary written from The Hindu's headline and teaser"),
  ).toBeInTheDocument();
});

it('shows open-source links without expanding the card', () => {
  renderAt(
    <FeedCard
      story={story({
        resources: [
          {
            type: 'model',
            label: 'Model on Hugging Face',
            url: 'https://huggingface.co/org/m',
            name: 'org/m',
          },
        ],
      })}
      now={NOW}
    />,
  );

  expect(screen.getByRole('button', { name: 'Show more' })).toHaveAttribute(
    'aria-expanded',
    'false',
  );
  expect(
    screen.getByRole('link', { name: 'Model on Hugging Face: org/m (opens in a new tab)' }),
  ).toBeVisible();
});

it('shows no open-source row when there are no links', () => {
  renderAt(<FeedCard story={story()} now={NOW} />);
  expect(screen.queryByLabelText('Open source')).not.toBeInTheDocument();
});

// 004 R4.4, 009 R1
it('has no embeds or social buttons, and draws cover art when there is no picture', () => {
  const { container } = renderAt(<FeedCard story={story()} now={NOW} />);

  expect(container.querySelector('img, iframe, video, embed, object')).toBeNull();
  expect(screen.getByTestId('cover-art')).toHaveAttribute('aria-hidden', 'true');
  for (const name of [/like/i, /share/i, /comment/i, /follow/i]) {
    expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
  }
});

// 009 R5.1, R5.2
it('shows a photo with the photographer and library credited', () => {
  renderAt(
    <FeedCard
      story={story({
        image: {
          kind: 'photo',
          url: 'https://images.pexels.com/p',
          alt: 'a computer chip',
          credit: 'Asha Rao',
          creditUrl: 'https://www.pexels.com/@asha',
          provider: 'Pexels',
          providerUrl: 'https://www.pexels.com',
          color: '#112233',
        },
      })}
      now={NOW}
    />,
  );

  expect(screen.getByRole('img', { name: 'a computer chip' })).toHaveAttribute(
    'src',
    'https://images.pexels.com/p',
  );
  expect(screen.getByText(/Illustrative photo by/)).toHaveTextContent(
    'Illustrative photo by Asha Rao on Pexels',
  );
  expect(screen.getByRole('link', { name: 'Asha Rao' })).toHaveAttribute(
    'href',
    'https://www.pexels.com/@asha',
  );
  expect(screen.queryByTestId('cover-art')).not.toBeInTheDocument();
});

// 009 R3.3
it('credits a publisher image to the publisher', () => {
  renderAt(
    <FeedCard
      story={story({
        image: {
          kind: 'publisher',
          url: 'https://lab.example/img.png',
          alt: 'Image supplied by Lab',
          credit: 'Lab press kit',
          creditUrl: null,
          provider: 'Lab',
          providerUrl: 'https://lab.example/',
          color: null,
        },
      })}
      now={NOW}
    />,
  );

  expect(screen.getByText(/^Image:/)).toHaveTextContent('Image: Lab press kit');
});
