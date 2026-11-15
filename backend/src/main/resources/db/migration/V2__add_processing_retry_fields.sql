-- FotoFetch AI Processing Retry Fields Migration (V2)

ALTER TABLE photos ADD COLUMN IF NOT EXISTS processing_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE photos ADD COLUMN IF NOT EXISTS last_processing_attempt_at TIMESTAMP;
ALTER TABLE photos ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP;
ALTER TABLE photos ADD COLUMN IF NOT EXISTS last_processing_error VARCHAR(1024);
ALTER TABLE photos ADD COLUMN IF NOT EXISTS processing_updated_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_photo_retry ON photos(upload_status, next_retry_at, processing_attempts);
