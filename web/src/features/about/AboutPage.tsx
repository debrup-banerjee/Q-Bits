import { useSections, useSite, useSources } from '@qbits/api-client';
import { pageTitles } from '../../app/page-titles';
import { ErrorState } from '../../components/ErrorState';
import { PageHero } from '../../components/PageHero';
import { usePageTitle } from '../../hooks/usePageTitle';
import { sectionDot } from '../sections/sectionTheme';

/** How Q-Bits works, the sources it uses and how publishers reach us (spec 003 R7). */
export function AboutPage() {
  const sections = useSections();
  const sources = useSources();
  const site = useSite();
  const email = site.data?.data.contactEmail;
  usePageTitle(pageTitles.about);

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <PageHero title="How Q-Bits works" eyebrow="About">
        <p>
          Short, plain-language AI news, written in our own words and always linked to the source.
        </p>
      </PageHero>
      <article className="card flex flex-col gap-8 p-6 text-base leading-relaxed *:max-w-[65ch] sm:p-10">
        <section className="flex flex-col gap-2">
          <h2 className="text-xl font-bold">What you read here</h2>
          <p>
            Q-Bits collects AI news from the last 72 hours from well-known news outlets and the
            official blogs of companies and research labs. For each story we write a short summary
            in our own words, in simple language, and explain the important terms along the way.
          </p>
          <p>
            We write each summary only from the headline and short teaser the publisher shares in
            its public news feed. We never copy their articles. Every story links to the original,
            so you can read the full piece on the publisher&apos;s own site.
          </p>
        </section>

        <section className="flex flex-col gap-2">
          <h2 className="text-xl font-bold">AI Latest and the five sections</h2>
          <p>
            Q-Bits is a <strong>daily digest</strong>. We read the news feeds all day, then write
            every summary together and publish one edition each morning at 6:00 am (India time).{' '}
            <strong>AI Latest</strong> shows today&apos;s edition: everything from the 24 hours
            before it. Each story also belongs to one of five sections, which show the last three
            editions:
          </p>
          {/* Section names and descriptions come from the API, never hard-coded (conventions). */}
          {sections.isError && <ErrorState onRetry={() => void sections.refetch()} />}
          {sections.isPending && <p className="text-muted">Loading the sections…</p>}
          {sections.data && (
            <ul className="space-y-1.5" aria-label="The sections">
              {sections.data.data.map((section) => (
                <li key={section.slug} className="flex items-start gap-2">
                  <span
                    aria-hidden="true"
                    className={`mt-2 h-2 w-2 shrink-0 rounded-full ${sectionDot(section.slug)}`}
                  />
                  <span>
                    <strong>{section.name}</strong>: {section.description}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="flex flex-col gap-2" aria-labelledby="sources-heading">
          <h2 id="sources-heading" className="text-xl font-bold">
            Where the news comes from
          </h2>
          {sources.isError && <ErrorState onRetry={() => void sources.refetch()} />}
          {sources.isPending && <p className="text-muted">Loading the source list…</p>}
          {sources.data && (
            <ul className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              {sources.data.data.map((s) => (
                <li key={s.name}>
                  <a
                    href={s.homepage}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-accent hover:underline"
                  >
                    {s.name}
                  </a>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="flex flex-col gap-2">
          <h2 className="text-xl font-bold">Corrections and removal</h2>
          <p>
            If you are a publisher and want a correction, or want your stories removed from Q-Bits,
            email{' '}
            {email ? (
              <a href={`mailto:${email}`} className="font-medium text-accent hover:underline">
                {email}
              </a>
            ) : (
              'us'
            )}
            . We act on removal requests within 24 hours.
          </p>
        </section>
      </article>
    </div>
  );
}
