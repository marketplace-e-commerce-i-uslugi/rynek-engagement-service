package pl.rynek.engagement.api;

import java.util.Optional;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.engagement.api.QuestionViews.QuestionResponse;
import pl.rynek.engagement.api.dto.AnswerQuestionRequest;
import pl.rynek.engagement.api.dto.UpdateQuestionRequest;
import pl.rynek.engagement.questions.QuestionService;

/** Odpowiedź sprzedawcy, edycja i usunięcie własnego pytania, „Przydatne”. */
@RestController
public class QuestionsController {

    private final QuestionService questions;
    private final QuestionViews views;

    public QuestionsController(QuestionService questions, QuestionViews views) {
        this.questions = questions;
        this.views = views;
    }

    /** Odpowiedź właściciela oferty (jedna, wyróżniona). Dostęp: seller. */
    @PostMapping("/api/v1/questions/{questionId}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionResponse answerQuestion(@AuthenticationPrincipal Jwt token, @PathVariable UUID questionId,
                                           @Valid @RequestBody AnswerQuestionRequest request) {
        return views.one(questions.answer(user(token), questionId, request.text()), Optional.empty());
    }

    /** Edycja własnego pytania, dopóki nie ma odpowiedzi. Dostęp: user. */
    @PatchMapping("/api/v1/questions/{questionId}")
    public QuestionResponse updateQuestion(@AuthenticationPrincipal Jwt token, @PathVariable UUID questionId,
                                           @Valid @RequestBody UpdateQuestionRequest request) {
        UUID user = user(token);
        return views.one(questions.edit(user, questionId, request.text()), Optional.of(user));
    }

    /** Usunięcie własnego pytania razem z odpowiedzią. Dostęp: user. */
    @DeleteMapping("/api/v1/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuestion(@AuthenticationPrincipal Jwt token, @PathVariable UUID questionId) {
        questions.delete(user(token), questionId);
    }

    /** „Przydatne”: jedno polubienie na użytkownika, ponowienie niczego nie zmienia. Dostęp: user. */
    @PutMapping("/api/v1/questions/{questionId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void likeQuestion(@AuthenticationPrincipal Jwt token, @PathVariable UUID questionId) {
        questions.like(user(token), questionId);
    }

    private static UUID user(Jwt token) {
        return UUID.fromString(token.getSubject());
    }
}
