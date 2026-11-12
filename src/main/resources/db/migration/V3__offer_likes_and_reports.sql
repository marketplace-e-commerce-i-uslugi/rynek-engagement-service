-- Polubienie oferty / Shorta: jedno na użytkownika, ponowienie niczego nie zmienia.
CREATE TABLE offer_likes (
    offer_id   uuid        NOT NULL,
    user_id    uuid        NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (offer_id, user_id)
);

CREATE INDEX offer_likes_user_idx ON offer_likes (user_id, created_at DESC);

-- Zgłoszenie treści (oferta, pytanie, opinia, czat). Jedno zgłoszenie danego użytkownika na daną treść.
-- Rozpatrywanie zgłoszeń (panel moderatora) nie jest jeszcze w zakresie, więc status zostaje OPEN.
CREATE TABLE reports (
    id          uuid PRIMARY KEY,
    reporter_id uuid          NOT NULL,
    target_type varchar(10)   NOT NULL CHECK (target_type IN ('OFFER', 'QUESTION', 'REVIEW', 'CHAT')),
    target_id   uuid          NOT NULL,
    reason      varchar(1000) NOT NULL,
    status      varchar(10)   NOT NULL DEFAULT 'OPEN',
    created_at  timestamptz   NOT NULL,
    UNIQUE (reporter_id, target_type, target_id)
);

CREATE INDEX reports_open_idx ON reports (created_at) WHERE status = 'OPEN';
CREATE INDEX reports_target_idx ON reports (target_type, target_id);
