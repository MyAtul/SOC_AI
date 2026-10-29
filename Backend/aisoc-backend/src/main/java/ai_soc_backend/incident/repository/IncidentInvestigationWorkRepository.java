package ai_soc_backend.incident.repository;

import ai_soc_backend.incident.entity.IncidentInvestigationWork;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentInvestigationWorkRepository extends JpaRepository<IncidentInvestigationWork, Long> {
}
