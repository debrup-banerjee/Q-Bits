import { useEffect } from 'react';

/** Sets the document title while a page is shown (spec 007 R5.1). Undefined leaves it as is. */
export function usePageTitle(title: string | undefined) {
  useEffect(() => {
    if (title) {
      document.title = title;
    }
  }, [title]);
}
