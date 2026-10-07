import type { KeyTerm } from '@qbits/api-client';
import { useState } from 'react';

const WIDE = '(min-width: 640px)';

function startsOpen(): boolean {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia(WIDE).matches
    : false;
}

/**
 * Key terms with plain meanings. Collapsed to the term names on phones, open on wider screens,
 * one tap to expand (spec 003 R6.2).
 */
export function WordsToKnow({ terms, defaultOpen }: { terms: KeyTerm[]; defaultOpen?: boolean }) {
  const [open, setOpen] = useState(() => defaultOpen ?? startsOpen());
  if (terms.length === 0) {
    return null;
  }
  return (
    <details
      open={open}
      onToggle={(e) => setOpen(e.currentTarget.open)}
      className="rounded-xl border border-line bg-chip/60 px-3.5 py-2.5"
    >
      <summary className="cursor-pointer text-sm font-semibold">
        Words to know
        {!open && (
          <span className="font-normal text-muted">: {terms.map((t) => t.term).join(', ')}</span>
        )}
      </summary>
      <dl className="mt-2 space-y-1.5 text-sm">
        {terms.map((t) => (
          <div key={t.term}>
            <dt className="inline font-semibold">{t.term}</dt>
            <dd className="inline text-muted"> — {t.meaning}</dd>
          </div>
        ))}
      </dl>
    </details>
  );
}
