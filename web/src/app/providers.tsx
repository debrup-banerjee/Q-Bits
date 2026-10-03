import { ApiProvider, createQBitsApi, type QBitsApi } from '@qbits/api-client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';

export function apiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL as string | undefined;
  return configured && configured.length > 0 ? configured : window.location.origin;
}

export function newQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { refetchOnWindowFocus: true, retry: 1 },
    },
  });
}

/** Data providers shared by the app and tests. */
export function Providers({
  children,
  api,
  queryClient,
}: {
  children: ReactNode;
  api?: QBitsApi;
  queryClient?: QueryClient;
}) {
  const [client] = useState(() => queryClient ?? newQueryClient());
  const [resolvedApi] = useState(() => api ?? createQBitsApi(apiBaseUrl()));
  return (
    <QueryClientProvider client={client}>
      <ApiProvider api={resolvedApi}>{children}</ApiProvider>
    </QueryClientProvider>
  );
}
