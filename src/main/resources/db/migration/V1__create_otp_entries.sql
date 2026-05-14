CREATE TABLE otp_entries (
  user_id        TEXT        PRIMARY KEY,
  otp_hash       BYTEA       NOT NULL,
  otp_algo       TEXT        NOT NULL,
  expires_at     TIMESTAMPTZ NOT NULL,
  attempts_left  INTEGER     NOT NULL CHECK (attempts_left >= 0),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX otp_entries_expires_at_idx ON otp_entries (expires_at);
