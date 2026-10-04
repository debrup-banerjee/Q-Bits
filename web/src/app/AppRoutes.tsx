import { Route, Routes } from 'react-router';
import { AppShell } from './AppShell';
import { NotFoundPage } from './NotFoundPage';
import { SectionsOverviewPage } from '../features/sections/SectionsOverviewPage';
import { LatestPage } from '../features/latest/LatestPage';
import { SectionPage } from '../features/sections/SectionPage';
import { StoryPage } from '../features/stories/StoryPage';
import { AboutPage } from '../features/about/AboutPage';
import { LoginPage } from '../features/auth/LoginPage';
import { RegisterPage } from '../features/auth/RegisterPage';

/**
 * Routes: /, /section/:slug, /story/:id, /about, /login, /register. Login and registration are
 * optional accounts, not a gate -- every other route stays reachable without signing in.
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<LatestPage />} />
        <Route path="sections" element={<SectionsOverviewPage />} />
        <Route path="section/:slug" element={<SectionPage />} />
        <Route path="story/:id" element={<StoryPage />} />
        <Route path="about" element={<AboutPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
