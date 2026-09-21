package ai_soc_backend.incident.dto;

public record ApprovalResponse(
        String incidentId,
        String action,
        String decision,
        String status,
        String message
) {}