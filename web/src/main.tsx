import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import { seedInitialData } from './app/initial-data';
import { newQueryClient } from './app/providers';
import './index.css';

const root = document.getElementById('root');
if (!root) {
  throw new Error('missing #root element');
}
// The server-filled first response carries this page's data (spec 007 R4.2).
const queryClient = newQueryClient();
seedInitialData(queryClient);
createRoot(root).render(
  <StrictMode>
    <App queryClient={queryClient} />
  </StrictMode>,
);
