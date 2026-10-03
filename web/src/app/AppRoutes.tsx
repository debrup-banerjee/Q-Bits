import { Route, Routes } from 'react-router';
import { AppShell } from './AppShell';
import { NotFoundPage } from './NotFoundPage';
import { HomePage } from '../features/sections/HomePage';

/** Routes: /, /section/:slug, /story/:id, /about. Pages are filled in by later tasks. */
export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<HomePage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
