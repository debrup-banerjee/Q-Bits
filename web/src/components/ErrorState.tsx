/** Plain error with a retry. Never shows technical details (spec 003 R8.2). */
export function ErrorState({ onRetry }: { onRetry: () => void }) {
  return (
    <div role="alert" className="rounded-xl border border-line bg-surface p-6 text-center">
      <p className="font-medium">We couldn&apos;t load the news. Try again.</p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-4 rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-accent-ink hover:opacity-90"
      >
        Try again
      </button>
    </div>
  );
}
