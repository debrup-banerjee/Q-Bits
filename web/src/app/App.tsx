import type { QueryClient } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router';
import { AppRoutes } from './AppRoutes';
import { Providers } from './providers';

export function App({ queryClient }: { queryClient?: QueryClient }) {
  return (
    <Providers queryClient={queryClient}>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </Providers>
  );
}
