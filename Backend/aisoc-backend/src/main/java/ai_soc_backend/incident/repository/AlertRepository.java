package ai_soc_backend.incident.repository;

import ai_soc_backend.incident.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.Instant;

public interface AlertRepository extends JpaRepository<Alert,Long> {
    Optional<Alert> findByAlertId(String alertId);

    long countByRuleAndHostAndEventTimestampUtcGreaterThanEqualAndEventTimestampUtcLessThan(
            String rule, String host, Instant startInclusive, Instant endExclusive);
}
