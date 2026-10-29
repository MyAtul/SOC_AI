package ai_soc_backend.incident.service;

import org.springframework.dao.DataIntegrityViolationException;

public class CorrelationKeyConflictException extends RuntimeException {
    public CorrelationKeyConflictException(DataIntegrityViolationException cause) {
        super("Concurrent incident correlation-key creation", cause);
    }
}
