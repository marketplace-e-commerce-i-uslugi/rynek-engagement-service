package pl.rynek.engagement.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.rynek.engagement.reports.Report;

/** Zgłoszenie treści: oferty, pytania, opinii albo czatu. */
public record CreateReportRequest(
        @NotNull(message = "Wskaż, co zgłaszasz.") Report.TargetType targetType,
        @NotNull(message = "Wskaż, co zgłaszasz.") UUID targetId,
        @NotBlank(message = "Wybierz powód zgłoszenia.") @Size(max = 1000, message = "Powód może mieć najwyżej 1000 znaków.") String reason) {
}
