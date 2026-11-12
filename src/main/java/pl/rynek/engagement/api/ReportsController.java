package pl.rynek.engagement.api;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.engagement.api.dto.CreateReportRequest;
import pl.rynek.engagement.reports.ReportService;

@RestController
public class ReportsController {

    private final ReportService reports;

    public ReportsController(ReportService reports) {
        this.reports = reports;
    }

    /** Zgłoszenie treści (oferta, pytanie, opinia, czat), raz na treść. Dostęp: user. */
    @PostMapping("/api/v1/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public void createReport(@AuthenticationPrincipal Jwt token, @Valid @RequestBody CreateReportRequest request) {
        reports.report(UUID.fromString(token.getSubject()), request.targetType(), request.targetId(), request.reason());
    }
}
