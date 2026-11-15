-- Migration V4: Create guest_identities and guest_identity_photos tables
-- Supports persistent face-to-name tagging and name-based photo retrieval (FYP Proposal Objective 4)

CREATE TABLE IF NOT EXISTS guest_identities (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT uq_guest_identity_event_username UNIQUE (event_id, username)
);

CREATE TABLE IF NOT EXISTS guest_identity_photos (
    identity_id UUID NOT NULL REFERENCES guest_identities(id) ON DELETE CASCADE,
    photo_id UUID NOT NULL,
    PRIMARY KEY (identity_id, photo_id)
);

CREATE INDEX IF NOT EXISTS idx_guest_identities_event_id ON guest_identities(event_id);
CREATE INDEX IF NOT EXISTS idx_guest_identities_username ON guest_identities(username);
CREATE INDEX IF NOT EXISTS idx_guest_identities_name ON guest_identities(name);
