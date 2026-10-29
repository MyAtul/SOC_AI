package ai_soc_backend.incident.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "incident_approval_decisions")
public class IncidentApprovalDecision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_pk", nullable = false)
    private Incident incident;

    @Column(nullable = false, length = 8)
    private String decision;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(name = "actor_id", length = 255)
    private String actorId;

    protected IncidentApprovalDecision() { }

    public IncidentApprovalDecision(Incident incident, String decision, Instant decidedAt, String actorId) {
        this.incident = incident;
        this.decision = decision;
        this.decidedAt = decidedAt;
        this.actorId = actorId;
    }

    public Long getId() { return id; }
    public Incident getIncident() { return incident; }
    public String getDecision() { return decision; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getActorId() { return actorId; }
}
