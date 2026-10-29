package ai_soc_backend.incident.entity;

import ai_soc_backend.incident.dto.AIInvestigationResponse;
import ai_soc_backend.incident.catalog.AttackScenarioCatalog;
import ai_soc_backend.incident.catalog.ResponseActionCatalog;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ai_investigations")
public class AIInvestigation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_pk", nullable = false, unique = true)
    private Incident incident;

    @Column(nullable = false)
    private String classification;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private double confidence;

    private String attackTechnique;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "ai_investigation_evidence",
            joinColumns = @JoinColumn(name = "investigation_id")
    )
    @OrderColumn(name = "position")
    @Column(name = "evidence", nullable = false, columnDefinition = "TEXT")
    private List<String> evidence = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String summary;

    private String recommendedAction;

    private String risk;

    @Column(nullable = false)
    private boolean approvalRequired;

    @Column(name = "analysis_status", nullable = false, length = 24)
    private String analysisStatus = "UNKNOWN";

    @Column(name = "scenario_id", length = 64)
    private String scenarioId;

    protected AIInvestigation() {
    }

    public AIInvestigation(Incident incident, AIInvestigationResponse response) {
        this.incident = incident;
        updateFrom(response);
    }

    public void updateFrom(AIInvestigationResponse response) {
        classification = response.classification();
        severity = response.severity();
        confidence = response.confidence();
        attackTechnique = response.attackTechnique();
        evidence = response.evidence() == null
                ? new ArrayList<>()
                : new ArrayList<>(response.evidence());
        summary = response.summary();
        recommendedAction = response.recommendedAction();
        risk = response.risk();
        approvalRequired = response.approvalRequired();
        analysisStatus = response.analysisStatus() == null ? "UNKNOWN" : response.analysisStatus();
        scenarioId = response.scenarioMetadata() == null ? null : response.scenarioMetadata().id();
    }

    public AIInvestigationResponse toResponse() {
        return new AIInvestigationResponse(
                incident.getIncidentId(),
                classification,
                severity,
                confidence,
                attackTechnique,
                List.copyOf(evidence),
                summary,
                recommendedAction,
                risk,
                approvalRequired,
                AttackScenarioCatalog.byId(scenarioId)
                        .or(() -> AttackScenarioCatalog.forDetection(incident.getDetection()))
                        .orElse(null),
                ResponseActionCatalog.find(recommendedAction).orElse(null),
                analysisStatus
        );
    }

    public Incident getIncident() {
        return incident;
    }

    public String getClassification() {
        return classification;
    }

    public String getSeverity() {
        return severity;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getAttackTechnique() {
        return attackTechnique;
    }

    public List<String> getEvidence() {
        return List.copyOf(evidence);
    }

    public String getSummary() {
        return summary;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public String getRisk() {
        return risk;
    }

    public boolean isApprovalRequired() {
        return approvalRequired;
    }
}
