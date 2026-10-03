import { setupServer } from 'msw/node';

/** API mock for component tests. Tests add handlers with server.use(...). */
export const server = setupServer();
