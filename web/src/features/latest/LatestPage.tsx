import { useEdition, useLatestFeed } from '@qbits/api-client';
import { useEffect, useRef } from 'react';
import { Link } from 'react-router';
import { pageTitles } from '../../app/page-titles';
import { ErrorState } from '../../components/ErrorState';
import { PageHero } from '../../components/PageHero';
import { useNow } from '../../hooks/useNow';
import { usePageTitle } from '../../hooks/usePageTitle';
import { FeedCard } from './FeedCard';
import { FeedSkeleton } from './FeedSkeleton';
import { editionLabel, nextEditionText, timeOf } from './editionText';

/** AI Latest: today's daily edition, newest first, as a timeline (spec 004 R3, 006 R5). */
export function LatestPage() {
  const feed = useLatestFeed();
  const edition = useEdition().data;
  const now = useNow();
  const sentinel = useRef<HTMLDivElement>(null);
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = feed;
  usePageTitle(pageTitles.home);
  const items = feed.data?.pages.flatMap((p) => p.data.data) ?? [];

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

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-5">
      <PageHero title="AI Latest" eyebrow="Daily digest">
        <p>
          {edition ? (
            <>
              {editionLabel(edition.data.publishedAt, now)} · published{' '}
              {timeOf(edition.data.publishedAt)}
              <span className="block sm:inline">
                <span className="hidden sm:inline"> · </span>
                {nextEditionText(edition.data.nextCutoffAt, now)}
              </span>
            </>
          ) : (
            'Every AI story from the last 24 hours, newest first'
          )}
        </p>
      </PageHero>

      {edition?.data.late && (
        <p role="status" className="card bg-chip px-4 py-3 text-sm font-medium">
          Today&apos;s edition is running late. Here is the last one.
        </p>
      )}

      {feed.isError && <ErrorState onRetry={() => void feed.refetch()} />}
      {feed.isPending && (
        <div className="card px-4 sm:px-6">
          <FeedSkeleton />
        </div>
      )}
      {feed.isSuccess && items.length === 0 && (
        <div className="rounded-2xl border border-dashed border-line bg-surface/60 p-8 text-center text-muted">
          <p>No AI news in the last 24 hours yet. Check back soon.</p>
          <Link to="/sections" className="btn-secondary mt-4">
            Browse by section
          </Link>
        </div>
      )}

      {items.length > 0 && (
        <div className="card overflow-hidden px-4 sm:px-6">
          {items.map((s) => (
            <FeedCard key={s.id} story={s} now={now} />
          ))}
          {isFetchingNextPage && <FeedSkeleton count={1} />}
        </div>
      )}
      {hasNextPage && !isFetchingNextPage && (
        <div ref={sentinel} className="flex justify-center py-2">
          <button type="button" onClick={() => void fetchNextPage()} className="btn-secondary">
            Load more
          </button>
        </div>
      )}
      {feed.isSuccess && items.length > 0 && !hasNextPage && (
        <p className="py-4 text-center text-sm text-muted">
          That&apos;s everything from the last 24 hours.{' '}
          <Link to="/sections" className="link-arrow">
            Browse by section{' '}
            <span aria-hidden="true" className="btn-arrow">
              →
            </span>
          </Link>
        </p>
      )}
    </div>
  );
}
