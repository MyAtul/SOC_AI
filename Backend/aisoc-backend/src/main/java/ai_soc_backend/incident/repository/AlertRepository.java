package ai_soc_backend.incident.repository;

import ai_soc_backend.incident.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert,Long> {
}
