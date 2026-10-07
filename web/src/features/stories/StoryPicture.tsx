import type { StoryView } from '@qbits/api-client';
import { useState } from 'react';
import { CoverArt } from './CoverArt';

const LINK = 'underline decoration-line underline-offset-2 hover:text-ink';

/**
 * A story's picture with its credit (spec 009 R5). A photo-library photo is labelled as
 * illustrative and credits the photographer and library, as the library's licence asks; a
 * publisher image (shown only with recorded permission) credits the publisher. Stories without a
 * picture, or whose picture fails to load, get generated cover art instead.
 */
export function StoryPicture({ story }: { story: StoryView }) {
  const [failed, setFailed] = useState(false);
  const image = story.image;

  if (!image || failed) {
    return (
      <div className="story-figure">
        <CoverArt story={story} />
      </div>
    );
  }
  return (
    <figure className="story-figure">
      <img
        src={image.url}
        alt={image.alt}
        loading="lazy"
        decoding="async"
        referrerPolicy="no-referrer"
        className="story-img"
        style={image.color ? { backgroundColor: image.color } : undefined}
        onError={() => setFailed(true)}
      />
      <figcaption className="story-credit">
        {image.kind === 'photo' ? (
          <>
            Illustrative photo by <Credit href={image.creditUrl}>{image.credit}</Credit> on{' '}
            <Credit href={image.providerUrl}>{image.provider}</Credit>
          </>
        ) : (
          <>
            Image: <Credit href={image.providerUrl}>{image.credit}</Credit>
          </>
        )}
      </figcaption>
    </figure>
  );
}

function Credit({ href, children }: { href: string | null; children: string }) {
  if (!href) {
    return <>{children}</>;
  }
  return (
    <a href={href} target="_blank" rel="noopener noreferrer" className={LINK}>
      {children}
    </a>
  );
}
