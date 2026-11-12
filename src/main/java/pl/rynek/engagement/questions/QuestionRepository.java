package pl.rynek.engagement.questions;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    @Query(value = "select * from questions where offer_id = :offerId order by created_at desc, id limit :limit offset :offset", nativeQuery = true)
    List<Question> findNewest(UUID offerId, int offset, int limit);

    @Query(value = "select * from questions where offer_id = :offerId order by likes_count desc, created_at desc, id limit :limit offset :offset",
            nativeQuery = true)
    List<Question> findMostUseful(UUID offerId, int offset, int limit);

    /** Wiersze: oferta i liczba jej pytań. */
    @Query("select q.offerId, count(q) from Question q where q.offerId in :offerIds group by q.offerId")
    List<Object[]> counts(Collection<UUID> offerIds);

    /** 1: nowe polubienie, 0: użytkownik już polubił to pytanie. */
    @Modifying
    @Query(value = "insert into question_likes (question_id, user_id) values (:questionId, :userId) on conflict do nothing", nativeQuery = true)
    int addLike(UUID questionId, UUID userId);

    @Modifying(clearAutomatically = true)
    @Query(value = "update questions set likes_count = likes_count + 1 where id = :questionId", nativeQuery = true)
    void bumpLikes(UUID questionId);
}
