import { useGoogleSignIn } from '@qbits/api-client';
import { useEffect, useRef } from 'react';
import { useAuth } from './AuthContext';

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: {
            client_id: string;
            callback: (response: { credential: string }) => void;
          }) => void;
          renderButton: (
            parent: HTMLElement,
            options: { theme?: string; size?: string; width?: number },
          ) => void;
        };
      };
    };
  }
}

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined;
const SCRIPT_SRC = 'https://accounts.google.com/gsi/client';
let scriptPromise: Promise<void> | null = null;

function loadGoogleScript(): Promise<void> {
  if (window.google?.accounts?.id) {
    return Promise.resolve();
  }
  scriptPromise ??= new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = SCRIPT_SRC;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Could not load Google Sign-In.'));
    document.head.appendChild(script);
  });
  return scriptPromise;
}

/**
 * "Sign in with Google" button, rendered by Google's own Identity Services script. Renders
 * nothing if VITE_GOOGLE_CLIENT_ID isn't set, so the rest of the page works without it.
 */
export function GoogleSignInButton({ onError }: { onError: (message: string) => void }) {
  const container = useRef<HTMLDivElement>(null);
  const { setSession } = useAuth();
  const googleSignIn = useGoogleSignIn();

  useEffect(() => {
    if (!CLIENT_ID || !container.current) {
      return;
    }
    let cancelled = false;
    loadGoogleScript()
      .then(() => {
        if (cancelled || !container.current || !window.google) {
          return;
        }
        window.google.accounts.id.initialize({
          client_id: CLIENT_ID,
          callback: (response) => {
            googleSignIn.mutate(response.credential, {
              onSuccess: (session) => setSession(session.token, session.username),
              onError: () => onError('Could not sign in with Google. Please try again.'),
            });
          },
        });
        window.google.accounts.id.renderButton(container.current, {
          theme: 'outline',
          size: 'large',
          width: 320,
        });
      })
      .catch(() => onError('Could not load Google Sign-In.'));
    return () => {
      cancelled = true;
    };
    // Google's script initializes the button once; re-running on every render would duplicate it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!CLIENT_ID) {
    return null;
  }
  return <div ref={container} />;
}
