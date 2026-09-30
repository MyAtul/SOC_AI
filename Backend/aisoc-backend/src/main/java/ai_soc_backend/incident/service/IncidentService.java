package ai_soc_backend.incident.service;

import ai_soc_backend.incident.entity.Incident;

import java.util.List;

public interface IncidentService {

    List<Incident> getAllIncidents();

    Incident getIncidentById(String incidentId);

    Incident createIncident(Incident incident);
}
