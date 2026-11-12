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

/** Zamówienia z order-service: sprawdzenie, że kupujący zrealizował zamówienie, i zgłoszenie zapisanej opinii. */
@Component
public class OrderClient {

    private static final Logger log = LoggerFactory.getLogger(OrderClient.class);

    private final RestClient http;

    public OrderClient(@Value("${rynek.order.url}") String orderUrl) {
        this.http = Clients.http(orderUrl);
    }

    /** Pusty wynik: nie ma takiego zamówienia. Gdy order-service nie odpowiada: 503. */
    public Optional<Order> order(UUID orderId) {
        try {
            return Optional.ofNullable(http.get().uri("/internal/orders/{id}", orderId).retrieve().body(Order.class));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
            }
            log.warn("order-service odpowiedział {} dla zamówienia {}", e.getStatusCode(), orderId);
            throw Clients.unavailable("Serwis zamówień");
        } catch (ResourceAccessException e) {
            log.warn("Brak odpowiedzi order-service: {}", e.getMessage());
            throw Clients.unavailable("Serwis zamówień");
        }
    }

    /** {@code true}: order-service przyjął zgłoszenie. */
    public boolean reviewed(UUID orderId) {
        try {
            http.put().uri("/internal/orders/{id}/reviewed", orderId).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientResponseException | ResourceAccessException e) {
            log.warn("order-service nie przyjął opinii o zamówieniu {}: {}", orderId, e.getMessage());
            return false;
        }
    }

    /** {@code status} to nazwa stałej, np. {@code COMPLETED}; {@code title} to pierwsza pozycja zamówienia. */
    public record Order(UUID id, UUID buyerId, UUID sellerId, UUID channelId, String status, String title) {
    }
}
