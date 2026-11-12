package pl.rynek.engagement.reviews;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.engagement.clients.BookingClient;
import pl.rynek.engagement.clients.IdentityClient;
import pl.rynek.engagement.clients.OrderClient;

/**
 * Opinie o sprzedawcy. Wystawia je kupujący zrealizowanego zamówienia albo klient wykonanej usługi (opinia zweryfikowanego
 * zakupu), raz na zamówienie lub rezerwację. Po zapisie zgłaszamy to order-service i booking-service (z ponawianiem).
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    public record Summary(double average, int count, Map<Integer, Integer> distribution) {
    }

    public record Rating(UUID channelId, double average, int count) {
    }

    private final ReviewRepository reviews;
    private final OrderClient orders;
    private final BookingClient bookings;
    private final IdentityClient identity;
    private final Clock clock;

    public ReviewService(ReviewRepository reviews, OrderClient orders, BookingClient bookings, IdentityClient identity, Clock clock) {
        this.reviews = reviews;
        this.orders = orders;
        this.bookings = bookings;
        this.identity = identity;
        this.clock = clock;
    }

    public Review reviewOrder(UUID buyerId, UUID orderId, int rating, String text) {
        OrderClient.Order order = orders.order(orderId)
                .filter(o -> o.buyerId().equals(buyerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleźliśmy takiego zamówienia."));
        if (!"COMPLETED".equals(order.status())) {
            throw conflict("Opinię możesz wystawić po zrealizowaniu zamówienia.");
        }
        return save(new Review(order.channelId(), order.sellerId(), buyerId, name(buyerId), Review.Source.ORDER, orderId,
                order.title(), rating, text.trim(), clock.instant()), "To zamówienie ma już Twoją opinię.");
    }

    public Review reviewBooking(UUID customerId, UUID bookingId, int rating, String text) {
        BookingClient.Booking booking = bookings.booking(bookingId)
                .filter(b -> b.customerId().equals(customerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleźliśmy takiej rezerwacji."));
        if (!"COMPLETED".equals(booking.status())) {
            throw conflict("Opinię możesz wystawić po wykonaniu usługi.");
        }
        return save(new Review(booking.channelId(), booking.providerId(), customerId, name(customerId), Review.Source.BOOKING, bookingId,
                booking.title(), rating, text.trim(), clock.instant()), "Ta rezerwacja ma już Twoją opinię.");
    }

    /** Odpowiedź sprzedawcy: raz, tylko właściciel kanału, którego dotyczy opinia. */
    public Review reply(UUID sellerId, UUID reviewId, String text) {
        Review review = reviews.findById(reviewId).filter(r -> r.getSellerId().equals(sellerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleźliśmy takiej opinii."));
        if (review.getReplyText() != null) {
            throw conflict("Na tę opinię już odpowiedziałeś.");
        }
        review.reply(text.trim(), clock.instant());
        return reviews.save(review);
    }

    public List<Review> ofChannel(UUID channelId, int offset, int limit) {
        return reviews.findOfChannel(channelId, offset, limit);
    }

    public Summary summary(UUID channelId) {
        Map<Integer, Integer> distribution = new HashMap<>();
        for (int stars = 1; stars <= 5; stars++) {
            distribution.put(stars, 0);
        }
        int count = 0;
        int sum = 0;
        for (Object[] row : reviews.distribution(channelId)) {
            int stars = ((Number) row[0]).intValue();
            int n = ((Number) row[1]).intValue();
            distribution.put(stars, n);
            count += n;
            sum += stars * n;
        }
        return new Summary(average(sum, count), count, distribution);
    }

    /** Oceny wielu kanałów naraz (karty ofert). Kanały bez opinii mają 0 / 0. */
    public List<Rating> ratings(Collection<UUID> channelIds) {
        Map<UUID, Rating> found = new HashMap<>();
        for (Object[] row : reviews.totals(channelIds)) {
            int count = ((Number) row[1]).intValue();
            found.put((UUID) row[0], new Rating((UUID) row[0], average(((Number) row[2]).longValue(), count), count));
        }
        return channelIds.stream().distinct().map(id -> found.getOrDefault(id, new Rating(id, 0, 0))).toList();
    }

    /** Opinie, o których order-service lub booking-service jeszcze nie wie (był niedostępny przy zapisie). */
    @Scheduled(fixedDelayString = "${rynek.reviews.notify-retry:60s}", initialDelayString = "${rynek.reviews.notify-retry:60s}")
    public void notifySources() {
        for (UUID id : reviews.findUnnotified()) {
            reviews.findById(id).ifPresent(this::notifySource);
        }
    }

    private Review save(Review review, String duplicate) {
        if (reviews.existsBySourceTypeAndSourceId(review.getSourceType(), review.getSourceId())) {
            throw conflict(duplicate);
        }
        Review saved;
        try {
            saved = reviews.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            // Dwa żądania naraz: unikalny klucz (źródło) wygrywa tylko jednemu.
            throw conflict(duplicate);
        }
        notifySource(saved);
        return saved;
    }

    private void notifySource(Review review) {
        boolean ok = review.getSourceType() == Review.Source.ORDER ? orders.reviewed(review.getSourceId()) : bookings.reviewed(review.getSourceId());
        if (ok) {
            review.markSourceNotified();
            reviews.save(review);
        } else {
            log.info("Opinia {} czeka na zgłoszenie do serwisu źródłowego", review.getId());
        }
    }

    private String name(UUID userId) {
        return identity.displayName(userId).orElse("Kupujący");
    }

    private static double average(long sum, int count) {
        return count == 0 ? 0 : Math.round(sum * 10.0 / count) / 10.0;
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
