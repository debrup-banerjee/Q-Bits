import { Link, Outlet } from 'react-router';
import { SectionNav } from '../features/sections/SectionNav';

/** Header, section navigation, page content and footer. */
export function AppShell() {
  return (
    <div className="flex min-h-dvh flex-col">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-20 focus:bg-surface focus:px-3 focus:py-2"
      >
        Skip to news
      </a>
      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex max-w-6xl flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
          <Link to="/" className="flex items-baseline gap-2 no-underline">
            <span className="text-2xl font-bold tracking-tight text-ink">Q-Bits</span>
            <span className="text-sm text-muted">AI news, in plain words</span>
          </Link>
          <div className="hidden sm:block">
            <SectionNav />
          </div>
        </div>
      </header>
      {/* The wrapper is what sticks: a sticky child only sticks inside its own parent. */}
      <div className="sticky top-0 z-20 sm:hidden">
        <SectionNav />
      </div>
      <main id="main" className="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
        <Outlet />
      </main>
      <footer className="border-t border-line bg-surface">
        <div className="mx-auto flex max-w-6xl flex-col gap-2 px-4 py-6 text-sm text-muted sm:flex-row sm:justify-between">
          <p>Every story links to its original publisher.</p>
          <div className="flex gap-4">
            <Link
              to="/sections"
              className="font-medium text-accent underline-offset-4 hover:underline"
            >
              Browse by section
            </Link>
            <Link
              to="/about"
              className="font-medium text-accent underline-offset-4 hover:underline"
            >
              How Q-Bits works
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
