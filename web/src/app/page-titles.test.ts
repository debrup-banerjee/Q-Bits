import titles from '../../../specs/007-seo-pages/titles.json';
import { pageTitles } from './page-titles';

// 007 R2.1, R5.1: the backend checks the same fixture, so both sides send the same titles.
it('matches the shared title fixture', () => {
  expect(pageTitles.home).toBe(titles.home);
  expect(pageTitles.sections).toBe(titles.sections);
  expect(pageTitles.about).toBe(titles.about);
  expect(pageTitles.login).toBe(titles.login);
  expect(pageTitles.register).toBe(titles.register);
  expect(pageTitles.storyNotFound).toBe(titles.storyNotFound);
  expect(pageTitles.notFound).toBe(titles.notFound);
  expect(pageTitles.story(titles.story.input)).toBe(titles.story.title);
  expect(pageTitles.section(titles.section.input)).toBe(titles.section.title);
});
