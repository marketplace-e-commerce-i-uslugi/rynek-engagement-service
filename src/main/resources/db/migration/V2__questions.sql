-- Pytania do ofert i odpowiedzi sprzedawcy. Sprzedawca (właściciel oferty) i autor imienia są kopią z chwili pytania.
CREATE TABLE questions (
    id           uuid PRIMARY KEY,
    offer_id     uuid          NOT NULL,
    seller_id    uuid          NOT NULL,
    author_id    uuid          NOT NULL,
    author_name  varchar(120)  NOT NULL,
    text         varchar(500)  NOT NULL,
    likes_count  integer       NOT NULL DEFAULT 0,
    answer_text  varchar(2000),
    answer_at    timestamptz,
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL
);

CREATE INDEX questions_offer_newest_idx ON questions (offer_id, created_at DESC, id);
CREATE INDEX questions_offer_useful_idx ON questions (offer_id, likes_count DESC, created_at DESC, id);

-- „Przydatne”: jedno polubienie na użytkownika, ponowienie niczego nie zmienia.
CREATE TABLE question_likes (
    question_id uuid        NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    user_id     uuid        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (question_id, user_id)
);
