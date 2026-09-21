package ai_soc_backend.incident.dto;

public record PolicyDecision(
        String incidentId,
        String action,
        boolean allowed,
        boolean approvalRequired,
        String reason
) {}