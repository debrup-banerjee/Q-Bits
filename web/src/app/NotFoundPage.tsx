import { Link } from 'react-router';
import { usePageTitle } from '../hooks/usePageTitle';
import { pageTitles } from './page-titles';

export function NotFoundPage() {
  usePageTitle(pageTitles.notFound);
  return (
    <section className="card mx-auto max-w-xl px-6 py-14 text-center">
      <h1 className="text-2xl font-semibold">We couldn't find that page.</h1>
      <p className="mt-2 text-muted">It may have moved, or the story is older than 72 hours.</p>
      <Link to="/" className="btn-primary mt-6">
        Back to the latest AI news{' '}
        <span aria-hidden="true" className="btn-arrow">
          →
        </span>
      </Link>
    </section>
  );
}
