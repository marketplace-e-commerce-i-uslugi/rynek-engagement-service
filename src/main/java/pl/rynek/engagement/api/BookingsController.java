package pl.rynek.engagement.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.engagement.api.dto.ReviewBookingRequest;

import static pl.rynek.engagement.common.NotImplemented.notImplemented;

@RestController
public class BookingsController {

    /**
     * Opinia po wykonanej usłudze. Dostęp: user.
     */
    @PostMapping("/api/v1/bookings/{bookingId}/review")
    public void reviewBooking(@PathVariable String bookingId, @RequestBody ReviewBookingRequest request) {
        throw notImplemented();
    }
}
