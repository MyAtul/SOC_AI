package ai_soc_backend.incident.dto;

public record IncidentResponse(
        String incidentId,
        String title,
        String severity,
        String status,
        String host,
        String detection,
        String timestamp
) {
}