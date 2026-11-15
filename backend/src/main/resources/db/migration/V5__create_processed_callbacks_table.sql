-- FotoFetch AI Callback Replay Protection Migration (V5)

CREATE TABLE IF NOT EXISTS processed_callbacks (
    callback_id VARCHAR(64) PRIMARY KEY,
    photo_id UUID NOT NULL REFERENCES photos(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_processed_callbacks_photo_id ON processed_callbacks(photo_id);
