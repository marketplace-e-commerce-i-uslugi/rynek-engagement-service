package pl.rynek.engagement.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Opinia po zakupie ({@code POST /orders/{id}/review}) albo po usłudze ({@code POST /bookings/{id}/review}). */
public record ReviewRequest(
        @NotNull(message = "Wybierz ocenę od 1 do 5.") @Min(value = 1, message = "Wybierz ocenę od 1 do 5.")
        @Max(value = 5, message = "Wybierz ocenę od 1 do 5.") Integer rating,
        @NotBlank(message = "Napisz kilka słów o zakupie.") @Size(max = 2000, message = "Opinia może mieć najwyżej 2000 znaków.") String text) {
}
