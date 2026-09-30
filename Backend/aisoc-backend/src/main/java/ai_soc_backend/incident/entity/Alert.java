package ai_soc_backend.incident.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", unique = true, nullable = false)
    private String alertId;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String rule;

    @Column(nullable = false)
    private String severity;

    private String host;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime timestamp;

    public Alert() {
    }

    public Alert(
            String alertId,
            String source,
            String rule,
            String severity,
            String host,
            String description,
            LocalDateTime timestamp
    ) {
        this.alertId = alertId;
        this.source = source;
        this.rule = rule;
        this.severity = severity;
        this.host = host;
        this.description = description;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}