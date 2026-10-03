/**
 * The link out to the publisher. Always opens a new tab safely (spec 003 R6.3). Principles: the
 * app never shows article content itself.
 */
export function ExternalLink({ href, sourceName }: { href: string; sourceName: string }) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      aria-label={`Read the full story at ${sourceName} (opens in a new tab)`}
      className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-accent-ink shadow-sm hover:opacity-90"
    >
      Read the full story at {sourceName}
      <span aria-hidden="true">↗</span>
    </a>
  );
}
