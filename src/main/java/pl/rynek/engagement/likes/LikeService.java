package pl.rynek.engagement.likes;

import java.time.Clock;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.engagement.clients.CatalogClient;

/** Polubienia ofert i Shortów: tylko opublikowane oferty, jedno polubienie na użytkownika, cofnięcie zawsze możliwe. */
@Service
public class LikeService {

    private final OfferLikeRepository likes;
    private final CatalogClient catalog;
    private final Clock clock;

    public LikeService(OfferLikeRepository likes, CatalogClient catalog, Clock clock) {
        this.likes = likes;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Transactional
    public void like(UUID userId, UUID offerId) {
        catalog.offer(offerId).filter(CatalogClient.OfferInfo::available)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleźliśmy takiej oferty."));
        likes.add(offerId, userId, clock.instant());
    }

    /** Cofnięcie nie sprawdza oferty: polubienie wstrzymanej albo usuniętej oferty też trzeba móc zdjąć. */
    @Transactional
    public void unlike(UUID userId, UUID offerId) {
        likes.remove(offerId, userId);
    }

    public boolean liked(UUID userId, UUID offerId) {
        return likes.existsById(new OfferLike.Key(offerId, userId));
    }
}
