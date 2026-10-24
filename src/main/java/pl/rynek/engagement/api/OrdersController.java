package pl.rynek.engagement.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.engagement.api.dto.ReviewOrderRequest;

import static pl.rynek.engagement.common.NotImplemented.notImplemented;

@RestController
public class OrdersController {

    /**
     * Opinia po zakupie (opinia zweryfikowanego zakupu). Dostęp: user.
     */
    @PostMapping("/api/v1/orders/{orderId}/review")
    public void reviewOrder(@PathVariable String orderId, @RequestBody ReviewOrderRequest request) {
        throw notImplemented();
    }
}
