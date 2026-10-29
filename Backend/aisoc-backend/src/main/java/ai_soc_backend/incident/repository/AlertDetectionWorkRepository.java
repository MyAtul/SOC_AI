package ai_soc_backend.incident.repository;

import ai_soc_backend.incident.entity.AlertDetectionWork;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertDetectionWorkRepository extends JpaRepository<AlertDetectionWork, Long> {
}
