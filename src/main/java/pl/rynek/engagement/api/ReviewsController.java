package pl.rynek.engagement.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.engagement.api.dto.ReplyToReviewRequest;

import static pl.rynek.engagement.common.NotImplemented.notImplemented;

@RestController
public class ReviewsController {

    /**
     * Odpowiedź sprzedawcy na opinię. Dostęp: seller.
     */
    @PostMapping("/api/v1/reviews/{reviewId}/reply")
    public void replyToReview(@PathVariable String reviewId, @RequestBody ReplyToReviewRequest request) {
        throw notImplemented();
    }
}
