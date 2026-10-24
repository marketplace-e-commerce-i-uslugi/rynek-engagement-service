package pl.rynek.engagement.api;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.engagement.api.dto.CreateQuestionRequest;

import static pl.rynek.engagement.common.NotImplemented.notImplemented;

@RestController
public class OffersController {

    /**
     * Pytania i odpowiedzi pod ofertą. Dostęp: pub.
     */
    @GetMapping("/api/v1/offers/{offerId}/questions")
    public void listQuestions(@PathVariable String offerId, @RequestParam(required = false) String sort, @RequestParam(required = false) String cursor) {
        throw notImplemented();
    }

    /**
     * Zadanie pytania. Dostęp: user.
     */
    @PostMapping("/api/v1/offers/{offerId}/questions")
    public void createQuestion(@PathVariable String offerId, @RequestBody CreateQuestionRequest request) {
        throw notImplemented();
    }

    /**
     * Polubienie oferty / Shorta. Dostęp: user.
     */
    @PutMapping("/api/v1/offers/{offerId}/like")
    public void likeOffer(@PathVariable String offerId) {
        throw notImplemented();
    }

    /**
     * Cofnięcie polubienia. Dostęp: user.
     */
    @DeleteMapping("/api/v1/offers/{offerId}/like")
    public void unlikeOffer(@PathVariable String offerId) {
        throw notImplemented();
    }
}
