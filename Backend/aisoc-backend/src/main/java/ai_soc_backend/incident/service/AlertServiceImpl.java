package ai_soc_backend.incident.service;

import ai_soc_backend.incident.entity.Alert;
import ai_soc_backend.incident.repository.AlertRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;
    private final DetectionService detectionService;

    public AlertServiceImpl(
            AlertRepository alertRepository,
            DetectionService detectionService
    ) {
        this.alertRepository = alertRepository;
        this.detectionService = detectionService;
    }

    @Override
    public Alert processAlert(Alert alert) {
        Optional<Alert> existing = alertRepository.findByAlertId(alert.getAlertId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Alert savedAlert;
        try {
            savedAlert = alertRepository.save(alert);
        } catch (DataIntegrityViolationException persistenceError) {
            try {
                Optional<Alert> createdByConcurrentRequest =
                        alertRepository.findByAlertId(alert.getAlertId());
                if (createdByConcurrentRequest.isPresent()) {
                    return createdByConcurrentRequest.get();
                }
            } catch (RuntimeException lookupError) {
                persistenceError.addSuppressed(lookupError);
            }
            throw persistenceError;
        }

        detectionService.evaluate(savedAlert);

        return savedAlert;
    }
}
