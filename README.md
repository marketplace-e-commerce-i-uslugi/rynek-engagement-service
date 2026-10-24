# rynek-engagement-service

`engagement-service` — pytania (komentarze), opinie, polubienia, zgłoszenia

> Status: **szkielet**. Wszystkie endpointy z tabeli istnieją w kontrolerach (`src/main/java/.../api/`) i zwracają `501 Not Implemented` (RFC 7807). Ciała żądań to puste rekordy w `api/dto/`.

## Uruchomienie

```bash
./mvnw spring-boot:run   # port 8088, zmienna PORT
./mvnw verify            # testy
```

- Swagger UI: http://localhost:8088/swagger-ui.html
- Zdrowie: http://localhost:8088/actuator/health

## Zakres i endpointy

_Skopiowane z `rynek-docs/architektura/rynek-endpointy-mikroserwisy.md`. Od teraz to repozytorium jest właścicielem tej specyfikacji._

Ekrany: 4 Karta oferty (pytania), 5 zakładka Opinie, Shorts (polubienia).

| Metoda | Ścieżka | Opis | Dostęp |
|---|---|---|---|
| GET | `/api/v1/offers/{offerId}/questions?sort=&cursor=` | Pytania i odpowiedzi pod ofertą | pub |
| POST | `/api/v1/offers/{offerId}/questions` | Zadanie pytania | user |
| POST | `/api/v1/questions/{questionId}/answers` | Odpowiedź sprzedawcy (wyróżniona) | seller |
| PATCH | `/api/v1/questions/{questionId}` | Edycja własnego pytania | user |
| DELETE | `/api/v1/questions/{questionId}` | Usunięcie własnego pytania | user |
| PUT | `/api/v1/questions/{questionId}/like` | Polub pytanie („przydatne”) | user |
| GET | `/api/v1/channels/{channelId}/reviews?cursor=` | Opinie o sprzedawcy | pub |
| GET | `/api/v1/channels/{channelId}/rating-summary` | Średnia i rozkład ocen | pub |
| POST | `/api/v1/orders/{orderId}/review` | Opinia po zakupie (opinia zweryfikowanego zakupu) | user |
| POST | `/api/v1/bookings/{bookingId}/review` | Opinia po wykonanej usłudze | user |
| POST | `/api/v1/reviews/{reviewId}/reply` | Odpowiedź sprzedawcy na opinię | seller |
| PUT | `/api/v1/offers/{offerId}/like` | Polubienie oferty / Shorta | user |
| DELETE | `/api/v1/offers/{offerId}/like` | Cofnięcie polubienia | user |
| POST | `/api/v1/reports` | Zgłoszenie treści (oferta, pytanie, opinia, czat) | user |
| GET | `/internal/ratings?channelIds=` | Wsadowo: oceny dla kart ofert | int |

## Zdarzenia Kafka

Wiersze z §17, w których występuje `engagement` (publikuje lub słucha).

| Zdarzenie | Publikuje | Słuchają |
|---|---|---|
| `question.created`, `question.answered` | engagement | notification |
| `review.created` | engagement | channel (ocena), search, notification |
| `order.created`, `order.paid`, `order.shipped`, `order.completed`, `order.cancelled` | order | catalog (zapas), notification, analytics, engagement |
| `booking.created`, `booking.confirmed`, `booking.cancelled`, `booking.completed` | booking | notification, analytics, engagement |

## Dane i zależności

- **Magazyn danych:** PostgreSQL
- **Zależności:** order-service, booking-service (weryfikacja zakupu)
- **Kontrakty:** `rynek-contracts/openapi/` i `rynek-contracts/events/`
- **Lokalne uruchomienie:** `rynek-infra/local/`

## Kolejność budowy

Etap z §19: 7. `chat-service`, `engagement-service`, `notification-service`.

## Wyjątki routingu

Gateway (§2) kieruje ruch po prefiksie ścieżki, a poniższe endpointy leżą pod prefiksem innego serwisu. Gateway obsługuje je trasami szczegółowymi (`order: -1`), które mają pierwszeństwo przed trasami po prefiksie. Ścieżki pozostają bez zmian (decyzja z 2026-10-24). Test `RoutingTests` w `rynek-api-gateway` sprawdza, czy każda z nich trafia do właściwego serwisu.

- `/api/v1/offers/{offerId}/questions`, `/api/v1/offers/{offerId}/like`: należy do `engagement-service` (prefiks wskazuje `catalog-service`).
- `/api/v1/channels/{channelId}/reviews`, `/api/v1/channels/{channelId}/rating-summary`: należy do `engagement-service` (prefiks wskazuje `channel-service`).
- `/api/v1/orders/{orderId}/review`: należy do `engagement-service` (prefiks wskazuje `order-service`).
- `/api/v1/bookings/{bookingId}/review`: należy do `engagement-service` (prefiks wskazuje `booking-service`).
