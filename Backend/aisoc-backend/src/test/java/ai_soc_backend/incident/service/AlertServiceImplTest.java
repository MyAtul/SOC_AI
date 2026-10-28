package ai_soc_backend.incident.service;

import ai_soc_backend.incident.entity.Alert;
import ai_soc_backend.incident.repository.AlertRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class AlertServiceImplTest {

    @Test
    void savesAndEvaluatesNewAlert() {
        AlertRepository repository = mock(AlertRepository.class);
        DetectionService detectionService = mock(DetectionService.class);
        AlertServiceImpl service = new AlertServiceImpl(repository, detectionService);
        Alert incoming = alert("WIN-100");
        Alert saved = alert("WIN-100");

        when(repository.findByAlertId("WIN-100")).thenReturn(Optional.empty());
        when(repository.save(incoming)).thenReturn(saved);

        assertSame(saved, service.processAlert(incoming));

        verify(repository).save(incoming);
        verify(detectionService).evaluate(saved);
    }

    @Test
    void retriesDetectionForPreviouslySavedAlertAfterEarlierAttemptFailed() {
        AlertRepository repository = mock(AlertRepository.class);
        DetectionService detectionService = mock(DetectionService.class);
        AlertServiceImpl service = new AlertServiceImpl(repository, detectionService);
        Alert incoming = alert("WIN-101");
        Alert existing = alert("WIN-101");

        when(repository.findByAlertId("WIN-101"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(repository.save(incoming)).thenReturn(existing);
        doThrow(new IllegalStateException("temporary detection failure"))
                .doReturn(null)
                .when(detectionService).evaluate(existing);

        assertThrows(IllegalStateException.class, () -> service.processAlert(incoming));
        assertSame(existing, service.processAlert(incoming));

        verify(repository).save(incoming);
        verify(detectionService, times(2)).evaluate(existing);
    }

    @Test
    void reevaluatesStoredAlertOnRepeatedDeliveryAfterSuccessfulDetection() {
        AlertRepository repository = mock(AlertRepository.class);
        DetectionService detectionService = mock(DetectionService.class);
        AlertServiceImpl service = new AlertServiceImpl(repository, detectionService);
        Alert incoming = alert("WIN-104");
        Alert existing = alert("WIN-104");

        when(repository.findByAlertId("WIN-104")).thenReturn(Optional.of(existing));

        assertSame(existing, service.processAlert(incoming));
        assertSame(existing, service.processAlert(incoming));

        verify(repository, never()).save(incoming);
        verify(detectionService, times(2)).evaluate(existing);
    }

    @Test
    void returnsConcurrentWinnerAfterUniqueConstraintConflict() {
        AlertRepository repository = mock(AlertRepository.class);
        DetectionService detectionService = mock(DetectionService.class);
        AlertServiceImpl service = new AlertServiceImpl(repository, detectionService);
        Alert incoming = alert("WIN-102");
        Alert concurrentWinner = alert("WIN-102");
        DataIntegrityViolationException uniquenessConflict =
                new DataIntegrityViolationException("duplicate alert_id");

        when(repository.findByAlertId("WIN-102"))
                .thenReturn(Optional.empty(), Optional.of(concurrentWinner));
        when(repository.save(incoming)).thenThrow(uniquenessConflict);

        assertSame(concurrentWinner, service.processAlert(incoming));

        verify(detectionService).evaluate(concurrentWinner);
    }

    @Test
    void rethrowsPersistenceErrorWhenNoConcurrentAlertExists() {
        AlertRepository repository = mock(AlertRepository.class);
        DetectionService detectionService = mock(DetectionService.class);
        AlertServiceImpl service = new AlertServiceImpl(repository, detectionService);
        Alert incoming = alert("WIN-103");
        DataIntegrityViolationException persistenceError =
                new DataIntegrityViolationException("unrelated persistence constraint");

        when(repository.findByAlertId("WIN-103")).thenReturn(Optional.empty());
        when(repository.save(incoming)).thenThrow(persistenceError);

        assertSame(persistenceError, assertThrows(
                DataIntegrityViolationException.class,
                () -> service.processAlert(incoming)
        ));
        verify(detectionService, never()).evaluate(incoming);
    }

    private Alert alert(String alertId) {
        return new Alert(
                alertId,
                "WINDOWS_SECURITY",
                "FAILED_LOGON",
                "MEDIUM",
                "localhost",
                "Windows Security Event 4625: failed logon detected",
                LocalDateTime.parse("2026-10-28T12:00:00")
        );
    }
}
