package pl.rynek.engagement.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Odpowiedź sprzedawcy na pytanie. */
public record AnswerQuestionRequest(
        @NotBlank(message = "Napisz odpowiedź.") @Size(max = 2000, message = "Odpowiedź może mieć najwyżej 2000 znaków.") String text) {
}
