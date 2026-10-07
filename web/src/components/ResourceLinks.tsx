import type { ResourceLink } from '@qbits/api-client';

const ICON: Record<string, string> = { code: '</>', model: '◆', dataset: '▦', paper: '¶' };

/**
 * Verified open-source links for a story: code, model, dataset or paper (spec 005 R6). Hidden when
 * there are none. Links only ever come from the publisher's feed and are checked by the backend.
 */
export function ResourceLinks({ resources }: { resources: ResourceLink[] }) {
  if (resources.length === 0) {
    return null;
  }
  return (
    <div className="flex min-w-0 flex-col gap-1.5" role="group" aria-label="Open source">
      <span className="text-xs font-semibold uppercase tracking-wide text-muted">Open source</span>
      <ul className="flex min-w-0 flex-wrap gap-2">
        {resources.map((r) => (
          <li key={r.url} className="min-w-0 max-w-full">
            <a
              href={r.url}
              target="_blank"
              rel="noopener noreferrer"
              aria-label={`${r.label}: ${r.name} (opens in a new tab)`}
              className="flex min-w-0 max-w-full items-center gap-1.5 rounded-full border border-line bg-surface px-3 py-1 text-sm transition-colors hover:border-accent hover:bg-chip"
            >
              <span aria-hidden="true" className="shrink-0 text-muted">
                {ICON[r.type] ?? '↗'}
              </span>
              <span className="shrink-0 whitespace-nowrap font-medium">{r.label}</span>
              <code title={r.name} className="min-w-0 truncate font-mono text-xs text-muted">
                {r.name}
              </code>
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
}
