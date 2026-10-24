package pl.rynek.engagement.api;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.engagement.api.dto.AnswerQuestionRequest;
import pl.rynek.engagement.api.dto.UpdateQuestionRequest;

import static pl.rynek.engagement.common.NotImplemented.notImplemented;

@RestController
public class QuestionsController {

    /**
     * Odpowiedź sprzedawcy (wyróżniona). Dostęp: seller.
     */
    @PostMapping("/api/v1/questions/{questionId}/answers")
    public void answerQuestion(@PathVariable String questionId, @RequestBody AnswerQuestionRequest request) {
        throw notImplemented();
    }

    /**
     * Edycja własnego pytania. Dostęp: user.
     */
    @PatchMapping("/api/v1/questions/{questionId}")
    public void updateQuestion(@PathVariable String questionId, @RequestBody UpdateQuestionRequest request) {
        throw notImplemented();
    }

    /**
     * Usunięcie własnego pytania. Dostęp: user.
     */
    @DeleteMapping("/api/v1/questions/{questionId}")
    public void deleteQuestion(@PathVariable String questionId) {
        throw notImplemented();
    }

    /**
     * Polub pytanie („przydatne”). Dostęp: user.
     */
    @PutMapping("/api/v1/questions/{questionId}/like")
    public void likeQuestion(@PathVariable String questionId) {
        throw notImplemented();
    }
}
