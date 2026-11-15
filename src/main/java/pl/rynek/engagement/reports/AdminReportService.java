package pl.rynek.engagement.reports;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.engagement.clients.CatalogClient;
import pl.rynek.engagement.clients.CatalogClient.OfferInfo;
import pl.rynek.engagement.notifications.EngagementNotifications;
import pl.rynek.engagement.questions.Question;
import pl.rynek.engagement.questions.QuestionRepository;
import pl.rynek.engagement.reports.Report.TargetType;
import pl.rynek.engagement.reviews.Review;
import pl.rynek.engagement.reviews.ReviewRepository;

/**
 * Moderacja zgłoszeń treści (administrator). Rozstrzygnięcie dotyczy treści, a nie pojedynczego zgłoszenia: obejmuje wszystkie otwarte
 * zgłoszenia o niej i powiadamia ich autorów. „Usuń treść” usuwa pytanie albo opinię, a ofertę blokuje w catalog (wstrzymana, bez
 * możliwości ponownej publikacji przez sprzedawcę). Rozmów (CHAT) nie usuwamy z tego panelu.
 */
@Service
public class AdminReportService {

    /** Podgląd treści w kolejce moderatora; {@code available}: treść nadal jest widoczna w serwisie. */
    public record Preview(String title, String text, String author, String link, boolean available) {
    }

    public record Row(Report report, int reportsCount, Preview preview) {
    }

    private final ReportRepository reports;
    private final QuestionRepository questions;
    private final ReviewRepository reviews;
    private final CatalogClient catalog;
    private final EngagementNotifications notices;
    private final Clock clock;

    public AdminReportService(ReportRepository reports, QuestionRepository questions, ReviewRepository reviews, CatalogClient catalog,
                              EngagementNotifications notices, Clock clock) {
        this.reports = reports;
        this.questions = questions;
        this.reviews = reviews;
        this.catalog = catalog;
        this.notices = notices;
        this.clock = clock;
    }

