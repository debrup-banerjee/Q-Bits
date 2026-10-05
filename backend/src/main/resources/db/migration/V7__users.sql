-- Optional accounts: register/login with a password, or sign in with Google. Login is never
-- required to browse Q-Bits (conventions: auth is additive, not a gate).

create table users (
  id              uuid primary key,
  username        text not null check (char_length(username) between 3 and 30),
  password_hash   text,
  google_subject  text,
  created_at      timestamptz not null,
  constraint users_username_key unique (username),
  constraint users_google_subject_key unique (google_subject),
  constraint users_has_credential check (password_hash is not null or google_subject is not null)
);
