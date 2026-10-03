import { Route, Routes } from 'react-router';
import { AppShell } from './AppShell';
import { NotFoundPage } from './NotFoundPage';
import { HomePage } from '../features/sections/HomePage';
import { SectionPage } from '../features/sections/SectionPage';
import { StoryPage } from '../features/stories/StoryPage';
import { AboutPage } from '../features/about/AboutPage';

/** Routes: /, /section/:slug, /story/:id, /about. Pages are filled in by later tasks. */
export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<HomePage />} />
        <Route path="section/:slug" element={<SectionPage />} />
        <Route path="story/:id" element={<StoryPage />} />
        <Route path="about" element={<AboutPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
