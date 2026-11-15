-- FotoFetch AI Baseline Schema Migration (V1)
-- Matches the existing PostgreSQL schema

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_email ON users(email);

CREATE TABLE IF NOT EXISTS events (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    event_date DATE NOT NULL,
    public_id VARCHAR(32) NOT NULL UNIQUE,
    rekognition_collection_id VARCHAR(128) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_event_public_id ON events(public_id);
CREATE INDEX IF NOT EXISTS idx_event_user_id ON events(user_id);

CREATE TABLE IF NOT EXISTS photos (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    original_s3_key VARCHAR(1024) NOT NULL,
    thumbnail_s3_key VARCHAR(1024),
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(64),
    file_size BIGINT,
    upload_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    thumbnail_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    indexing_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    faces_found INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_photo_event_id ON photos(event_id);
CREATE INDEX IF NOT EXISTS idx_photo_indexing_status ON photos(indexing_status);
