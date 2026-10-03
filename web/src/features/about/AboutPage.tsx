import { useSite, useSources } from '@qbits/api-client';
import { ErrorState } from '../../components/ErrorState';

/** How Q-Bits works, the sources it uses and how publishers reach us (spec 003 R7). */
export function AboutPage() {
  const sources = useSources();
  const site = useSite();
  const email = site.data?.data.contactEmail;

  return (
    <article className="mx-auto flex max-w-[65ch] flex-col gap-6 text-base leading-relaxed">
      <h1 className="text-2xl font-bold sm:text-3xl">How Q-Bits works</h1>

      <section className="flex flex-col gap-2">
        <h2 className="text-lg font-semibold">What you read here</h2>
        <p>
          Q-Bits collects AI news from the last 72 hours from well-known news outlets and the
          official blogs of companies and research labs. For each story we write a short summary in
          our own words, in simple language, and explain the important terms along the way.
        </p>
        <p>
          We write each summary only from the headline and short teaser the publisher shares in its
          public news feed. We never copy their articles. Every story links to the original, so you
          can read the full piece on the publisher&apos;s own site.
        </p>
      </section>

      <section className="flex flex-col gap-2">
        <h2 className="text-lg font-semibold">The four sections</h2>
        <ul className="list-disc space-y-1 pl-5">
          <li>
            <strong>Global AI Tech</strong>: new AI models and products, and what they can do.
          </li>
          <li>
            <strong>World Business</strong>: money, companies, chips and jobs around the world.
          </li>
          <li>
            <strong>India AI</strong>: everything AI in India, including global companies&apos;
            moves here.
          </li>
          <li>
            <strong>Innovations &amp; Research</strong>: new ideas and discoveries from labs and
            universities.
          </li>
        </ul>
      </section>

      <section className="flex flex-col gap-2" aria-labelledby="sources-heading">
        <h2 id="sources-heading" className="text-lg font-semibold">
          Where the news comes from
        </h2>
        {sources.isError && <ErrorState onRetry={() => void sources.refetch()} />}
        {sources.isPending && <p className="text-muted">Loading the source list…</p>}
        {sources.data && (
          <ul className="grid grid-cols-1 gap-1 sm:grid-cols-2">
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
        <h2 className="text-lg font-semibold">Corrections and removal</h2>
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
  );
}
