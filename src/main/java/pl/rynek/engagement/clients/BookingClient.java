package pl.rynek.engagement.clients;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Rezerwacje z booking-service: sprawdzenie, że usługa została wykonana, i zgłoszenie zapisanej opinii. */
@Component
public class BookingClient {

    private static final Logger log = LoggerFactory.getLogger(BookingClient.class);

    private final RestClient http;

    public BookingClient(@Value("${rynek.booking.url}") String bookingUrl) {
        this.http = Clients.http(bookingUrl);
    }

    public Optional<Booking> booking(UUID bookingId) {
        try {
            return Optional.ofNullable(http.get().uri("/internal/bookings/{id}", bookingId).retrieve().body(Booking.class));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
            }
            log.warn("booking-service odpowiedział {} dla rezerwacji {}", e.getStatusCode(), bookingId);
            throw Clients.unavailable("Serwis rezerwacji");
        } catch (ResourceAccessException e) {
            log.warn("Brak odpowiedzi booking-service: {}", e.getMessage());
            throw Clients.unavailable("Serwis rezerwacji");
        }
    }

    public boolean reviewed(UUID bookingId) {
        try {
            http.put().uri("/internal/bookings/{id}/reviewed", bookingId).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientResponseException | ResourceAccessException e) {
            log.warn("booking-service nie przyjął opinii o rezerwacji {}: {}", bookingId, e.getMessage());
            return false;
        }
    }

    /** {@code status} to nazwa stałej, np. {@code COMPLETED}; {@code providerId} to właściciel usługi. */
    public record Booking(UUID id, UUID offerId, String title, UUID channelId, UUID customerId, UUID providerId, String status) {
    }
}
