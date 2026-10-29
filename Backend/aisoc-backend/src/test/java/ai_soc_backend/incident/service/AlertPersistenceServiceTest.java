package ai_soc_backend.incident.service;

import ai_soc_backend.incident.entity.Alert;
import ai_soc_backend.incident.entity.AlertDetectionWork;
import ai_soc_backend.incident.repository.AlertDetectionWorkRepository;
import ai_soc_backend.incident.repository.AlertRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertPersistenceServiceTest {

    @Test
    void savesAlertAndExactlyOnePendingWorkRowInOrderInsideTransactionalService() throws Exception {
        AlertRepository alerts = mock(AlertRepository.class);
        AlertDetectionWorkRepository work = mock(AlertDetectionWorkRepository.class);
        AlertPersistenceService service = new AlertPersistenceService(alerts, work);
        Alert input = mock(Alert.class);
        Alert persisted = mock(Alert.class);
        when(alerts.saveAndFlush(input)).thenReturn(persisted);
        when(persisted.getId()).thenReturn(42L);
        when(work.saveAndFlush(any(AlertDetectionWork.class))).thenAnswer(call -> call.getArgument(0));

        assertSame(persisted, service.saveNewAlertWithDetectionWork(input));

        var order = inOrder(alerts, work);
        order.verify(alerts).saveAndFlush(input);
        var workRow = org.mockito.ArgumentCaptor.forClass(AlertDetectionWork.class);
        order.verify(work).saveAndFlush(workRow.capture());
        assertEquals(42L, workRow.getValue().getAlertPk());
        assertEquals("PENDING", workRow.getValue().getStatus());
        assertEquals(0, workRow.getValue().getAttemptCount());
        verify(work).saveAndFlush(any(AlertDetectionWork.class));

        Method method = AlertPersistenceService.class.getMethod("saveNewAlertWithDetectionWork", Alert.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertNotNull(transactional,
                "Alert and work persistence must run through one Spring transaction");
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    @Test
    void propagatesWorkInsertFailureSoTransactionCanRollBackAlert() {
        AlertRepository alerts = mock(AlertRepository.class);
        AlertDetectionWorkRepository work = mock(AlertDetectionWorkRepository.class);
        AlertPersistenceService service = new AlertPersistenceService(alerts, work);
        Alert input = mock(Alert.class);
        Alert persisted = mock(Alert.class);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("work constraint failure");
        when(alerts.saveAndFlush(input)).thenReturn(persisted);
        when(persisted.getId()).thenReturn(43L);
        when(work.saveAndFlush(any(AlertDetectionWork.class))).thenThrow(failure);

        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> service.saveNewAlertWithDetectionWork(input)));
        verify(alerts).saveAndFlush(input);
        verify(work).saveAndFlush(any(AlertDetectionWork.class));
    }

    @Test
    void marksOnlyTheAlertIdUniqueConstraintAsRecoverableDuplicate() {
        AlertRepository alerts = mock(AlertRepository.class);
        AlertDetectionWorkRepository work = mock(AlertDetectionWorkRepository.class);
        AlertPersistenceService service = new AlertPersistenceService(alerts, work);
        Alert input = mock(Alert.class);
        DataIntegrityViolationException uniqueAlertId = new DataIntegrityViolationException(
                "duplicate alert id",
                new ConstraintViolationException("duplicate alert id", new SQLException("duplicate", "23505"),
                        "uq_alerts_alert_id"));
        when(alerts.saveAndFlush(input)).thenThrow(uniqueAlertId);

        AlertIdConflictException conflict = assertThrows(AlertIdConflictException.class,
                () -> service.saveNewAlertWithDetectionWork(input));

        assertSame(uniqueAlertId, conflict.getPersistenceError());
        verify(work, org.mockito.Mockito.never()).saveAndFlush(any(AlertDetectionWork.class));
    }

    @Test
    void doesNotClassifyAnotherUniqueConstraintAsAnAlertIdDuplicate() {
        AlertRepository alerts = mock(AlertRepository.class);
        AlertDetectionWorkRepository work = mock(AlertDetectionWorkRepository.class);
        AlertPersistenceService service = new AlertPersistenceService(alerts, work);
        Alert input = mock(Alert.class);
        DataIntegrityViolationException unrelatedUnique = new DataIntegrityViolationException(
                "duplicate primary key",
                new ConstraintViolationException("duplicate primary key", new SQLException("duplicate", "23505"),
                        "pk_alerts"));
        when(alerts.saveAndFlush(input)).thenThrow(unrelatedUnique);

        assertSame(unrelatedUnique, assertThrows(DataIntegrityViolationException.class,
                () -> service.saveNewAlertWithDetectionWork(input)));
        verify(work, org.mockito.Mockito.never()).saveAndFlush(any(AlertDetectionWork.class));
    }
}
