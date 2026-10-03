import { BrowserRouter } from 'react-router';
import { AppRoutes } from './AppRoutes';
import { Providers } from './providers';

export function App() {
  return (
    <Providers>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </Providers>
  );
}
