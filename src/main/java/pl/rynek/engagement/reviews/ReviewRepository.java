package pl.rynek.engagement.reviews;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsBySourceTypeAndSourceId(Review.Source sourceType, UUID sourceId);

    @Query(value = "select * from reviews where channel_id = :channelId order by created_at desc, id limit :limit offset :offset", nativeQuery = true)
    List<Review> findOfChannel(UUID channelId, int offset, int limit);

    /** Wiersze: ocena (1–5) i liczba opinii o tej ocenie. */
    @Query("select r.rating, count(r) from Review r where r.channelId = :channelId group by r.rating")
    List<Object[]> distribution(UUID channelId);

    /** Wiersze: kanał, liczba opinii i suma ocen. */
    @Query("select r.channelId, count(r), sum(r.rating) from Review r where r.channelId in :channelIds group by r.channelId")
    List<Object[]> totals(Collection<UUID> channelIds);

    @Query("select r.id from Review r where r.sourceNotified = false order by r.createdAt")
    List<UUID> findUnnotified();
}
