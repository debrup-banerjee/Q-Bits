import { ApiError, useLogin } from '@qbits/api-client';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { pageTitles } from '../../app/page-titles';
import { usePageTitle } from '../../hooks/usePageTitle';
import { useAuth } from './AuthContext';
import { GoogleSignInButton } from './GoogleSignInButton';

/** Log in. Optional: every story is readable without an account. */
export function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const login = useLogin();
  const { setSession } = useAuth();
  const navigate = useNavigate();
  usePageTitle(pageTitles.login);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    login.mutate(
      { username, password },
      {
        onSuccess: (session) => {
          setSession(session.token, session.username);
          void navigate('/');
        },
        onError: (err) => {
          setError(
            err instanceof ApiError && err.code === 'INVALID_CREDENTIALS'
              ? 'Wrong username or password.'
              : 'Could not log in. Please try again.',
          );
        },
      },
    );
  }

  return (
    <div className="card mx-auto flex w-full max-w-md flex-col gap-6 p-6 sm:mt-6 sm:p-10">
      <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">Log in</h1>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        <label className="flex flex-col gap-1.5">
          <span className="text-sm font-medium">Username</span>
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            className="field"
            required
          />
        </label>
        <label className="flex flex-col gap-1.5">
          <span className="text-sm font-medium">Password</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            className="field"
            required
          />
        </label>

        {error && (
          <p role="alert" className="text-sm font-semibold">
            {error}
          </p>
        )}

        <button type="submit" disabled={login.isPending} className="btn-primary py-2.5 text-base">
          {login.isPending ? 'Logging in…' : 'Log in'}
        </button>
      </form>

      <div className="flex items-center gap-3 text-xs text-muted">
        <span className="h-px flex-1 bg-line" aria-hidden="true" />
        or
        <span className="h-px flex-1 bg-line" aria-hidden="true" />
      </div>

      <GoogleSignInButton onError={setError} />

      <p className="text-sm text-muted">
        No account yet?{' '}
        <Link to="/register" className="font-semibold text-accent hover:underline">
          Create one
        </Link>
      </p>
    </div>
  );
}
