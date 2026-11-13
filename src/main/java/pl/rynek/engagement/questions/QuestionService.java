package pl.rynek.engagement.questions;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.engagement.clients.CatalogClient;
import pl.rynek.engagement.clients.IdentityClient;
import pl.rynek.engagement.notifications.EngagementNotifications;

/**
 * Pytania do ofert: pytać może każdy zalogowany poza właścicielem oferty, odpowiada właściciel (raz), autor edytuje
 * pytanie do czasu odpowiedzi i może je usunąć. „Przydatne” to jedno polubienie na użytkownika.
 */
@Service
public class QuestionService {

    public enum Sort { NEWEST, USEFUL }

    private final QuestionRepository questions;
    private final CatalogClient catalog;
    private final IdentityClient identity;
    private final EngagementNotifications notices;
    private final Clock clock;

    public QuestionService(QuestionRepository questions, CatalogClient catalog, IdentityClient identity, EngagementNotifications notices, Clock clock) {
        this.questions = questions;
        this.catalog = catalog;
        this.identity = identity;
        this.notices = notices;
        this.clock = clock;
    }

    public List<Question> ofOffer(UUID offerId, Sort sort, int offset, int limit) {
        return sort == Sort.USEFUL ? questions.findMostUseful(offerId, offset, limit) : questions.findNewest(offerId, offset, limit);
    }

    public Question ask(UUID userId, UUID offerId, String text) {
        CatalogClient.OfferInfo offer = catalog.offer(offerId).filter(CatalogClient.OfferInfo::available)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleźliśmy takiej oferty."));
        if (offer.ownerId().equals(userId)) {
            throw conflict("Nie możesz zadać pytania o własną ofertę.");
        }
        String name = identity.displayName(userId).orElse("Kupujący");
        Question asked = questions.save(new Question(offerId, offer.ownerId(), userId, name, text.trim(), clock.instant()));
        notices.questionAsked(asked, offer.title());
        return asked;
    }

    public Question edit(UUID userId, UUID questionId, String text) {
        Question question = own(userId, questionId);
        if (question.answered()) {
            throw conflict("Sprzedawca już odpowiedział, więc nie możesz zmienić pytania.");
        }
        question.edit(text.trim(), clock.instant());
        return questions.save(question);
    }

    public void delete(UUID userId, UUID questionId) {
        questions.delete(own(userId, questionId));
    }

    /** Odpowiedź właściciela oferty: raz, bez edycji. */
    public Question answer(UUID sellerId, UUID questionId, String text) {
        Question question = questions.findById(questionId).filter(q -> q.getSellerId().equals(sellerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleźliśmy takiego pytania."));
        if (question.answered()) {
            throw conflict("Na to pytanie już odpowiedziałeś.");
        }
        question.answer(text.trim(), clock.instant());
        Question answered = questions.save(question);
        notices.questionAnswered(answered);
        return answered;
    }

    /** „Przydatne”: ponowienie przez tego samego użytkownika niczego nie zmienia. */
    @Transactional
    public void like(UUID userId, UUID questionId) {
        if (!questions.existsById(questionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleźliśmy takiego pytania.");
        }
        if (questions.addLike(questionId, userId) > 0) {
            questions.bumpLikes(questionId);
        }
    }

    /** Liczba pytań na ofertę; oferty bez pytań mają 0. */
    public Map<UUID, Integer> counts(Collection<UUID> offerIds) {
        Map<UUID, Integer> counts = new HashMap<>();
        offerIds.forEach(id -> counts.put(id, 0));
        for (Object[] row : questions.counts(offerIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    private Question own(UUID userId, UUID questionId) {
        return questions.findById(questionId).filter(q -> q.getAuthorId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleźliśmy takiego pytania."));
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
