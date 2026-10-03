import { useLatestFeed } from '@qbits/api-client';
import { useEffect, useRef } from 'react';
import { Link } from 'react-router';
import { ErrorState } from '../../components/ErrorState';
import { RelativeTime } from '../../components/RelativeTime';
import { useNow } from '../../hooks/useNow';
import { FeedCard } from './FeedCard';
import { FeedSkeleton } from './FeedSkeleton';

/** AI Latest: every story from the last 24 hours, newest first, as a timeline (spec 004 R3). */
export function LatestPage() {
  const feed = useLatestFeed();
  const now = useNow();
  const sentinel = useRef<HTMLDivElement>(null);
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = feed;
  const firstPage = feed.data?.pages[0];
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
          Every AI story from the last 24 hours, newest first
          {firstPage?.dataAsOf && (
            <>
              {' · Updated '}
              <RelativeTime iso={firstPage.dataAsOf} now={now} />
            </>
          )}
        </p>
      </div>

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
