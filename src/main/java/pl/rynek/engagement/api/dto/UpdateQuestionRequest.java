package pl.rynek.engagement.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Edycja własnego pytania (do czasu odpowiedzi sprzedawcy). */
public record UpdateQuestionRequest(
        @NotBlank(message = "Napisz pytanie.") @Size(max = 500, message = "Pytanie może mieć najwyżej 500 znaków.") String text) {
}
