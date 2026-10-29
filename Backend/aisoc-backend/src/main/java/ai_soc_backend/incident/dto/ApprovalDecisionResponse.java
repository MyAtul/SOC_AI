package ai_soc_backend.incident.dto;

import java.time.Instant;

public record ApprovalDecisionResponse(String incidentId, String decision, Instant decidedAt, String actorId) {
}
