CREATE TABLE IF NOT EXISTS time_records (
    id BIGSERIAL PRIMARY KEY,
    recorded_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_time_records_recorded_time ON time_records(recorded_time);

CREATE INDEX IF NOT EXISTS idx_time_records_created_at ON time_records(created_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO timerecorder;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO timerecorder;