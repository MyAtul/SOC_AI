package ai_soc_backend.incident.service;

import ai_soc_backend.incident.entity.Alert;
import ai_soc_backend.incident.entity.AlertDetectionWork;
import ai_soc_backend.incident.repository.AlertDetectionWorkRepository;
import ai_soc_backend.incident.repository.AlertRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertPersistenceService {

    private final AlertRepository alertRepository;
    private final AlertDetectionWorkRepository detectionWorkRepository;

    public AlertPersistenceService(
            AlertRepository alertRepository,
            AlertDetectionWorkRepository detectionWorkRepository
    ) {
        this.alertRepository = alertRepository;
        this.detectionWorkRepository = detectionWorkRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Alert saveNewAlertWithDetectionWork(Alert alert) {
        Alert savedAlert;
        try {
            savedAlert = alertRepository.saveAndFlush(alert);
        } catch (DataIntegrityViolationException persistenceError) {
            if (isAlertIdUniqueConflict(persistenceError)) {
                throw new AlertIdConflictException(persistenceError);
            }
            throw persistenceError;
        }
        detectionWorkRepository.saveAndFlush(new AlertDetectionWork(savedAlert.getId()));
        return savedAlert;
    }

    private boolean isAlertIdUniqueConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return "23505".equals(constraintViolation.getSQLState())
                        && "uq_alerts_alert_id".equals(constraintViolation.getConstraintName());
            }
        }
        return false;
    }
}
