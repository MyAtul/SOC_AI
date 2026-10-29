package ai_soc_backend.incident.repository;

import ai_soc_backend.incident.entity.Incident;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.LocalDateTime;

public interface IncidentRepository extends JpaRepository<Incident,Long> {

    Optional<Incident> findByIncidentId(String incidentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Incident i where i.incidentId = :incidentId")
    Optional<Incident> findByIncidentIdForUpdate(@Param("incidentId") String incidentId);

    Optional<Incident> findByCorrelationKey(String correlationKey);

    Optional<Incident> findFirstByHostAndDetectionAndStatusAndTimestampGreaterThanEqualAndTimestampLessThanEqual(
            String host,
            String detection,
            String status,
            LocalDateTime windowStart,
            LocalDateTime windowEnd
    );
}
