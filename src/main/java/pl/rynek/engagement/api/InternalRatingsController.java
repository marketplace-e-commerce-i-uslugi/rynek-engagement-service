package pl.rynek.engagement.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static pl.rynek.engagement.common.NotImplemented.notImplemented;

@RestController
public class InternalRatingsController {

    /**
     * Wsadowo: oceny dla kart ofert. Dostęp: int.
     */
    @GetMapping("/internal/ratings")
    public void getRatingsForChannels(@RequestParam(required = false) String channelIds) {
        throw notImplemented();
    }
}
