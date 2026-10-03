/** Timeline-shaped placeholders while the feed loads (spec 004 R3.4). */
export function FeedSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div role="status" aria-label="Loading news">
      {Array.from({ length: count }, (_, i) => (
        <div
          key={i}
          aria-hidden="true"
          data-testid="feed-skeleton"
          className="flex animate-pulse gap-3 border-b border-line px-1 py-4"
        >
          <div className="h-10 w-10 shrink-0 rounded-full bg-line" />
          <div className="flex flex-1 flex-col gap-2">
            <div className="h-3 w-1/2 rounded bg-line" />
            <div className="h-4 w-5/6 rounded bg-line" />
            <div className="h-3 w-full rounded bg-line" />
            <div className="h-3 w-2/3 rounded bg-line" />
          </div>
        </div>
      ))}
    </div>
  );
}
