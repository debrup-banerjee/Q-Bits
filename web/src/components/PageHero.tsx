import type { ReactNode } from 'react';

/**
 * The gradient header at the top of a page: an optional eyebrow label, the page's h1 and a short
 * line under it. Styles live in index.css (.page-hero) so the server-filled page matches.
 */
export function PageHero({
  title,
  eyebrow,
  children,
}: {
  title: string;
  eyebrow?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <div className="page-hero">
      {eyebrow && <p className="hero-eyebrow">{eyebrow}</p>}
      <h1>{title || ' '}</h1>
      {children && <div className="hero-sub">{children}</div>}
    </div>
  );
}
