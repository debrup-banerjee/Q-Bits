import type { StoryView } from '@qbits/api-client';
import { useId, useState } from 'react';
import { Link } from 'react-router';
import { ExternalLink } from '../../components/ExternalLink';
import { RelativeTime } from '../../components/RelativeTime';
import { ResourceLinks } from '../../components/ResourceLinks';
import { WordsToKnow } from '../stories/WordsToKnow';
import { SourceBadge } from './SourceBadge';

/**
 * A compact, timeline-style story (spec 004 R4): source badge, source, time, section, headline,
 * a three-line summary that expands in place with the words to know, the link out and the
 * attribution. No images and no social buttons.
 */
export function FeedCard({ story, now }: { story: StoryView; now?: Date }) {
  const [expanded, setExpanded] = useState(false);
  const bodyId = useId();

  return (
    <article className="flex gap-3 border-b border-line px-1 py-4">
      <SourceBadge name={story.source.name} />
      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        <div className="flex flex-wrap items-center gap-x-1.5 text-sm text-muted">
          <span className="font-semibold text-ink">{story.source.name}</span>
          <span aria-hidden="true">·</span>
          <RelativeTime iso={story.publishedAt} estimated={story.dateEstimated} now={now} />
          <span aria-hidden="true">·</span>
          <Link
            to={`/section/${story.section.slug}`}
            className="font-medium text-accent hover:underline"
          >
            {story.section.name}
          </Link>
        </div>
        <h2 className="text-[17px] font-semibold leading-snug sm:text-lg">
          <Link to={`/story/${story.id}`} className="hover:underline">
            {story.headline}
          </Link>
        </h2>
        <div id={bodyId} className="flex flex-col gap-2">
          <p
            data-testid="feed-summary"
            className={`text-base leading-relaxed ${expanded ? '' : 'line-clamp-3'}`}
          >
            {story.summary}
          </p>
          {expanded && <WordsToKnow terms={story.keyTerms} defaultOpen />}
        </div>
        <div>
          <button
            type="button"
            aria-expanded={expanded}
            aria-controls={bodyId}
            onClick={() => setExpanded((v) => !v)}
            className="text-sm font-semibold text-accent hover:underline"
          >
            {expanded ? 'Show less' : 'Show more'}
          </button>
        </div>
        <ResourceLinks resources={story.resources} />
        <div className="mt-1">
          <ExternalLink href={story.originalUrl} sourceName={story.source.name} />
        </div>
        <p className="text-xs text-muted">{story.attribution}</p>
      </div>
    </article>
  );
}
