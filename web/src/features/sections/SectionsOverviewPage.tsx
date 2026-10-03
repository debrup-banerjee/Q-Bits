import { useSections, useStories, type SectionView } from '@qbits/api-client';
import { Link } from 'react-router';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import { RelativeTime } from '../../components/RelativeTime';
import { SkeletonList } from '../../components/SkeletonCard';
import { useNow } from '../../hooks/useNow';
import { StoryCard } from '../stories/StoryCard';

const PER_SECTION = 5;
const GRID = 'grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3';

/** Browse by section: the four sections, each with its newest stories (003 R4, moved by 004 R2.3). */
export function SectionsOverviewPage() {
  const sections = useSections();
  const now = useNow();

  return (
    <div className="flex flex-col gap-10">
      <div>
        <h1 className="text-2xl font-bold sm:text-3xl">Browse by section</h1>
        <p className="mt-1 text-sm text-muted">
          {sections.data?.dataAsOf ? (
            <>
              Updated <RelativeTime iso={sections.data.dataAsOf} now={now} />
            </>
          ) : (
            'AI news from the last 72 hours, sorted into four sections.'
          )}
        </p>
      </div>

      {sections.isError && <ErrorState onRetry={() => void sections.refetch()} />}
      {sections.isPending && (
        <div className={GRID}>
          <SkeletonList count={3} />
        </div>
      )}
      {sections.data?.data.map((section) => (
        <SectionBlock key={section.slug} section={section} now={now} />
      ))}
    </div>
  );
}

function SectionBlock({ section, now }: { section: SectionView; now: Date }) {
  const stories = useStories(section.slug, PER_SECTION);
  const items = stories.data?.pages[0]?.data.data ?? [];
  const headingId = `section-${section.slug}`;

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end justify-between gap-2 border-b border-line pb-2">
        <div>
          <h2 id={headingId} className="text-xl font-bold">
            {section.name}
          </h2>
          <p className="text-sm text-muted">{section.description}</p>
        </div>
        <Link
          to={`/section/${section.slug}`}
          className="text-sm font-semibold text-accent hover:underline"
        >
          See all {section.name}
        </Link>
      </div>
      {stories.isError && <ErrorState onRetry={() => void stories.refetch()} />}
      {stories.isPending && (
        <div className={GRID}>
          <SkeletonList count={3} />
        </div>
      )}
      {stories.isSuccess && items.length === 0 && <EmptyState sectionName={section.name} />}
      {items.length > 0 && (
        <div className={GRID}>
          {items.map((s) => (
            <StoryCard key={s.id} story={s} now={now} />
          ))}
        </div>
      )}
    </section>
  );
}
