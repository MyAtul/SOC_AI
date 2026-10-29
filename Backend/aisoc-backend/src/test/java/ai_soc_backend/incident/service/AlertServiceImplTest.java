package ai_soc_backend.incident.service;

import ai_soc_backend.incident.entity.Alert;
import ai_soc_backend.incident.repository.AlertRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AlertServiceImplTest {
    @Test
    void newAlertIsSavedWithWorkAndDetectionIsDeferred() {
        AlertRepository repository = mock(AlertRepository.class);
        AlertPersistenceService persistence = mock(AlertPersistenceService.class);
        AlertServiceImpl service = new AlertServiceImpl(repository, persistence);
        Alert input = alert("new");
        Alert saved = alert("new");
        when(repository.findByAlertId("new")).thenReturn(Optional.empty());
        when(persistence.saveNewAlertWithDetectionWork(input)).thenReturn(saved);

        assertSame(saved, service.processAlert(input));
        verify(persistence).saveNewAlertWithDetectionWork(input);
        verifyNoMoreInteractions(persistence);
    }

    @Test
    void canonicalizesValidHostIdentityBeforePersistingNewAlert() {
        AlertRepository repository = mock(AlertRepository.class);
        AlertPersistenceService persistence = mock(AlertPersistenceService.class);
        AlertServiceImpl service = new AlertServiceImpl(repository, persistence);
        Alert input = alert("host-normalize");
        input.setHost("  HOST-A  ");
        when(repository.findByAlertId("host-normalize")).thenReturn(Optional.empty());
        when(persistence.saveNewAlertWithDetectionWork(input)).thenReturn(input);

        service.processAlert(input);

        assertEquals("host-a", input.getHost());
        verify(persistence).saveNewAlertWithDetectionWork(input);
    }

    @Test
    void duplicateDeliveryReturnsExistingAlertWithoutResettingOrRunningDetection() {
        AlertRepository repository = mock(AlertRepository.class);
        AlertPersistenceService persistence = mock(AlertPersistenceService.class);
        AlertServiceImpl service = new AlertServiceImpl(repository, persistence);
        Alert saved = alert("same");
        when(repository.findByAlertId("same")).thenReturn(Optional.of(saved));

        assertSame(saved, service.processAlert(alert("same")));
        assertSame(saved, service.processAlert(alert("same")));
        verify(persistence, never()).saveNewAlertWithDetectionWork(any());
        verify(repository, times(2)).findByAlertId("same");
    }

    @Test
    void duplicateInsertRaceReturnsWinnerButDoesNotRunDetectionInline() {
        AlertRepository repository = mock(AlertRepository.class);
        AlertPersistenceService persistence = mock(AlertPersistenceService.class);
        AlertServiceImpl service = new AlertServiceImpl(repository, persistence);
        Alert input = alert("race");
        Alert winner = alert("race");
        DataIntegrityViolationException cause = new DataIntegrityViolationException("unique");
        when(repository.findByAlertId("race")).thenReturn(Optional.empty(), Optional.of(winner));
        when(persistence.saveNewAlertWithDetectionWork(input)).thenThrow(new AlertIdConflictException(cause));

        assertSame(winner, service.processAlert(input));
        verify(persistence).saveNewAlertWithDetectionWork(input);
    }

    @Test
    void unrelatedPersistenceErrorIsRethrown() {
        AlertRepository repository = mock(AlertRepository.class);
        AlertPersistenceService persistence = mock(AlertPersistenceService.class);
        AlertServiceImpl service = new AlertServiceImpl(repository, persistence);
        Alert input = alert("error");
        DataIntegrityViolationException error = new DataIntegrityViolationException("other constraint");
        when(repository.findByAlertId("error")).thenReturn(Optional.empty());
        when(persistence.saveNewAlertWithDetectionWork(input)).thenThrow(error);

        assertSame(error, assertThrows(DataIntegrityViolationException.class, () -> service.processAlert(input)));
    }

    private Alert alert(String id) {
        return new Alert(id, "WINDOWS_SECURITY", "FAILED_LOGON", "MEDIUM", "host-1", "test",
                LocalDateTime.parse("2026-10-28T12:00:00"));
    }
}
