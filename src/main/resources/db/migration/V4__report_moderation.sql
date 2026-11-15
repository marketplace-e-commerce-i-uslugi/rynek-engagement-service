-- Rozpatrywanie zgłoszeń przez administratora. status: OPEN (czeka), RESOLVED (rozpatrzone, content_removed: treść usunięta albo oferta
-- zablokowana) albo REJECTED (bez naruszenia). Rozstrzygnięcie dotyczy treści, więc obejmuje wszystkie otwarte zgłoszenia o niej.
ALTER TABLE reports
    ADD COLUMN resolved_by     uuid,
    ADD COLUMN resolved_at     timestamptz,
    ADD COLUMN note            varchar(500),
    ADD COLUMN content_removed boolean NOT NULL DEFAULT false;

CREATE INDEX reports_status_idx ON reports (status, created_at);
