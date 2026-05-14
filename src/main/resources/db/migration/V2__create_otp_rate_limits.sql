CREATE TABLE otp_rate_limits (
  user_id            TEXT        NOT NULL,
  operation          TEXT        NOT NULL,
  window_key         TEXT        NOT NULL,
  count              INTEGER     NOT NULL,
  window_started_at  TIMESTAMPTZ NOT NULL,
  expires_at         TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (user_id, operation, window_key)
);
CREATE INDEX otp_rate_limits_expires_at_idx ON otp_rate_limits (expires_at);
