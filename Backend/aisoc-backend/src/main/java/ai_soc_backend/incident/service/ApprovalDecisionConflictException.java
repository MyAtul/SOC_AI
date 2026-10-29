package ai_soc_backend.incident.service;

public class ApprovalDecisionConflictException extends RuntimeException {
    public ApprovalDecisionConflictException(String incidentId, String currentStatus) {
        super("Incident " + incidentId + " cannot accept a decision from status " + currentStatus);
    }
}
