package pl.rynek.engagement.likes;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Polubienie oferty przez użytkownika (klucz: oferta i użytkownik). */
@Entity
@Table(name = "offer_likes")
@IdClass(OfferLike.Key.class)
public class OfferLike {

    public record Key(UUID offerId, UUID userId) implements Serializable {
    }

    @Id
    @Column(name = "offer_id")
    private UUID offerId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "created_at")
    private Instant createdAt;

    protected OfferLike() {
    }

    public UUID getOfferId() {
        return offerId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
