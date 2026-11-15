package pl.rynek.engagement.api.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Size;

/** Kształty panelu moderacji zgłoszeń (rynek-frontend/src/app/core/api/models/engagement.ts). */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** {@code available}: treść nadal widoczna w serwisie; {@code link}: ścieżka w aplikacji albo {@code null}. */
    public record PreviewView(String title, String text, String author, String link, boolean available) {
    }

    /**
     * Zgłoszenie w kolejce. {@code reportsCount}: ile razy zgłoszono tę treść (łącznie); {@code status}: OPEN, RESOLVED albo REJECTED;
     * {@code contentRemoved}: treść usunięta albo oferta zablokowana.
     */
    public record ReportView(UUID id, String targetType, UUID targetId, String reason, String status, Instant createdAt, int reportsCount,
                             PreviewView preview, Instant resolvedAt, boolean contentRemoved, String note) {
    }

    public record RejectReportRequest(@Size(max = 500, message = "Notatka może mieć najwyżej 500 znaków.") String note) {
    }

    public record ResolveReportRequest(@Size(max = 500, message = "Notatka może mieć najwyżej 500 znaków.") String note, boolean removeContent) {
    }
}
