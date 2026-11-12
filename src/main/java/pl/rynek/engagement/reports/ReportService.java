package pl.rynek.engagement.reports;

import java.time.Clock;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.engagement.clients.CatalogClient;
import pl.rynek.engagement.questions.QuestionRepository;
import pl.rynek.engagement.reviews.ReviewRepository;

/**
 * Zgłoszenia treści. Treść musi istnieć (oferta w catalog, pytanie i opinia u nas) i nie może być własna: autor pytania lub
 * opinii oraz właściciel oferty jej nie zgłaszają. Sprzedawca może zgłosić pytanie albo opinię o swoim kanale. Czat
 * (chat-service jeszcze nie istnieje) przyjmujemy bez sprawdzania. Jedno zgłoszenie użytkownika na treść.
 */
@Service
public class ReportService {

    private final ReportRepository reports;
    private final CatalogClient catalog;
    private final QuestionRepository questions;
    private final ReviewRepository reviews;
    private final Clock clock;

    public ReportService(ReportRepository reports, CatalogClient catalog, QuestionRepository questions, ReviewRepository reviews, Clock clock) {
        this.reports = reports;
        this.catalog = catalog;
        this.questions = questions;
        this.reviews = reviews;
        this.clock = clock;
    }

    public Report report(UUID reporterId, Report.TargetType type, UUID targetId, String reason) {
        UUID author = switch (type) {
            case OFFER -> catalog.offer(targetId).filter(CatalogClient.OfferInfo::available)
                    .orElseThrow(() -> notFound("Nie znaleźliśmy takiej oferty.")).ownerId();
            case QUESTION -> questions.findById(targetId).orElseThrow(() -> notFound("Nie znaleźliśmy takiego pytania.")).getAuthorId();
            case REVIEW -> reviews.findById(targetId).orElseThrow(() -> notFound("Nie znaleźliśmy takiej opinii.")).getAuthorId();
            case CHAT -> null;
        };
        if (reporterId.equals(author)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nie możesz zgłosić własnej treści.");
        }
        if (reports.existsByReporterIdAndTargetTypeAndTargetId(reporterId, type, targetId)) {
            throw duplicate();
        }
        try {
            return reports.saveAndFlush(new Report(reporterId, type, targetId, reason.trim(), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            throw duplicate();
        }
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Już zgłosiłeś tę treść. Sprawdzimy ją.");
    }
}
