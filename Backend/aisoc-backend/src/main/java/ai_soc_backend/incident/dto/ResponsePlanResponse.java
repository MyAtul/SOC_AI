package ai_soc_backend.incident.dto;

import java.util.List;

public record ResponsePlanResponse(
        String incidentId,
        String recommendedAction,
        String risk,
        boolean approvalRequired,
        String policyStatus,
        List<String> steps,
        String explanation
) {}