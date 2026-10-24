package pl.rynek.engagement.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static pl.rynek.engagement.common.NotImplemented.notImplemented;

@RestController
public class ChannelsController {

    /**
     * Opinie o sprzedawcy. Dostęp: pub.
     */
    @GetMapping("/api/v1/channels/{channelId}/reviews")
    public void listReviews(@PathVariable String channelId, @RequestParam(required = false) String cursor) {
        throw notImplemented();
    }

    /**
     * Średnia i rozkład ocen. Dostęp: pub.
     */
    @GetMapping("/api/v1/channels/{channelId}/rating-summary")
    public void getRatingSummary(@PathVariable String channelId) {
        throw notImplemented();
    }
}
