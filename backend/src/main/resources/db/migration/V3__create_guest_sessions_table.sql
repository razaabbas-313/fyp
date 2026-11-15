-- FotoFetch AI Guest Sessions and Matched Photos Migration (V3)

CREATE TABLE IF NOT EXISTS guest_sessions (
    id UUID PRIMARY KEY,
    session_token_hash VARCHAR(64) NOT NULL UNIQUE,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_guest_session_token_hash ON guest_sessions(session_token_hash);
CREATE INDEX IF NOT EXISTS idx_guest_session_event_id ON guest_sessions(event_id);
CREATE INDEX IF NOT EXISTS idx_guest_session_expires_at ON guest_sessions(expires_at);

CREATE TABLE IF NOT EXISTS guest_session_photos (
    session_id UUID NOT NULL REFERENCES guest_sessions(id) ON DELETE CASCADE,
    photo_id UUID NOT NULL,
    PRIMARY KEY (session_id, photo_id)
);
