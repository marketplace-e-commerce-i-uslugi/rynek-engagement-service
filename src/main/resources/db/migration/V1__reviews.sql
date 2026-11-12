-- Opinia o sprzedawcy po zrealizowanym zamówieniu albo wykonanej usłudze. Jedna opinia na zamówienie lub rezerwację.
-- Tytuł oferty, kanał i autor są kopią z chwili wystawienia opinii.
CREATE TABLE reviews (
    id               uuid PRIMARY KEY,
    channel_id       uuid          NOT NULL,
    seller_id        uuid          NOT NULL,
    author_id        uuid          NOT NULL,
    author_name      varchar(120)  NOT NULL,
    source_type      varchar(10)   NOT NULL CHECK (source_type IN ('ORDER', 'BOOKING')),
    source_id        uuid          NOT NULL,
    offer_title      varchar(200),
    rating           integer       NOT NULL CHECK (rating BETWEEN 1 AND 5),
    text             varchar(2000) NOT NULL,
    verified         boolean       NOT NULL DEFAULT true,
    reply_text       varchar(2000),
    reply_at         timestamptz,
    -- false: order-service lub booking-service jeszcze nie przyjął zgłoszenia „opinia zapisana”; ponawiane co minutę.
    source_notified  boolean       NOT NULL DEFAULT false,
    created_at       timestamptz   NOT NULL,
    UNIQUE (source_type, source_id)
);

CREATE INDEX reviews_channel_idx ON reviews (channel_id, created_at DESC, id);
CREATE INDEX reviews_unnotified_idx ON reviews (created_at) WHERE NOT source_notified;
