import { useSections } from '@qbits/api-client';
import { NavLink } from 'react-router';
import { sectionDot, sectionPill } from './sectionTheme';

const base =
  'inline-flex items-center whitespace-nowrap rounded-full px-3 py-1.5 text-sm font-semibold transition-colors sm:px-3.5';
const idle = 'text-muted hover:bg-chip hover:text-ink';
const active = 'bg-ink text-surface shadow-sm';

/**
 * Tabs: AI Latest first (a fixed label; spec 004 R2.1), then the sections from the API.
 * Section navigation. A sticky, scrollable tab bar on phones; an inline bar on wider screens
 * (spec 003 R4.3). Names come from the API, never hard-coded. Each section keeps its own colour
 * when active, so the current tab is easy to spot at a glance (conventions: colourful but
 * compliant -- colour marks the active tab, never changes idle-state text contrast).
 */
export function SectionNav() {
  const sections = useSections();
  return (
    <nav
      aria-label="Sections"
      className="site-header sticky top-0 z-10 sm:static sm:border-0 sm:bg-transparent"
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
              className={({ isActive }) =>
                `${base} flex items-center gap-1.5 ${isActive ? `${sectionPill(s.slug)} shadow-sm` : idle}`
              }
            >
              {({ isActive }) =>
                isActive ? (
                  s.name
                ) : (
                  <>
                    <span
                      aria-hidden="true"
                      className={`h-2 w-2 rounded-full ${sectionDot(s.slug)}`}
                    />
                    {s.name}
                  </>
                )
              }
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
