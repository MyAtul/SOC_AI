package ai_soc_backend.incident.service;

import ai_soc_backend.incident.entity.Alert;
import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.repository.AlertRepository;
import ai_soc_backend.incident.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DetectionServiceImpl implements DetectionService {

    private final AlertRepository alertRepository;
    private static final Logger logger = LoggerFactory.getLogger(DetectionServiceImpl.class);

    private final AIInvestigationPersistenceService aiInvestigationPersistenceService;
    private final IncidentRepository incidentRepository;

    public DetectionServiceImpl(
            AlertRepository alertRepository, AIInvestigationPersistenceService aiInvestigationPersistenceService,
            IncidentRepository incidentRepository
    ) {
        this.alertRepository = alertRepository;
        this.aiInvestigationPersistenceService = aiInvestigationPersistenceService;
        this.incidentRepository = incidentRepository;
    }

    @Override
    public Incident evaluate(Alert alert) {

        if (!"FAILED_LOGON".equals(alert.getRule())) {
            return null;
        }

        LocalDateTime fiveMinutesAgo =
                alert.getTimestamp().minusMinutes(5);

        List<Alert> recentAlerts = alertRepository.findAll()
                .stream()
                .filter(a -> "FAILED_LOGON".equals(a.getRule()))
                .filter(a -> a.getTimestamp() != null)
                .filter(a -> !a.getTimestamp().isBefore(fiveMinutesAgo))
                .filter(a -> a.getTimestamp().isBefore(alert.getTimestamp())
                        || a.getTimestamp().isEqual(alert.getTimestamp()))
                .toList();

        if (recentAlerts.size() < 5) {
            return null;
        }

        Incident incident = new Incident(
                "INC-" + UUID.randomUUID().toString().substring(0, 8),
                "Possible Brute Force Attack",
                "HIGH",
                "OPEN",
                alert.getHost(),
                "Multiple Failed Logins",
                alert.getTimestamp()
        );

        Incident savedIncident = incidentRepository.save(incident);

        try {
            aiInvestigationPersistenceService.investigateAndPersist(savedIncident);
        } catch (RuntimeException exception) {
            logger.error(
                    "Automatic AI investigation failed for incident {}",
                    savedIncident.getIncidentId(),
                    exception
            );
        }

        return savedIncident;
    }
}
