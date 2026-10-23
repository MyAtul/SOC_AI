package ai_soc_backend.incident.repository;

import ai_soc_backend.incident.entity.AIInvestigation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AIInvestigationRepository extends JpaRepository<AIInvestigation, Long> {

    Optional<AIInvestigation> findByIncident_IncidentId(String incidentId);
}
