import { ApiError, useRegister } from '@qbits/api-client';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { pageTitles } from '../../app/page-titles';
import { usePageTitle } from '../../hooks/usePageTitle';
import { useAuth } from './AuthContext';
import { GoogleSignInButton } from './GoogleSignInButton';
import { passwordError, usernameError } from './passwordPolicy';

/** Create an account. Optional: every story is readable without one. */
export function RegisterPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const register = useRegister();
  const { setSession } = useAuth();
  const navigate = useNavigate();
  usePageTitle(pageTitles.register);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const problem = usernameError(username) ?? passwordError(password);
    if (problem) {
      setError(problem);
      return;
    }
    setError(null);
    register.mutate(
      { username: username.trim(), password },
      {
        onSuccess: (session) => {
          setSession(session.token, session.username);
          void navigate('/');
        },
        onError: (err) => {
          setError(
            err instanceof ApiError && err.code === 'USERNAME_TAKEN'
              ? 'That username is taken.'
              : 'Could not create your account. Please try again.',
          );
        },
      },
    );
  }

  return (
    <div className="card mx-auto flex w-full max-w-md flex-col gap-6 p-6 sm:mt-6 sm:p-10">
      <div>
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">Create an account</h1>
        <p className="mt-1 text-sm text-muted">Optional -- you can read every story without one.</p>
      </div>

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
            autoComplete="new-password"
            className="field"
            required
          />
          <span className="text-xs text-muted">
            At least 5 characters, letters and numbers only -- no special characters.
          </span>
        </label>

        {error && (
          <p role="alert" className="text-sm font-semibold">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={register.isPending}
          className="btn-primary py-2.5 text-base"
        >
          {register.isPending ? 'Creating account…' : 'Create account'}
        </button>
      </form>

      <div className="flex items-center gap-3 text-xs text-muted">
        <span className="h-px flex-1 bg-line" aria-hidden="true" />
        or
        <span className="h-px flex-1 bg-line" aria-hidden="true" />
      </div>

      <GoogleSignInButton onError={setError} />

      <p className="text-sm text-muted">
        Already have an account?{' '}
        <Link to="/login" className="font-semibold text-accent hover:underline">
          Log in
        </Link>
      </p>
    </div>
  );
}
