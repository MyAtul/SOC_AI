package ai_soc_backend.incident.service;

import ai_soc_backend.incident.entity.Alert;
import ai_soc_backend.incident.repository.AlertRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;
    private final AlertPersistenceService alertPersistenceService;
    public AlertServiceImpl(
            AlertRepository alertRepository,
            AlertPersistenceService alertPersistenceService
    ) {
        this.alertRepository = alertRepository;
        this.alertPersistenceService = alertPersistenceService;
    }

    @Override
    public Alert processAlert(Alert alert) {
        Optional<Alert> existing = alertRepository.findByAlertId(alert.getAlertId());
        if (existing.isPresent()) {
            return existing.get();
        }

        String canonicalHost = HostIdentity.canonicalize(alert.getHost());
        if (canonicalHost != null) {
            alert.setHost(canonicalHost);
        }

        Alert savedAlert;
        try {
            // The proxied persistence service commits (or rolls back) alert and work
            // before control returns here. A losing unique-key race can then be read
            // safely in a fresh repository transaction.
            savedAlert = alertPersistenceService.saveNewAlertWithDetectionWork(alert);
        } catch (AlertIdConflictException alertIdConflict) {
            var persistenceError = alertIdConflict.getPersistenceError();
            Optional<Alert> createdByConcurrentRequest;
            try {
                createdByConcurrentRequest = alertRepository.findByAlertId(alert.getAlertId());
            } catch (RuntimeException lookupError) {
                persistenceError.addSuppressed(lookupError);
                throw persistenceError;
            }
            if (createdByConcurrentRequest.isPresent()) {
                return createdByConcurrentRequest.get();
            }
            throw persistenceError;
        }

        return savedAlert;
    }
}
