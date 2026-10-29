package ai_soc_backend.incident.repository;

import ai_soc_backend.incident.entity.IncidentApprovalDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentApprovalDecisionRepository extends JpaRepository<IncidentApprovalDecision, Long> {
    List<IncidentApprovalDecision> findAllByIncident_IncidentIdOrderByDecidedAtDescIdDesc(String incidentId);
}
