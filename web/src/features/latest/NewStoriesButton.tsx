import { useNewStories, type StoryPage, type WithAsOf } from '@qbits/api-client';
import { useState } from 'react';

function label(count: number, more: boolean): string {
  if (more) return `${count}+ new stories`;
  return count === 1 ? '1 new story' : `${count} new stories`;
}

/**
 * Shows "N new stories" when newer stories exist, without changing the feed underneath
 * (spec 004 R5). Mounted only once the feed's first page has loaded.
 */
export function NewStoriesButton({
  firstPage,
  loadedAt,
}: {
  firstPage: WithAsOf<StoryPage>;
  loadedAt: number;
}) {
  const { count, more, apply } = useNewStories(firstPage, loadedAt);
  const [busy, setBusy] = useState(false);

  async function onClick() {
    setBusy(true);
    await apply();
    setBusy(false);
    if (typeof window.scrollTo === 'function') {
      try {
        window.scrollTo({ top: 0, behavior: 'smooth' });
      } catch {
        // jsdom and very old browsers
      }
    }
  }

  return (
    <div aria-live="polite" className="sticky top-14 z-10 flex justify-center sm:top-2">
      {count > 0 && (
        <button
          type="button"
          onClick={() => void onClick()}
          disabled={busy}
          className="my-2 rounded-full bg-accent px-4 py-2 text-sm font-semibold text-accent-ink shadow-md hover:opacity-90"
        >
          <span aria-hidden="true">↑ </span>
          {label(count, more)}
        </button>
      )}
    </div>
  );
}
