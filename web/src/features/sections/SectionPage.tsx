import { useSections, useStories } from '@qbits/api-client';
import { useEffect, useRef } from 'react';
import { useParams } from 'react-router';
import { NotFoundPage } from '../../app/NotFoundPage';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import { SkeletonList } from '../../components/SkeletonCard';
import { useNow } from '../../hooks/useNow';
import { StoryCard } from '../stories/StoryCard';

/** All stories of one section from the last 72 hours, loading more on scroll (spec 003 R5). */
export function SectionPage() {
  const { slug = '' } = useParams();
  const sections = useSections();
  const section = sections.data?.data.find((s) => s.slug === slug);
  const stories = useStories(slug, 20);
  const now = useNow();
  const sentinel = useRef<HTMLDivElement>(null);
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = stories;

  useEffect(() => {
    const el = sentinel.current;
    if (!el || !hasNextPage || typeof IntersectionObserver === 'undefined') {
      return;
    }
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting) && !isFetchingNextPage) {
        void fetchNextPage();
      }
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  if (sections.isSuccess && !section) {
    return <NotFoundPage />;
  }
  const items = stories.data?.pages.flatMap((p) => p.data.data) ?? [];
  const name = section?.name ?? '';

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-4">
      <div>
        <h1 className="text-2xl font-bold sm:text-3xl">{name || ' '}</h1>
        {section && <p className="mt-1 text-muted">{section.description}</p>}
      </div>
      {stories.isError && <ErrorState onRetry={() => void stories.refetch()} />}
      {stories.isPending && <SkeletonList count={3} />}
      {stories.isSuccess && items.length === 0 && name && <EmptyState sectionName={name} />}
      {items.map((s) => (
        <StoryCard key={s.id} story={s} now={now} headingLevel={2} />
      ))}
      {isFetchingNextPage && <SkeletonList count={1} />}
      {hasNextPage && !isFetchingNextPage && (
        <div ref={sentinel} className="flex justify-center py-4">
          <button
            type="button"
            onClick={() => void fetchNextPage()}
            className="rounded-lg border border-line px-4 py-2 text-sm font-semibold hover:bg-chip"
          >
            Load more
          </button>
        </div>
      )}
    </div>
  );
}
