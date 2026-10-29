package ai_soc_backend.incident.service;

import ai_soc_backend.incident.dto.ApprovalDecisionResponse;
import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.entity.IncidentApprovalDecision;
import ai_soc_backend.incident.repository.IncidentApprovalDecisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ApprovalDecisionAuditService {
    private final IncidentApprovalDecisionRepository repository;

    public ApprovalDecisionAuditService(IncidentApprovalDecisionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(Incident incident, String decision) {
        // No authentication is configured, so never fabricate an actor identity.
        repository.save(new IncidentApprovalDecision(incident, decision, Instant.now(), null));
    }

    @Transactional(readOnly = true)
    public List<ApprovalDecisionResponse> listForIncident(String incidentId) {
        return repository.findAllByIncident_IncidentIdOrderByDecidedAtDescIdDesc(incidentId).stream()
                .map(record -> new ApprovalDecisionResponse(
                        record.getIncident().getIncidentId(), record.getDecision(), record.getDecidedAt(), record.getActorId()))
                .toList();
    }
}
