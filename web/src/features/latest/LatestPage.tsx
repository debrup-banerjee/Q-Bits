import { useEdition, useLatestFeed } from '@qbits/api-client';
import { useEffect, useRef } from 'react';
import { Link } from 'react-router';
import { ErrorState } from '../../components/ErrorState';
import { useNow } from '../../hooks/useNow';
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
    <div className="mx-auto flex max-w-xl flex-col">
      <div className="pb-2">
        <h1 className="text-2xl font-bold sm:text-3xl">AI Latest</h1>
        <p className="mt-1 text-sm text-muted">
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
      </div>

      {edition?.data.late && (
        <p
          role="status"
          className="mb-2 rounded-lg border border-line bg-chip px-3 py-2 text-sm font-medium"
        >
          Today&apos;s edition is running late. Here is the last one.
        </p>
      )}

      {feed.isError && <ErrorState onRetry={() => void feed.refetch()} />}
      {feed.isPending && <FeedSkeleton />}
      {feed.isSuccess && items.length === 0 && (
        <div className="rounded-xl border border-dashed border-line p-6 text-center text-muted">
          <p>No AI news in the last 24 hours yet. Check back soon.</p>
          <Link
            to="/sections"
            className="mt-3 inline-block font-semibold text-accent hover:underline"
          >
            Browse by section
          </Link>
        </div>
      )}

      <div>
        {items.map((s) => (
          <FeedCard key={s.id} story={s} now={now} />
        ))}
      </div>

      {isFetchingNextPage && <FeedSkeleton count={1} />}
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
      {feed.isSuccess && items.length > 0 && !hasNextPage && (
        <p className="py-6 text-center text-sm text-muted">
          That&apos;s everything from the last 24 hours.{' '}
          <Link to="/sections" className="font-semibold text-accent hover:underline">
            Browse by section
          </Link>
        </p>
      )}
    </div>
  );
}
