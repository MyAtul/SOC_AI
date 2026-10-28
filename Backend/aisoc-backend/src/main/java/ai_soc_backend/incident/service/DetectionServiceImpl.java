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
import java.util.Optional;
import java.util.UUID;

@Service
public class DetectionServiceImpl implements DetectionService {

    private static final String FAILED_LOGON_RULE = "FAILED_LOGON";
    private static final String FAILED_LOGON_DETECTION = "Multiple Failed Logins";
    private static final String OPEN_STATUS = "OPEN";
    private static final int FAILED_LOGON_THRESHOLD = 5;
    private static final long CORRELATION_WINDOW_MINUTES = 5;

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

        if (!FAILED_LOGON_RULE.equals(alert.getRule())) {
            return null;
        }

        LocalDateTime eventTimestamp = alert.getTimestamp();
        String host = alert.getHost();
        // Without an event time or a known host, events cannot be correlated safely.
        if (eventTimestamp == null || host == null || host.isBlank()) {
            return null;
        }

        LocalDateTime windowStart = eventTimestamp.minusMinutes(CORRELATION_WINDOW_MINUTES);

        List<Alert> recentAlerts = alertRepository.findAll()
                .stream()
                .filter(a -> FAILED_LOGON_RULE.equals(a.getRule()))
                .filter(a -> host.equals(a.getHost()))
                .filter(a -> a.getTimestamp() != null)
                .filter(a -> !a.getTimestamp().isBefore(windowStart))
                .filter(a -> !a.getTimestamp().isAfter(eventTimestamp))
                .toList();

        if (recentAlerts.size() < FAILED_LOGON_THRESHOLD) {
            return null;
        }

        Optional<Incident> existingIncident = incidentRepository
                .findFirstByHostAndDetectionAndStatusAndTimestampGreaterThanEqualAndTimestampLessThanEqual(
                        host,
                        FAILED_LOGON_DETECTION,
                        OPEN_STATUS,
                        windowStart,
                        eventTimestamp
                );
        if (existingIncident.isPresent()) {
            return existingIncident.get();
        }

        Incident incident = new Incident(
                "INC-" + UUID.randomUUID().toString().substring(0, 8),
                "Possible Brute Force Attack",
                "HIGH",
                OPEN_STATUS,
                host,
                FAILED_LOGON_DETECTION,
                eventTimestamp
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
