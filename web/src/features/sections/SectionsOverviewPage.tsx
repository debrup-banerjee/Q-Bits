import { useSections, useStories, type SectionView } from '@qbits/api-client';
import { Link } from 'react-router';
import { pageTitles } from '../../app/page-titles';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import { PageHero } from '../../components/PageHero';
import { RelativeTime } from '../../components/RelativeTime';
import { SkeletonList } from '../../components/SkeletonCard';
import { useNow } from '../../hooks/useNow';
import { usePageTitle } from '../../hooks/usePageTitle';
import { StoryCard } from '../stories/StoryCard';
import { sectionDot } from './sectionTheme';

const PER_SECTION = 5;
const GRID = 'grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3';

/** Browse by section: the five sections, each with its newest stories (003 R4, moved by 004 R2.3). */
export function SectionsOverviewPage() {
  const sections = useSections();
  const now = useNow();
  usePageTitle(pageTitles.sections);

  return (
    <div className="flex flex-col gap-10">
      <PageHero title="Browse by section" eyebrow="Last 72 hours">
        {/* 003 R4.2: the 72-hour line always shows; "Updated …" joins it once data has loaded. */}
        <p>
          AI news from the last 72 hours, sorted into five sections.
          {sections.data?.dataAsOf && (
            <>
              {' '}
              <span>
                Updated <RelativeTime iso={sections.data.dataAsOf} now={now} />
              </span>
            </>
          )}
        </p>
      </PageHero>

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
      <div className="flex flex-wrap items-end justify-between gap-3 border-b border-line pb-3">
        <div className="flex items-start gap-2.5">
          <span
            aria-hidden="true"
            className={`mt-2 h-3 w-3 shrink-0 rounded-full ${sectionDot(section.slug)}`}
          />
          <div>
            <h2 id={headingId} className="text-xl font-bold sm:text-2xl">
              {section.name}
            </h2>
            <p className="text-sm text-muted">{section.description}</p>
          </div>
        </div>
        <Link to={`/section/${section.slug}`} className="link-arrow text-sm">
          See all {section.name}{' '}
          <span aria-hidden="true" className="btn-arrow">
            →
          </span>
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
