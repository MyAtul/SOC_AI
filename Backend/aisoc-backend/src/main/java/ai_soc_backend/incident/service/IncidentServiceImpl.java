package ai_soc_backend.incident.service;

import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.repository.IncidentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IncidentServiceImpl implements IncidentService {

    private final IncidentRepository incidentRepository;

    public IncidentServiceImpl(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    @Override
    public List<Incident> getAllIncidents() {
        return incidentRepository.findAll();
    }

    @Override
    public Incident getIncidentById(String incidentId) {
        return incidentRepository.findByIncidentId(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
    }

    @Override
    public Incident createIncident(Incident incident) {
        return incidentRepository.save(incident);
    }
}
