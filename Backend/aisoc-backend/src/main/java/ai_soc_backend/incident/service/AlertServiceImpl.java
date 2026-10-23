package ai_soc_backend.incident.service;

import ai_soc_backend.incident.entity.Alert;
import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.repository.AlertRepository;
import org.springframework.stereotype.Service;

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

        Alert savedAlert = alertRepository.save(alert);

        detectionService.evaluate(savedAlert);

        return savedAlert;
    }
}