import { useSections, useStories } from '@qbits/api-client';
import { useEffect, useRef } from 'react';
import { useParams } from 'react-router';
import { NotFoundPage } from '../../app/NotFoundPage';
import { pageTitles } from '../../app/page-titles';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import { PageHero } from '../../components/PageHero';
import { SkeletonList } from '../../components/SkeletonCard';
import { useNow } from '../../hooks/useNow';
import { usePageTitle } from '../../hooks/usePageTitle';
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
  usePageTitle(section ? pageTitles.section(section.name) : undefined);

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
    <div className="mx-auto flex max-w-3xl flex-col gap-5">
      <PageHero title={name} eyebrow="Last 72 hours">
        {section && <p>{section.description}</p>}
      </PageHero>
      {stories.isError && <ErrorState onRetry={() => void stories.refetch()} />}
      {stories.isPending && <SkeletonList count={3} />}
      {stories.isSuccess && items.length === 0 && name && <EmptyState sectionName={name} />}
      {items.map((s) => (
        <StoryCard key={s.id} story={s} now={now} headingLevel={2} />
      ))}
      {isFetchingNextPage && <SkeletonList count={1} />}
      {hasNextPage && !isFetchingNextPage && (
        <div ref={sentinel} className="flex justify-center py-4">
          <button type="button" onClick={() => void fetchNextPage()} className="btn-secondary">
            Load more
          </button>
        </div>
      )}
    </div>
  );
}
