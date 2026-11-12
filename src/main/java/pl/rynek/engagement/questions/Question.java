package pl.rynek.engagement.questions;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Pytanie do oferty. Odpowiada na nie właściciel oferty (jedna odpowiedź). */
@Entity
@Table(name = "questions")
public class Question {

    @Id
    private UUID id;

    @Column(name = "offer_id")
    private UUID offerId;

    @Column(name = "seller_id")
    private UUID sellerId;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "author_name")
    private String authorName;

    private String text;

    @Column(name = "likes_count")
    private int likes;

    @Column(name = "answer_text")
    private String answerText;

    @Column(name = "answer_at")
    private Instant answerAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Question() {
    }

    Question(UUID offerId, UUID sellerId, UUID authorId, String authorName, String text, Instant now) {
        this.id = UUID.randomUUID();
        this.offerId = offerId;
        this.sellerId = sellerId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.text = text;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void edit(String text, Instant now) {
        this.text = text;
        this.updatedAt = now;
    }

    void answer(String text, Instant now) {
        this.answerText = text;
        this.answerAt = now;
        this.updatedAt = now;
    }

    public boolean answered() {
        return answerText != null;
    }

    public UUID getId() { return id; }
    public UUID getOfferId() { return offerId; }
    public UUID getSellerId() { return sellerId; }
    public UUID getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public String getText() { return text; }
    public int getLikes() { return likes; }
    public String getAnswerText() { return answerText; }
    public Instant getAnswerAt() { return answerAt; }
    public Instant getCreatedAt() { return createdAt; }
}
