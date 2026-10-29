package ai_soc_backend.incident.service;

public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(String incidentId) {
        super("Incident not found: " + incidentId);
    }
}
