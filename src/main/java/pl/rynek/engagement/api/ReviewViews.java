package pl.rynek.engagement.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import pl.rynek.engagement.api.dto.CursorPage;
import pl.rynek.engagement.reviews.Review;
import pl.rynek.engagement.reviews.ReviewService;

/** Kształty odpowiedzi zgodne z typami {@code Review} i {@code RatingSummary} z rynek-frontend (models/engagement.ts). */
@Component
class ReviewViews {

    record Author(String name, String initials) {
    }

    record Reply(String text, Instant createdAt) {
    }

    record ReviewResponse(UUID id, Author author, int rating, String text, boolean verifiedPurchase, Instant createdAt,
                          String offerTitle, Reply reply) {
    }

    record SummaryResponse(double average, int count, Map<String, Integer> distribution) {
    }

    ReviewResponse one(Review r) {
        Reply reply = r.getReplyText() == null ? null : new Reply(r.getReplyText(), r.getReplyAt());
        return new ReviewResponse(r.getId(), new Author(r.getAuthorName(), initials(r.getAuthorName())), r.getRating(), r.getText(),
                r.isVerified(), r.getCreatedAt(), r.getOfferTitle(), reply);
    }

    CursorPage<ReviewResponse> page(String cursor, Integer limit, Cursors.Fetch<Review> fetch) {
        return Cursors.page(cursor, limit, fetch, this::one);
    }

    SummaryResponse summary(ReviewService.Summary s) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (int stars = 5; stars >= 1; stars--) {
            distribution.put(String.valueOf(stars), s.distribution().getOrDefault(stars, 0));
        }
        return new SummaryResponse(s.average(), s.count(), distribution);
    }

    static String initials(String name) {
        String[] parts = name == null ? new String[0] : name.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length && out.length() < 2; i++) {
            if (!parts[i].isEmpty()) {
                out.appendCodePoint(Character.toUpperCase(parts[i].codePointAt(0)));
            }
        }
        return out.isEmpty() ? "?" : out.toString();
    }
}
