import { Route, Routes } from 'react-router';
import { AppShell } from './AppShell';
import { NotFoundPage } from './NotFoundPage';

/** Routes: /, /section/:slug, /story/:id, /about. Pages are filled in by later tasks. */
export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<h1 className="sr-only">Latest AI news</h1>} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
