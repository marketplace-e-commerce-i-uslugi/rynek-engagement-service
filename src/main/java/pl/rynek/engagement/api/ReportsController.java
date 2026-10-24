package pl.rynek.engagement.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.engagement.api.dto.CreateReportRequest;

import static pl.rynek.engagement.common.NotImplemented.notImplemented;

@RestController
public class ReportsController {

    /**
     * Zgłoszenie treści (oferta, pytanie, opinia, czat). Dostęp: user.
     */
    @PostMapping("/api/v1/reports")
    public void createReport(@RequestBody CreateReportRequest request) {
        throw notImplemented();
    }
}
