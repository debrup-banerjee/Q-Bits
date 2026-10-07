import { Link, Outlet } from 'react-router';
import { useAuth } from '../features/auth/AuthContext';
import { SectionNav } from '../features/sections/SectionNav';

/** Header, section navigation, page content and footer. */
export function AppShell() {
  const { username, isLoggedIn, logout } = useAuth();
  return (
    <div className="relative isolate flex min-h-dvh flex-col">
      <div aria-hidden="true" className="page-backdrop" />
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-30 focus:bg-surface focus:px-3 focus:py-2"
      >
        Skip to news
      </a>
      <header className="site-header z-20 sm:sticky sm:top-0">
        <div aria-hidden="true" className="brand-line" />
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-3">
          <Link to="/" className="flex items-center gap-2.5 no-underline">
            <span aria-hidden="true" className="brand-mark">
              Q
            </span>
            <span className="text-xl font-bold tracking-tight text-ink">Q-Bits</span>
            <span className="hidden text-sm font-medium text-muted min-[400px]:inline">
              Unlocking AI
            </span>
          </Link>
          <div className="flex flex-wrap items-center gap-3 sm:gap-4">
            <div className="hidden sm:block">
              <SectionNav />
            </div>
            {isLoggedIn ? (
              <div className="flex items-center gap-2 text-sm">
                <span className="text-muted">Hi, {username}</span>
                <button type="button" onClick={logout} className="btn-secondary">
                  Log out
                </button>
              </div>
            ) : (
              <Link to="/login" className="btn-secondary">
                Log in{' '}
                <span aria-hidden="true" className="btn-arrow">
                  →
                </span>
              </Link>
            )}
          </div>
        </div>
      </header>
      {/* The wrapper is what sticks: a sticky child only sticks inside its own parent. */}
      <div className="sticky top-0 z-20 sm:hidden">
        <SectionNav />
      </div>
      <main id="main" className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:py-10">
        <Outlet />
      </main>
      <footer className="mt-10 border-t border-line bg-surface">
        <div className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-10 sm:flex-row sm:justify-between">
          <div className="flex max-w-sm flex-col gap-2">
            <span className="flex items-center gap-2.5">
              <span aria-hidden="true" className="brand-mark">
                Q
              </span>
              <span className="text-lg font-bold tracking-tight">Q-Bits</span>
            </span>
            <p className="text-sm text-muted">
              Plain-language AI news from the last three days. Every story links to its original
              publisher.
            </p>
          </div>
          <nav aria-label="Footer" className="flex flex-col gap-2 text-sm">
            <span className="text-xs font-semibold uppercase tracking-wider text-muted">
              Explore
            </span>
            <Link to="/sections" className="link-arrow">
              Browse by section{' '}
              <span aria-hidden="true" className="btn-arrow">
                →
              </span>
            </Link>
            <Link to="/about" className="link-arrow">
              How Q-Bits works{' '}
              <span aria-hidden="true" className="btn-arrow">
                →
              </span>
            </Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}
