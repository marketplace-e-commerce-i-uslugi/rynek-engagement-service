package pl.rynek.engagement.api;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.engagement.api.QuestionViews.QuestionResponse;
import pl.rynek.engagement.api.dto.CreateQuestionRequest;
import pl.rynek.engagement.api.dto.CursorPage;
import pl.rynek.engagement.config.Viewer;
import pl.rynek.engagement.likes.LikeService;
import pl.rynek.engagement.questions.QuestionService;

/** Pytania pod ofertą (pub / user) oraz liczba pytań dla catalog (int). Polubienia ofert i Shortów (user) i „czy polubiłem” dla catalog (int). */
@RestController
public class OffersController {

    static final int MAX_BATCH = 100;

    private final QuestionService questions;
    private final QuestionViews views;
    private final Viewer viewer;
    private final LikeService likes;

    public OffersController(QuestionService questions, QuestionViews views, Viewer viewer, LikeService likes) {
        this.questions = questions;
        this.views = views;
        this.viewer = viewer;
        this.likes = likes;
    }

    /** Pytania i odpowiedzi pod ofertą; {@code sort=newest} (domyślnie) albo {@code useful}. Dostęp: pub. */
    @GetMapping("/api/v1/offers/{offerId}/questions")
    public CursorPage<QuestionResponse> listQuestions(@PathVariable UUID offerId, @RequestParam(required = false) String sort,
                                                      @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit,
                                                      HttpServletRequest http) {
        QuestionService.Sort order = sort(sort);
        return views.page(cursor, limit, viewer.of(http), (offset, n) -> questions.ofOffer(offerId, order, offset, n));
    }

    /** Zadanie pytania; nie o własną ofertę. Dostęp: user. */
    @PostMapping("/api/v1/offers/{offerId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionResponse createQuestion(@AuthenticationPrincipal Jwt token, @PathVariable UUID offerId,
                                           @Valid @RequestBody CreateQuestionRequest request) {
        UUID user = UUID.fromString(token.getSubject());
        return views.one(questions.ask(user, offerId, request.text()), Optional.of(user));
    }

    /** Wsadowo: liczba pytań ofert ({@code ?offerIds=a,b}, najwyżej {@value #MAX_BATCH}). Dostęp: int. */
    @GetMapping("/internal/questions/counts")
    public List<Count> counts(@RequestParam(required = false) List<UUID> offerIds) {
        if (offerIds == null || offerIds.isEmpty()) {
            return List.of();
        }
        if (offerIds.size() > MAX_BATCH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Najwyżej " + MAX_BATCH + " ofert naraz.");
        }
        return questions.counts(offerIds).entrySet().stream().map(e -> new Count(e.getKey(), e.getValue())).toList();
    }

    public record Count(UUID offerId, int count) {
    }

    /** Polubienie oferty / Shorta: jedno na użytkownika, ponowienie niczego nie zmienia. Dostęp: user. */
    @PutMapping("/api/v1/offers/{offerId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void likeOffer(@AuthenticationPrincipal Jwt token, @PathVariable UUID offerId) {
        likes.like(UUID.fromString(token.getSubject()), offerId);
    }

    /** Cofnięcie polubienia (też gdy go nie było). Dostęp: user. */
    @DeleteMapping("/api/v1/offers/{offerId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlikeOffer(@AuthenticationPrincipal Jwt token, @PathVariable UUID offerId) {
        likes.unlike(UUID.fromString(token.getSubject()), offerId);
    }

    /** Czy użytkownik polubił ofertę (karta oferty pokazuje „Cofnij polubienie”). Dostęp: int. */
    @GetMapping("/internal/offers/{offerId}/liked")
    public Liked liked(@PathVariable UUID offerId, @RequestParam UUID userId) {
        return new Liked(likes.liked(userId, offerId));
    }

    public record Liked(boolean liked) {
    }

    private static QuestionService.Sort sort(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("newest")) {
            return QuestionService.Sort.NEWEST;
        }
        if (value.toLowerCase(Locale.ROOT).equals("useful")) {
            return QuestionService.Sort.USEFUL;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sortuj według „newest” albo „useful”.");
    }
}
