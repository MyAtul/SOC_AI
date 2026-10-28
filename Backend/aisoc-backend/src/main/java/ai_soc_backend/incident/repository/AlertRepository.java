package ai_soc_backend.incident.repository;

import ai_soc_backend.incident.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert,Long> {
    Optional<Alert> findByAlertId(String alertId);
}
