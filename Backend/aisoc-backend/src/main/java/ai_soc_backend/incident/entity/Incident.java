package ai_soc_backend.incident.entity;

import jakarta.persistence.*;


import java.time.LocalDateTime;

@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;

    @Column(name = "incident_id",nullable = false,unique = true)
    private String incidentId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String severity;

    private String host;

    private String detection;

    private LocalDateTime timestamp;

    @Column(name = "correlation_key", length = 64)
    private String correlationKey;

    public Incident() {
    }

    public Incident(
            String incidentId,
            String title,
            String severity,
            String status,
            String host,
            String detection,
            LocalDateTime timestamp
    ) {
        this.incidentId = incidentId;
        this.title = title;
        this.severity = severity;
        this.status = status;
        this.host = host;
        this.detection = detection;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return Id;
    }

    public void setId(Long id) {
        Id = id;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getDetection() {
        return detection;
    }

    public void setDetection(String detection) {
        this.detection = detection;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getCorrelationKey() { return correlationKey; }
    public void setCorrelationKey(String correlationKey) { this.correlationKey = correlationKey; }
}
