package ai_soc_backend.incident.repository;

import ai_soc_backend.incident.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.LocalDateTime;

public interface IncidentRepository extends JpaRepository<Incident,Long> {

    Optional<Incident> findByIncidentId(String incidentId);

    Optional<Incident> findFirstByHostAndDetectionAndStatusAndTimestampGreaterThanEqualAndTimestampLessThanEqual(
            String host,
            String detection,
            String status,
            LocalDateTime windowStart,
            LocalDateTime windowEnd
    );
}
