package ai_soc_backend.incident.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_detection_work")
public class AlertDetectionWork {

    @Id
    @Column(name = "alert_pk", nullable = false)
    private Long alertPk;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "attempt_count", nullable = false)
    private short attemptCount;

    @Column(name = "available_at", nullable = false, columnDefinition = "timestamptz")
    private Instant availableAt;

    @Column(name = "lease_expires_at", columnDefinition = "timestamptz")
    private Instant leaseExpiresAt;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "last_error_detail", length = 1000)
    private String lastErrorDetail;

    @Column(name = "result_code", length = 64)
    private String resultCode;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    @Column(name = "finished_at", columnDefinition = "timestamptz")
    private Instant finishedAt;

    @Column(name = "operator_requeue_count", nullable = false)
    private int operatorRequeueCount;

    @Column(name = "last_requeued_at", columnDefinition = "timestamptz")
    private Instant lastRequeuedAt;

    @Column(name = "last_requeued_by", length = 128)
    private String lastRequeuedBy;

    protected AlertDetectionWork() {
    }

    public AlertDetectionWork(Long alertPk) {
        this.alertPk = alertPk;
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        if (availableAt == null) availableAt = now;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    public Long getAlertPk() { return alertPk; }
    public void setAlertPk(Long alertPk) { this.alertPk = alertPk; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public short getAttemptCount() { return attemptCount; }
    public void setAttemptCount(short attemptCount) { this.attemptCount = attemptCount; }
    public Instant getAvailableAt() { return availableAt; }
    public void setAvailableAt(Instant availableAt) { this.availableAt = availableAt; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public void setLeaseExpiresAt(Instant leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; }
    public UUID getClaimToken() { return claimToken; }
    public void setClaimToken(UUID claimToken) { this.claimToken = claimToken; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public String getLastErrorDetail() { return lastErrorDetail; }
    public void setLastErrorDetail(String lastErrorDetail) { this.lastErrorDetail = lastErrorDetail; }
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public int getOperatorRequeueCount() { return operatorRequeueCount; }
    public void setOperatorRequeueCount(int operatorRequeueCount) { this.operatorRequeueCount = operatorRequeueCount; }
    public Instant getLastRequeuedAt() { return lastRequeuedAt; }
    public void setLastRequeuedAt(Instant lastRequeuedAt) { this.lastRequeuedAt = lastRequeuedAt; }
    public String getLastRequeuedBy() { return lastRequeuedBy; }
    public void setLastRequeuedBy(String lastRequeuedBy) { this.lastRequeuedBy = lastRequeuedBy; }
}
