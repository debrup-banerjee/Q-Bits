import { screen } from '@testing-library/react';
import { story } from '../../test/fixtures';
import { renderAt } from '../../test/render';
import { StoryCard } from './StoryCard';

const NOW = new Date('2026-10-03T09:00:00Z');

// 003 R6.1, R6.4, R6.5
it('shows every part of the story', () => {
  const { container } = renderAt(<StoryCard story={story()} now={NOW} />);

  expect(screen.getByRole('heading', { name: story().headline })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'India AI' })).toHaveAttribute(
    'href',
    '/section/india-ai',
  );
  expect(screen.getByText('The Hindu')).toBeInTheDocument();
  expect(screen.getByText('5 hours ago')).toBeInTheDocument();
  expect(screen.getByText(story().summary)).toBeInTheDocument();
  expect(
    screen.getByText("Summary written from The Hindu's headline and teaser"),
  ).toBeInTheDocument();
  expect(container.querySelector('img, iframe, video, embed, object')).toBeNull();
});

// 003 R6.3
it('links out safely to the publisher', () => {
  renderAt(<StoryCard story={story()} now={NOW} />);

  const link = screen.getByRole('link', {
    name: 'Read the full story at The Hindu (opens in a new tab)',
  });
  expect(link).toHaveAttribute('href', 'https://www.thehindu.com/sci-tech/ai-hub');
  expect(link).toHaveAttribute('target', '_blank');
  expect(link).toHaveAttribute('rel', 'noopener noreferrer');
});

it('shows open-source links when present', () => {
  renderAt(
    <StoryCard
      story={story({
        resources: [
          { type: 'code', label: 'Code on GitHub', url: 'https://github.com/a/b', name: 'a/b' },
        ],
      })}
      now={NOW}
    />,
  );

  expect(
    screen.getByRole('link', { name: 'Code on GitHub: a/b (opens in a new tab)' }),
  ).toBeInTheDocument();
});

// 003 R6.1
it('says "about" when the publish date was estimated', () => {
  renderAt(<StoryCard story={story({ dateEstimated: true })} now={NOW} />);

  expect(screen.getByText('about 5 hours ago')).toBeInTheDocument();
});

// 003 R6.6
it('links the headline to the shareable story page', () => {
  renderAt(<StoryCard story={story()} now={NOW} />);

  expect(screen.getByRole('link', { name: story().headline })).toHaveAttribute(
    'href',
    `/story/${story().id}`,
  );
});
