/** Placeholder card shown while stories load (spec 003 R8.1). */
export function SkeletonCard() {
  return (
    <div
      aria-hidden="true"
      data-testid="skeleton-card"
      className="flex animate-pulse flex-col gap-3 rounded-xl border border-line bg-surface p-4 sm:p-5"
    >
      <div className="h-3 w-1/3 rounded bg-line" />
      <div className="h-5 w-5/6 rounded bg-line" />
      <div className="h-3 w-full rounded bg-line" />
      <div className="h-3 w-full rounded bg-line" />
      <div className="h-3 w-2/3 rounded bg-line" />
      <div className="h-8 w-48 rounded-lg bg-line" />
    </div>
  );
}

export function SkeletonList({ count = 3 }: { count?: number }) {
  return (
    <div role="status" aria-label="Loading news" className="contents">
      {Array.from({ length: count }, (_, i) => (
        <SkeletonCard key={i} />
      ))}
    </div>
  );
}
