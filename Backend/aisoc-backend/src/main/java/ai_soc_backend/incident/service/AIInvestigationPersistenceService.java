package ai_soc_backend.incident.service;

import ai_soc_backend.incident.dto.AIInvestigationResponse;
import ai_soc_backend.incident.dto.IncidentResponse;
import ai_soc_backend.incident.entity.AIInvestigation;
import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.repository.AIInvestigationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AIInvestigationPersistenceService {

    private final AIInvestigationService aiInvestigationService;
    private final AIInvestigationRepository investigationRepository;

    public AIInvestigationPersistenceService(
            AIInvestigationService aiInvestigationService,
            AIInvestigationRepository investigationRepository
    ) {
        this.aiInvestigationService = aiInvestigationService;
        this.investigationRepository = investigationRepository;
    }

    @Transactional
    public AIInvestigationResponse investigateAndPersist(Incident incident) {
        IncidentResponse incidentResponse = new IncidentResponse(
                incident.getIncidentId(),
                incident.getTitle(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getHost(),
                incident.getDetection(),
                incident.getTimestamp().toString()
        );

        AIInvestigationResponse response = aiInvestigationService.investigate(incidentResponse);
        if (response == null) {
            throw new IllegalStateException("AI investigation returned no result");
        }

        AIInvestigation investigation = investigationRepository
                .findByIncident_IncidentId(incident.getIncidentId())
                .orElseGet(() -> new AIInvestigation(incident, response));

        investigation.updateFrom(response);
        return investigationRepository.save(investigation).toResponse();
    }

    @Transactional(readOnly = true)
    public Optional<AIInvestigationResponse> getPersisted(String incidentId) {
        return investigationRepository.findByIncident_IncidentId(incidentId)
                .map(AIInvestigation::toResponse);
    }
}
