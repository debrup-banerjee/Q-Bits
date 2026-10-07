/** Plain error with a retry. Never shows technical details (spec 003 R8.2). */
export function ErrorState({ onRetry }: { onRetry: () => void }) {
  return (
    <div role="alert" className="card p-6 text-center">
      <p className="font-medium">We couldn&apos;t load the news. Try again.</p>
      <button type="button" onClick={onRetry} className="btn-primary mt-4">
        Try again
      </button>
    </div>
  );
}
