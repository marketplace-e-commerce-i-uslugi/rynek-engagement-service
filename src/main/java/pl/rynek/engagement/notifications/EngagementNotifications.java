package pl.rynek.engagement.notifications;

import java.util.UUID;

import org.springframework.stereotype.Component;
import pl.rynek.engagement.clients.NotificationClient;
import pl.rynek.engagement.questions.Question;
import pl.rynek.engagement.reviews.Review;

/**
 * Powiadomienia z pytań i opinii: sprzedawca dowiaduje się o nowym pytaniu i nowej opinii, autor o odpowiedzi na swoje
 * pytanie albo opinię. Pytania liczą się do kategorii „wiadomości”, opinie do „zamówień” albo „rezerwacji” (zależnie od
 * źródła). Klucz zdarzenia chroni przed dubletami przy ponowieniu.
 */
@Component
public class EngagementNotifications {

    private static final int EXCERPT = 140;

    private final NotificationClient notifications;

    public EngagementNotifications(NotificationClient notifications) {
        this.notifications = notifications;
    }

    public void questionAsked(Question q, String offerTitle) {
        notifications.send(q.getSellerId(), "messages", "Nowe pytanie: " + offerTitle, q.getAuthorName() + ": " + excerpt(q.getText()),
                offerLink(q.getOfferId()), "question-" + q.getId() + "-asked");
    }

    public void questionAnswered(Question q) {
        notifications.send(q.getAuthorId(), "messages", "Odpowiedź na Twoje pytanie", excerpt(q.getAnswerText()),
                offerLink(q.getOfferId()), "question-" + q.getId() + "-answered");
    }

    public void reviewed(Review r) {
        notifications.send(r.getSellerId(), category(r), "Nowa opinia: " + r.getRating() + "/5",
                r.getAuthorName() + " ocenił(a): " + r.getOfferTitle() + ".", "/sprzedawca/opinie", "review-" + r.getId() + "-created");
    }

    public void reviewReplied(Review r) {
        notifications.send(r.getAuthorId(), category(r), "Sprzedawca odpowiedział na Twoją opinię", excerpt(r.getReplyText()),
                r.getSourceType() == Review.Source.ORDER ? "/profil/zamowienia" : "/profil/rezerwacje", "review-" + r.getId() + "-replied");
    }

    private static String category(Review r) {
        return r.getSourceType() == Review.Source.ORDER ? "orders" : "bookings";
    }

    private static String offerLink(UUID offerId) {
        return "/oferta/" + offerId;
    }

    private static String excerpt(String text) {
        String clean = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        return clean.length() <= EXCERPT ? clean : clean.substring(0, EXCERPT - 1) + "…";
    }
}
