import { useSections } from '@qbits/api-client';
import { NavLink } from 'react-router';

const base =
  'whitespace-nowrap rounded-full px-3 py-1.5 text-sm font-medium transition-colors sm:px-3.5';
const idle = 'text-muted hover:bg-chip hover:text-ink';
const active = 'bg-accent text-accent-ink';

/**
 * Tabs: AI Latest first (a fixed label; spec 004 R2.1), then the sections from the API.
 * Section navigation. A sticky, scrollable tab bar on phones; an inline bar on wider screens
 * (spec 003 R4.3). Names come from the API, never hard-coded.
 */
export function SectionNav() {
  const sections = useSections();
  return (
    <nav
      aria-label="Sections"
      className="sticky top-0 z-10 border-b border-line bg-page/95 backdrop-blur sm:static sm:border-0 sm:bg-transparent"
    >
      <ul className="mx-auto flex max-w-6xl gap-1 overflow-x-auto px-4 py-2 sm:flex-wrap sm:overflow-visible sm:px-0 sm:py-0">
        <li>
          <NavLink to="/" end className={({ isActive }) => `${base} ${isActive ? active : idle}`}>
            AI Latest
          </NavLink>
        </li>
        {sections.data?.data.map((s) => (
          <li key={s.slug}>
            <NavLink
              to={`/section/${s.slug}`}
              className={({ isActive }) => `${base} ${isActive ? active : idle}`}
            >
              {s.name}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
