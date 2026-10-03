import type { StoryView } from '@qbits/api-client';
import { Link } from 'react-router';
import { ExternalLink } from '../../components/ExternalLink';
import { RelativeTime } from '../../components/RelativeTime';
import { WordsToKnow } from './WordsToKnow';

/**
 * One story: headline, section, source and time, our summary, words to know, the link out and the
 * attribution line. No images or embedded content (spec 003 R6).
 */
export function StoryCard({
  story,
  now,
  headingLevel = 3,
}: {
  story: StoryView;
  now?: Date;
  headingLevel?: 2 | 3;
}) {
  const Heading = headingLevel === 2 ? 'h2' : 'h3';
  return (
    <article className="flex flex-col gap-3 rounded-xl border border-line bg-surface p-4 shadow-sm sm:p-5">
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs font-medium uppercase tracking-wide text-muted">
        <Link to={`/section/${story.section.slug}`} className="text-accent hover:underline">
          {story.section.name}
        </Link>
        <span aria-hidden="true">·</span>
        <span>{story.source.name}</span>
        <span aria-hidden="true">·</span>
        <RelativeTime iso={story.publishedAt} estimated={story.dateEstimated} now={now} />
      </div>
      <Heading className="text-lg font-bold leading-snug sm:text-xl">
        <Link to={`/story/${story.id}`} className="hover:underline">
          {story.headline}
        </Link>
      </Heading>
      <p className="max-w-[65ch] text-base leading-relaxed">{story.summary}</p>
      <WordsToKnow terms={story.keyTerms} />
      <div>
        <ExternalLink href={story.originalUrl} sourceName={story.source.name} />
      </div>
      <p className="text-xs text-muted">{story.attribution}</p>
    </article>
  );
}
