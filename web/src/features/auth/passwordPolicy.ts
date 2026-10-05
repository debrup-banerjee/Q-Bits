/** Mirrors the backend's PasswordPolicy exactly, so the form can give instant feedback. */
export const MIN_USERNAME = 3;
export const MAX_USERNAME = 30;
export const MIN_PASSWORD = 5;

export function usernameError(username: string): string | null {
  const trimmed = username.trim();
  if (trimmed.length < MIN_USERNAME || trimmed.length > MAX_USERNAME) {
    return `Username must be ${MIN_USERNAME}-${MAX_USERNAME} characters.`;
  }
  return null;
}

export function passwordError(password: string): string | null {
  if (password.length < MIN_PASSWORD) {
    return `Password must be at least ${MIN_PASSWORD} characters.`;
  }
  if (!/^[A-Za-z0-9]+$/.test(password)) {
    return 'Password can only contain letters and numbers -- no special characters.';
  }
  return null;
}
