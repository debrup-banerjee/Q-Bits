import { useTopStories } from '@qbits/api-client';
import { useRef, useState } from 'react';
import { Link } from 'react-router';
import { sectionDot } from '../sections/sectionTheme';
import { SourceBadge } from './SourceBadge';

/**
 * "Top stories": up to three stories reported by the most outlets (spec 009 R3). A native
 * scroll-snap slider with previous/next buttons and dots. No autoplay, no images, no library.
 * Renders nothing while loading, on error, or when no story qualifies (R3.4).
 */
export function TopStories() {
  const query = useTopStories();
  const track = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(0);
  const stories = query.data?.data ?? [];
  if (stories.length === 0) {
    return null;
  }

  function go(index: number) {
    const el = track.current;
    if (!el) {
      return;
    }
    const clamped = Math.max(0, Math.min(stories.length - 1, index));
    const slide = el.children[clamped] as HTMLElement | undefined;
    slide?.scrollIntoView?.({ behavior: 'smooth', block: 'nearest', inline: 'start' });
    setActive(clamped);
  }

  function onScroll() {
    const el = track.current;
    if (el && el.clientWidth > 0) {
      setActive(Math.round(el.scrollLeft / el.clientWidth));
    }
  }

  return (
    <section
      aria-roledescription="carousel"
      aria-labelledby="top-stories-heading"
      className="flex flex-col gap-3"
    >
      <div className="flex items-center justify-between gap-3">
        <h2 id="top-stories-heading" className="text-lg font-semibold">
          Top stories
        </h2>
        {stories.length > 1 && (
          <div className="flex gap-2">
            <button
              type="button"
              className="btn-secondary"
              aria-label="Previous story"
              disabled={active === 0}
              onClick={() => go(active - 1)}
            >
              ←
            </button>
            <button
              type="button"
              className="btn-secondary"
              aria-label="Next story"
              disabled={active === stories.length - 1}
              onClick={() => go(active + 1)}
            >
              →
            </button>
          </div>
        )}
      </div>
      <div
        ref={track}
        onScroll={onScroll}
        className="flex snap-x snap-mandatory gap-3 overflow-x-auto overscroll-x-contain pb-1"
      >
        {stories.map((s, i) => (
          <div
            key={s.id}
            role="group"
            aria-roledescription="slide"
            aria-label={`${i + 1} of ${stories.length}`}
            className="card flex w-full shrink-0 snap-start flex-col gap-2 p-4"
          >
            <div className="flex items-center gap-2.5 text-sm text-muted">
              <SourceBadge name={s.source.name} />
              <span className="font-semibold text-ink">{s.source.name}</span>
              <span aria-hidden="true">·</span>
              <Link
                to={`/section/${s.section.slug}`}
                className="flex items-center gap-1.5 font-medium text-accent hover:underline"
              >
                <span
                  aria-hidden="true"
                  className={`h-2 w-2 rounded-full ${sectionDot(s.section.slug)}`}
                />
                {s.section.name}
              </Link>
            </div>
            <h3 className="text-[17px] font-semibold leading-snug sm:text-lg">
              <Link to={`/story/${s.id}`} className="hover:text-accent">
                {s.headline}
              </Link>
            </h3>
            <p className="line-clamp-2 text-base leading-relaxed">{s.summary}</p>
            <p className="text-sm font-medium text-muted">Covered by {s.coverage} outlets</p>
          </div>
        ))}
      </div>
      {stories.length > 1 && (
        <div className="flex justify-center gap-2" aria-hidden="true">
          {stories.map((s, i) => (
            <span
              key={s.id}
              className={`h-2 w-2 rounded-full ${i === active ? 'bg-accent' : 'bg-line'}`}
            />
          ))}
        </div>
      )}
    </section>
  );
}
