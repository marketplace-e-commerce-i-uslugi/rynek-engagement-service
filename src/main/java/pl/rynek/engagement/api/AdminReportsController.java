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
import pl.rynek.engagement.api.dto.AdminDtos.PreviewView;
import pl.rynek.engagement.api.dto.AdminDtos.RejectReportRequest;
import pl.rynek.engagement.api.dto.AdminDtos.ReportView;
import pl.rynek.engagement.api.dto.AdminDtos.ResolveReportRequest;
import pl.rynek.engagement.api.dto.CursorPage;
import pl.rynek.engagement.reports.AdminReportService;
import pl.rynek.engagement.reports.AdminReportService.Row;

/** Moderacja zgłoszeń treści. Dostęp: admin (rola {@code ADMIN} w tokenie, nadawana poza aplikacją). */
@RestController
public class AdminReportsController {

    private final AdminReportService reports;

    public AdminReportsController(AdminReportService reports) {
        this.reports = reports;
    }

    /**
     * Kolejka zgłoszeń: {@code status} (OPEN, RESOLVED, REJECTED) i {@code type} (OFFER, QUESTION, REVIEW, CHAT) są opcjonalne. Otwarte od
     * najstarszego, rozpatrzone od ostatnio zamkniętych. Dostęp: admin.
     */
    @GetMapping("/api/v1/admin/reports")
    public CursorPage<ReportView> listReports(@AuthenticationPrincipal Jwt token, @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String type, @RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) Integer limit) {
        requireAdmin(token);
        return Cursors.page(cursor, limit, (offset, size) -> reports.list(status, type, offset, size), AdminReportsController::view);
    }

    /**
     * Odrzucenie zgłoszenia (treść jest w porządku); obejmuje wszystkie otwarte zgłoszenia o tej treści. Dostęp: admin.
     */
    @PostMapping("/api/v1/admin/reports/{reportId}/reject")
    public ReportView rejectReport(@AuthenticationPrincipal Jwt token, @PathVariable String reportId,
                                   @Valid @RequestBody(required = false) RejectReportRequest request) {
        UUID admin = requireAdmin(token);
        return view(reports.reject(admin, reportId(reportId), request == null ? null : request.note()));
    }

    /**
     * Rozpatrzenie: {@code removeContent} usuwa pytanie albo opinię i blokuje ofertę (dla rozmów 400); bez niego zgłoszenia tylko się
     * zamykają. Obejmuje wszystkie otwarte zgłoszenia o tej treści. Dostęp: admin.
     */
    @PostMapping("/api/v1/admin/reports/{reportId}/resolve")
    public ReportView resolveReport(@AuthenticationPrincipal Jwt token, @PathVariable String reportId, @Valid @RequestBody ResolveReportRequest request) {
        UUID admin = requireAdmin(token);
        return view(reports.resolve(admin, reportId(reportId), request.note(), request.removeContent()));
    }

    /**
     * Zdjęcie blokady z oferty zablokowanej po zgłoszeniu: zostaje wstrzymana, sprzedawca publikuje ją sam. Dostęp: admin.
     */
    @PostMapping("/api/v1/admin/offers/{offerId}/unblock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblockOffer(@AuthenticationPrincipal Jwt token, @PathVariable String offerId) {
        requireAdmin(token);
        UUID id;
        try {
            id = UUID.fromString(offerId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono oferty.");
        }
        reports.unblockOffer(id);
    }

    private static UUID requireAdmin(Jwt token) {
        List<String> roles = token.getClaimAsStringList("roles");
        if (roles == null || !roles.contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ta operacja wymaga uprawnień administratora.");
        }
        return UUID.fromString(token.getSubject());
    }

    private static UUID reportId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono zgłoszenia.");
        }
    }

    private static ReportView view(Row row) {
        var r = row.report();
        var p = row.preview();
        return new ReportView(r.getId(), r.getTargetType().name(), r.getTargetId(), r.getReason(), r.getStatus(), r.getCreatedAt(), row.reportsCount(),
                new PreviewView(p.title(), p.text(), p.author(), p.link(), p.available()), r.getResolvedAt(), r.isContentRemoved(), r.getNote());
    }
}
