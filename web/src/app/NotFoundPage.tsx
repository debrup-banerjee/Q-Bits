import { Link } from 'react-router';
import { usePageTitle } from '../hooks/usePageTitle';
import { pageTitles } from './page-titles';

export function NotFoundPage() {
  usePageTitle(pageTitles.notFound);
  return (
    <section className="py-16 text-center">
      <h1 className="text-2xl font-semibold">We couldn't find that page.</h1>
      <p className="mt-2 text-muted">It may have moved, or the story is older than 72 hours.</p>
      <Link to="/" className="mt-6 inline-block font-medium text-accent hover:underline">
        Back to the latest AI news
      </Link>
    </section>
  );
}
