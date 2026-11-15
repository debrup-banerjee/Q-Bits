/**
 * Page titles (spec 007 R2.1, R5.1). The backend sends the same titles on the first response;
 * both sides are checked against specs/007-seo-pages/titles.json.
 */
export const pageTitles = {
  home: "Q-Bits: today's AI news, explained in plain words",
  sections: 'AI news by section | Q-Bits',
  about: 'About Q-Bits',
  login: 'Log in | Q-Bits',
  register: 'Create an account | Q-Bits',
  storyNotFound: 'Story not available | Q-Bits',
  notFound: 'Page not found | Q-Bits',
  story: (headline: string) => `${headline} | Q-Bits`,
  section: (sectionName: string) => `${sectionName}: AI news from the last 72 hours | Q-Bits`,
};
