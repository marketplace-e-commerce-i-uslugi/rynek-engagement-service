package pl.rynek.engagement.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.engagement.api.ReviewViews.ReviewResponse;
import pl.rynek.engagement.api.ReviewViews.SummaryResponse;
import pl.rynek.engagement.api.dto.CursorPage;
import pl.rynek.engagement.api.dto.ReplyToReviewRequest;
import pl.rynek.engagement.api.dto.ReviewRequest;
import pl.rynek.engagement.reviews.ReviewService;

/**
 * Opinie o sprzedawcy: lista i średnia (pub), opinia po zakupie lub usłudze (user), odpowiedź sprzedawcy (seller),
 * oceny kanałów dla kart ofert (int).
 */
@RestController
public class ReviewsController {

    static final int MAX_BATCH = 100;

    private final ReviewService reviews;
    private final ReviewViews views;

    public ReviewsController(ReviewService reviews, ReviewViews views) {
        this.reviews = reviews;
        this.views = views;
    }

    /** Opinie o sprzedawcy, od najnowszej. Dostęp: pub. */
    @GetMapping("/api/v1/channels/{channelId}/reviews")
    public CursorPage<ReviewResponse> listReviews(@PathVariable UUID channelId, @RequestParam(required = false) String cursor,
                                                  @RequestParam(required = false) Integer limit) {
        return views.page(cursor, limit, (offset, n) -> reviews.ofChannel(channelId, offset, n));
    }

    /** Średnia i rozkład ocen (5…1 gwiazdek). Dostęp: pub. */
    @GetMapping("/api/v1/channels/{channelId}/rating-summary")
    public SummaryResponse getRatingSummary(@PathVariable UUID channelId) {
        return views.summary(reviews.summary(channelId));
    }

    /** Opinia zweryfikowanego zakupu: tylko kupujący, tylko zrealizowane zamówienie, raz. Dostęp: user. */
    @PostMapping("/api/v1/orders/{orderId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse reviewOrder(@AuthenticationPrincipal Jwt token, @PathVariable UUID orderId, @Valid @RequestBody ReviewRequest request) {
        return views.one(reviews.reviewOrder(userId(token), orderId, request.rating(), request.text()));
    }

    /** Opinia po wykonanej usłudze: tylko klient, tylko wykonana rezerwacja, raz. Dostęp: user. */
    @PostMapping("/api/v1/bookings/{bookingId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse reviewBooking(@AuthenticationPrincipal Jwt token, @PathVariable UUID bookingId, @Valid @RequestBody ReviewRequest request) {
        return views.one(reviews.reviewBooking(userId(token), bookingId, request.rating(), request.text()));
    }

    /** Odpowiedź sprzedawcy na opinię o jego kanale (jedna). Dostęp: seller. */
    @PostMapping("/api/v1/reviews/{reviewId}/reply")
    public ReviewResponse replyToReview(@AuthenticationPrincipal Jwt token, @PathVariable UUID reviewId, @Valid @RequestBody ReplyToReviewRequest request) {
        return views.one(reviews.reply(userId(token), reviewId, request.text()));
    }

    /** Wsadowo: oceny kanałów ({@code ?channelIds=a,b}, najwyżej {@value #MAX_BATCH}). Dostęp: int. */
    @GetMapping("/internal/ratings")
    public List<ReviewService.Rating> getRatingsForChannels(@RequestParam(required = false) List<UUID> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return List.of();
        }
        if (channelIds.size() > MAX_BATCH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Najwyżej " + MAX_BATCH + " kanałów naraz.");
        }
        return reviews.ratings(channelIds);
    }

    private static UUID userId(Jwt token) {
        return UUID.fromString(token.getSubject());
    }
}
