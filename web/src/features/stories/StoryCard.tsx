import type { StoryView } from '@qbits/api-client';
import { Link } from 'react-router';
import { ExternalLink } from '../../components/ExternalLink';
import { RelativeTime } from '../../components/RelativeTime';
import { ResourceLinks } from '../../components/ResourceLinks';
import { sectionPill } from '../sections/sectionTheme';
import { StoryPicture } from './StoryPicture';
import { WordsToKnow } from './WordsToKnow';

/**
 * One story: its picture or cover art, headline, section, source and time, our summary, words to
 * know, the link out and the attribution line. No embedded content (spec 003 R6, 009 R5).
 */
export function StoryCard({
  story,
  now,
  headingLevel = 3,
}: {
  story: StoryView;
  now?: Date;
  headingLevel?: 1 | 2 | 3;
}) {
  const Heading = (['h1', 'h2', 'h3'] as const)[headingLevel - 1]!;
  return (
    <article
      className={`card flex flex-col gap-3.5 p-5 sm:p-6 ${headingLevel === 1 ? 'sm:p-8' : 'card-hover'}`}
    >
      <StoryPicture story={story} />
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs font-medium uppercase tracking-wide text-muted">
        <Link
          to={`/section/${story.section.slug}`}
          className={`rounded-full px-2.5 py-0.5 tracking-wide hover:opacity-90 ${sectionPill(story.section.slug)}`}
        >
          {story.section.name}
        </Link>
        <span aria-hidden="true">·</span>
        <span>{story.source.name}</span>
        <span aria-hidden="true">·</span>
        <RelativeTime iso={story.publishedAt} estimated={story.dateEstimated} now={now} />
      </div>
      <Heading className="text-lg font-bold leading-snug sm:text-xl">
        <Link to={`/story/${story.id}`} className="hover:text-accent">
          {story.headline}
        </Link>
      </Heading>
      <p className="max-w-[65ch] text-base leading-relaxed">{story.summary}</p>
      <WordsToKnow terms={story.keyTerms} />
      <ResourceLinks resources={story.resources} />
      <div>
        <ExternalLink href={story.originalUrl} sourceName={story.source.name} />
      </div>
      <p className="text-xs text-muted">{story.attribution}</p>
    </article>
  );
}
