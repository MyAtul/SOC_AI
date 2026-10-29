package ai_soc_backend.incident.dto;

import java.util.List;
import ai_soc_backend.incident.catalog.AttackScenarioMetadata;
import ai_soc_backend.incident.catalog.ResponseActionMetadata;

public record AIInvestigationResponse(
        String incidentId,
        String classification,
        String severity,
        double confidence,
        String attackTechnique,
        List<String> evidence,
        String summary,
        String recommendedAction,
        String risk,
        boolean approvalRequired,
        AttackScenarioMetadata scenarioMetadata,
        ResponseActionMetadata responseActionMetadata,
        String analysisStatus
) {
    public AIInvestigationResponse(
            String incidentId, String classification, String severity, double confidence,
            String attackTechnique, List<String> evidence, String summary, String recommendedAction,
            String risk, boolean approvalRequired
    ) {
        this(incidentId, classification, severity, confidence, attackTechnique, evidence, summary,
                recommendedAction, risk, approvalRequired, null, null, "SIMULATED");
    }

    public AIInvestigationResponse withCatalogMetadata(AttackScenarioMetadata scenario,
                                                        ResponseActionMetadata responseAction) {
        return new AIInvestigationResponse(incidentId, classification, severity, confidence, attackTechnique,
                evidence, summary, recommendedAction, risk, approvalRequired,
                scenarioMetadata == null ? scenario : scenarioMetadata,
                responseActionMetadata == null ? responseAction : responseActionMetadata,
                analysisStatus == null ? "UNKNOWN" : analysisStatus);
    }
}
