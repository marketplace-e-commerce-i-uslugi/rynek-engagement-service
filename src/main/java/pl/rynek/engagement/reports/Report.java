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

    public UUID getId() { return id; }
    public UUID getReporterId() { return reporterId; }
    public TargetType getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