    /** Strona kolejki; {@code status} (OPEN, RESOLVED, REJECTED) i {@code type} są opcjonalne, zły filtr to 400. */
    @Transactional(readOnly = true)
    public List<Row> list(String status, String type, int offset, int limit) {
        String statusFilter = status == null || status.isBlank() ? null : status.toUpperCase();
        if (statusFilter != null && !List.of("OPEN", "RESOLVED", "REJECTED").contains(statusFilter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieznany status. Wybierz OPEN, RESOLVED albo REJECTED.");
        }
        String typeFilter = null;
        if (type != null && !type.isBlank()) {
            try {
                typeFilter = TargetType.valueOf(type.toUpperCase()).name();
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieznany rodzaj treści. Wybierz OFFER, QUESTION, REVIEW albo CHAT.");
            }
        }
        return reports.findForAdmin(statusFilter, typeFilter, offset, limit).stream()
                .map(r -> new Row(r, reports.countByTargetTypeAndTargetId(r.getTargetType(), r.getTargetId()), preview(r))).toList();
    }

    /** Odrzucenie: treść jest w porządku. Obejmuje wszystkie otwarte zgłoszenia o tej treści. */
    @Transactional
    public Row reject(UUID adminId, UUID reportId, String note) {
        Report report = open(reportId);
        List<Report> siblings = reports.findOpenOn(report.getTargetType(), report.getTargetId());
        siblings.forEach(r -> r.reject(adminId, note, clock.instant()));
        siblings.forEach(r -> notices.reportDecided(r.getReporterId(), r.getId(), "Sprawdziliśmy zgłoszoną treść i nie znaleźliśmy naruszenia regulaminu."));
        return row(report);
    }

    /**
     * Rozpatrzenie: z {@code removeContent} usuwa pytanie albo opinię, a ofertę blokuje (jej sprzedawca dostaje powiadomienie); bez niego
     * tylko zamyka zgłoszenia (np. gdy sprawa została załatwiona poza panelem). Niedostępny catalog daje 503 i nic się nie zmienia.
     */
    @Transactional
    public Row resolve(UUID adminId, UUID reportId, String note, boolean removeContent) {
        Report report = open(reportId);
        if (removeContent) {
            removeContent(report);
        }
        List<Report> siblings = reports.findOpenOn(report.getTargetType(), report.getTargetId());
        siblings.forEach(r -> r.resolve(adminId, note, removeContent, clock.instant()));
        String decision = !removeContent ? "Zgłoszenie zostało rozpatrzone. Dziękujemy za czujność."
                : report.getTargetType() == TargetType.OFFER ? "Zgłoszona oferta została zablokowana. Dziękujemy za czujność."
                : "Zgłoszona treść została usunięta. Dziękujemy za czujność.";
        siblings.forEach(r -> notices.reportDecided(r.getReporterId(), r.getId(), decision));
        return row(report);
    }

    /** Zdjęcie blokady z oferty: zostaje wstrzymana, a jej sprzedawca dostaje powiadomienie i publikuje ją sam. */
    @Transactional
    public void unblockOffer(UUID offerId) {
        Optional<OfferInfo> offer = catalog.offer(offerId);
        if (offer.isEmpty() || !catalog.setBlocked(offerId, false)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono oferty.");
        }
        notices.moderation(offer.get().ownerId(), "Zdjęliśmy blokadę z Twojej oferty", "Oferta „" + offer.get().title() + "” jest wstrzymana, możesz ją opublikować ponownie.",
                "/sprzedawca/oferty", "offer-" + offerId + "-unblocked-" + clock.instant().toEpochMilli());
    }

    private void removeContent(Report report) {
        switch (report.getTargetType()) {
            case QUESTION -> questions.findById(report.getTargetId()).ifPresent(q -> {
                questions.delete(q);
                notices.moderation(q.getAuthorId(), "Usunęliśmy Twoje pytanie", "Pytanie naruszało regulamin Rynku.", "/oferta/" + q.getOfferId(),
                        "report-" + report.getId() + "-removed");
            });
            case REVIEW -> reviews.findById(report.getTargetId()).ifPresent(r -> {
                reviews.delete(r);
                notices.moderation(r.getAuthorId(), "Usunęliśmy Twoją opinię", "Opinia naruszała regulamin Rynku.", "/", "report-" + report.getId() + "-removed");
            });
            case OFFER -> {
                Optional<OfferInfo> offer = catalog.offer(report.getTargetId());
                catalog.setBlocked(report.getTargetId(), true);
                offer.ifPresent(o -> notices.moderation(o.ownerId(), "Zablokowaliśmy Twoją ofertę", "Oferta „" + o.title()
                        + "” naruszała regulamin Rynku, więc jest wstrzymana i nie można jej opublikować.", "/sprzedawca/oferty", "report-" + report.getId() + "-removed"));
            }
            case CHAT -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rozmów nie usuwamy z tego panelu. Rozpatrz zgłoszenie bez usuwania treści albo je odrzuć.");
        }
    }

    private Report open(UUID reportId) {
        Report report = reports.findById(reportId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono zgłoszenia."));
        if (!report.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "To zgłoszenie zostało już rozpatrzone.");
        }
        return report;
    }

    private Row row(Report report) {
        return new Row(report, reports.countByTargetTypeAndTargetId(report.getTargetType(), report.getTargetId()), preview(report));
    }

    /** Podgląd treści; brak (usunięta) albo niedostępny serwis nie psuje kolejki. */
    private Preview preview(Report report) {
        try {
            return switch (report.getTargetType()) {
                case OFFER -> catalog.offer(report.getTargetId())
                        .map(o -> new Preview(o.title(), null, null, "/oferta/" + o.offerId(), o.available()))
                        .orElse(gone("Oferta niedostępna lub usunięta"));
                case QUESTION -> questions.findById(report.getTargetId())
                        .map((Question q) -> new Preview("Pytanie", q.getText(), q.getAuthorName(), "/oferta/" + q.getOfferId(), true))
                        .orElse(gone("Pytanie niedostępne lub usunięte"));
                case REVIEW -> reviews.findById(report.getTargetId())
                        .map((Review r) -> new Preview("Opinia " + r.getRating() + "/5: " + r.getOfferTitle(), r.getText(), r.getAuthorName(), null, true))
                        .orElse(gone("Opinia niedostępna lub usunięta"));
                case CHAT -> new Preview("Rozmowa", "Treść rozmowy jest widoczna tylko dla jej uczestników.", null, null, true);
            };
        } catch (ResponseStatusException e) {
            return new Preview("Podgląd niedostępny", null, null, null, true);
        }
    }

    private static Preview gone(String title) {
        return new Preview(title, null, null, null, false);
    }
}
