package ai_soc_backend.incident.service;

import org.springframework.dao.DataIntegrityViolationException;

/** Signals only the unique constraint protecting alerts.alert_id. */
public class AlertIdConflictException extends RuntimeException {

    private final DataIntegrityViolationException persistenceError;

    public AlertIdConflictException(DataIntegrityViolationException persistenceError) {
        super("An alert with this alertId was persisted concurrently", persistenceError);
        this.persistenceError = persistenceError;
    }

    public DataIntegrityViolationException getPersistenceError() {
        return persistenceError;
    }
}
