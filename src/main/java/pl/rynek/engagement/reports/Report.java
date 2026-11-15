package pl.rynek.engagement.reports;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Zgłoszenie treści przez użytkownika. Status {@code OPEN} do czasu rozpatrzenia (moderacja poza zakresem). */
@Entity
@Table(name = "reports")
public class Report {

    public enum TargetType { OFFER, QUESTION, REVIEW, CHAT }

    @Id
    private UUID id;

    @Column(name = "reporter_id")
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type")
    private TargetType targetType;

    @Column(name = "target_id")
    private UUID targetId;

    private String reason;

    private String status = "OPEN";

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    private String note;

    @Column(name = "content_removed")
    private boolean contentRemoved;

    protected Report() {
    }

    Report(UUID reporterId, TargetType targetType, UUID targetId, String reason, Instant now) {
        this.id = UUID.randomUUID();
        this.reporterId = reporterId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.createdAt = now;
    }

    public boolean isOpen() {
        return "OPEN".equals(status);
    }

    /** Rozpatrzone: {@code contentRemoved} oznacza usunięcie treści albo zablokowanie oferty. */
    public void resolve(UUID adminId, String note, boolean contentRemoved, Instant now) {
        close("RESOLVED", adminId, note, now);
        this.contentRemoved = contentRemoved;
    }

    /** Odrzucone: treść jest w porządku. */
    public void reject(UUID adminId, String note, Instant now) {
        close("REJECTED", adminId, note, now);
    }

    private void close(String status, UUID adminId, String note, Instant now) {
        this.status = status;
        this.resolvedBy = adminId;
        this.resolvedAt = now;
        this.note = note == null || note.isBlank() ? null : note.trim();
    }

    public UUID getId() { return id; }
    public UUID getReporterId() { return reporterId; }
    public TargetType getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getNote() { return note; }
    public boolean isContentRemoved() { return contentRemoved; }
}
