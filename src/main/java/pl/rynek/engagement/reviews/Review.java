package pl.rynek.engagement.reviews;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Opinia o sprzedawcy. Źródłem jest zrealizowane zamówienie albo wykonana usługa, więc każda opinia jest zweryfikowana. */
@Entity
@Table(name = "reviews")
public class Review {

    public enum Source { ORDER, BOOKING }

    @Id
    private UUID id;

    @Column(name = "channel_id")
    private UUID channelId;

    @Column(name = "seller_id")
    private UUID sellerId;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "author_name")
    private String authorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    private Source sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "offer_title")
    private String offerTitle;

    private int rating;

    private String text;

    private boolean verified = true;

    @Column(name = "reply_text")
    private String replyText;

    @Column(name = "reply_at")
    private Instant replyAt;

    @Column(name = "source_notified")
    private boolean sourceNotified;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Review() {
    }

    Review(UUID channelId, UUID sellerId, UUID authorId, String authorName, Source sourceType, UUID sourceId, String offerTitle,
           int rating, String text, Instant now) {
        this.id = UUID.randomUUID();
        this.channelId = channelId;
        this.sellerId = sellerId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.offerTitle = offerTitle;
        this.rating = rating;
        this.text = text;
        this.createdAt = now;
    }

    void reply(String text, Instant now) {
        this.replyText = text;
        this.replyAt = now;
    }

    void markSourceNotified() {
        this.sourceNotified = true;
    }

    public UUID getId() { return id; }
    public UUID getChannelId() { return channelId; }
    public UUID getSellerId() { return sellerId; }
    public UUID getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public Source getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public String getOfferTitle() { return offerTitle; }
    public int getRating() { return rating; }
    public String getText() { return text; }
    public boolean isVerified() { return verified; }
    public String getReplyText() { return replyText; }
    public Instant getReplyAt() { return replyAt; }
    public Instant getCreatedAt() { return createdAt; }
}
