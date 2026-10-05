import { ApiError, useStory } from '@qbits/api-client';
import { Link, useParams } from 'react-router';
import { pageTitles } from '../../app/page-titles';
import { ErrorState } from '../../components/ErrorState';
import { SkeletonList } from '../../components/SkeletonCard';
import { usePageTitle } from '../../hooks/usePageTitle';
import { StoryCard } from './StoryCard';

/** A single, shareable story (spec 003 R6.6). */
export function StoryPage() {
  const { id = '' } = useParams();
  const story = useStory(id);
  const gone =
    story.error instanceof ApiError && (story.error.status === 404 || story.error.status === 400);
  usePageTitle(
    gone
      ? pageTitles.storyNotFound
      : story.data
        ? pageTitles.story(story.data.data.headline)
        : undefined,
  );

  if (gone) {
    return (
      <section className="py-16 text-center">
        <h1 className="text-2xl font-semibold">This story isn&apos;t available.</h1>
        <p className="mt-2 text-muted">Q-Bits only keeps stories from the last 72 hours.</p>
        <Link to="/" className="mt-6 inline-block font-medium text-accent hover:underline">
          See the latest AI news
        </Link>
      </section>
    );
  }
  return (
    <div className="mx-auto max-w-3xl">
      {story.isError && <ErrorState onRetry={() => void story.refetch()} />}
      {story.isPending && <SkeletonList count={1} />}
      {story.data && <StoryCard story={story.data.data} headingLevel={1} />}
    </div>
  );
}
