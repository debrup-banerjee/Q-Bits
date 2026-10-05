import { useMe } from '@qbits/api-client';
import { createContext, useContext, useState, type ReactNode } from 'react';

export const SESSION_STORAGE_KEY = 'qbits_auth_session';

type StoredSession = { token: string; username: string };

type AuthState = {
  username: string | null;
  isLoggedIn: boolean;
  setSession: (token: string, username: string) => void;
  logout: () => void;
};

const AuthContext = createContext<AuthState | null>(null);

function readStoredSession(): StoredSession | null {
  try {
    const raw = localStorage.getItem(SESSION_STORAGE_KEY);
    return raw ? (JSON.parse(raw) as StoredSession) : null;
  } catch {
    return null; // private browsing, storage disabled, or corrupt JSON
  }
}

function writeStoredSession(session: StoredSession | null) {
  try {
    if (session === null) {
      localStorage.removeItem(SESSION_STORAGE_KEY);
    } else {
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
    }
  } catch {
    // ignore: private browsing or storage disabled
  }
}

/**
 * Optional accounts (never required to browse). The session (token + username) is known
 * immediately from login/register/Google -- no need to wait on a round trip to show it. The
 * stored token is re-checked against /me in the background purely to catch an expired or revoked
 * token: once that check fails, the reader shows as logged out right away (derived at render time
 * below, not via an effect). The stale copy in storage is cleared by the query cache's own error
 * handler (see newQueryClient in app/providers.tsx), which isn't a React state update.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSessionState] = useState<StoredSession | null>(readStoredSession);
  const me = useMe(session?.token ?? null);
  const active = session !== null && !me.isError;

  function setSession(token: string, username: string) {
    const next = { token, username };
    setSessionState(next);
    writeStoredSession(next);
  }

  function logout() {
    setSessionState(null);
    writeStoredSession(null);
  }

  return (
    <AuthContext.Provider
      value={{ username: active ? session.username : null, isLoggedIn: active, setSession, logout }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return ctx;
}
