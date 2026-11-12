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

    /** {@code available}: oferta jest opublikowana; {@code ownerId} to właściciel (sprzedawca). */
    public record OfferInfo(UUID offerId, boolean available, String title, UUID channelId, UUID ownerId) {
    }
}
