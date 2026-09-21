package ai_soc_backend.incident.dto;

import java.util.List;

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
        boolean approvalRequired
) {
}