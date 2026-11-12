package pl.rynek.engagement.likes;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OfferLikeRepository extends JpaRepository<OfferLike, OfferLike.Key> {

    /** Wstawia polubienie; ponowienie przez tego samego użytkownika niczego nie zmienia. */
    @Modifying
    @Query(value = "insert into offer_likes (offer_id, user_id, created_at) values (:offerId, :userId, :now) on conflict do nothing", nativeQuery = true)
    int add(UUID offerId, UUID userId, Instant now);

    @Modifying
    @Query(value = "delete from offer_likes where offer_id = :offerId and user_id = :userId", nativeQuery = true)
    int remove(UUID offerId, UUID userId);
}
