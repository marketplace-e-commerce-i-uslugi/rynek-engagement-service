package pl.rynek.engagement.clients;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Oferta z catalog-service: czy istnieje, czy jest opublikowana i kto jest jej właścicielem ({@code price-and-stock}). */
@Component
public class CatalogClient {

    private final RestClient http;

    public CatalogClient(@Value("${rynek.catalog.url}") String catalogUrl) {
        this.http = Clients.http(catalogUrl);
    }

    /** Oferta albo pusta, gdy nie istnieje lub została usunięta. Gdy catalog nie odpowiada: 503. */
    public Optional<OfferInfo> offer(UUID offerId) {
        try {
            return Optional.ofNullable(http.get().uri("/internal/offers/{id}/price-and-stock", offerId).retrieve().body(OfferInfo.class));
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest e) {
            return Optional.empty();
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw Clients.unavailable("Katalog ofert");
        }
    }

    /**
     * Blokada oferty przez moderatora (wstrzymuje ją i uniemożliwia publikację) albo jej zdjęcie. {@code false}, gdy oferty już nie ma;
     * niedostępny catalog to 503, żeby moderator nie uznał sprawy za załatwioną.
     */
    public boolean setBlocked(UUID offerId, boolean blocked) {
        try {
            http.put().uri("/internal/offers/{id}/moderation", offerId).body(java.util.Map.of("blocked", blocked)).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw Clients.unavailable("Katalog ofert");
        }
    }

    /** {@code available}: oferta jest opublikowana; {@code ownerId} to właściciel (sprzedawca). */
    public record OfferInfo(UUID offerId, boolean available, String title, UUID channelId, UUID ownerId) {
    }
}
