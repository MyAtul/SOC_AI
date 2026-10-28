package ai_soc_backend.incident.service;

import ai_soc_backend.incident.entity.Alert;
import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.repository.AlertRepository;
import ai_soc_backend.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DetectionServiceImplTest {

    private static final LocalDateTime EVENT_TIME = LocalDateTime.parse("2026-10-28T12:00:00");
    private static final String HOST = "host-a";

    @Test
    void createsIncidentAtFiveFailedLogonsForSameHostInInclusiveWindow() {
        AlertRepository alertRepository = mock(AlertRepository.class);
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        AIInvestigationPersistenceService investigations = mock(AIInvestigationPersistenceService.class);
        DetectionServiceImpl service = new DetectionServiceImpl(alertRepository, investigations, incidentRepository);
        Alert incoming = alert(HOST, EVENT_TIME);
        Incident saved = incident();

        when(alertRepository.findAll()).thenReturn(List.of(
                alert(HOST, EVENT_TIME.minusMinutes(5)),
                alert(HOST, EVENT_TIME.minusMinutes(4)),
                alert(HOST, EVENT_TIME.minusMinutes(3)),
                alert(HOST, EVENT_TIME.minusMinutes(2)),
                incoming
        ));
        when(incidentRepository.findFirstByHostAndDetectionAndStatusAndTimestampGreaterThanEqualAndTimestampLessThanEqual(
                HOST, "Multiple Failed Logins", "OPEN", EVENT_TIME.minusMinutes(5), EVENT_TIME
        )).thenReturn(Optional.empty());
        when(incidentRepository.save(org.mockito.ArgumentMatchers.any(Incident.class))).thenReturn(saved);

        assertSame(saved, service.evaluate(incoming));

        verify(incidentRepository).save(org.mockito.ArgumentMatchers.any(Incident.class));
        verify(investigations).investigateAndPersist(saved);
    }

    @Test
    void doesNotCreateIncidentBelowThreshold() {
        AlertRepository alertRepository = mock(AlertRepository.class);
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        AIInvestigationPersistenceService investigations = mock(AIInvestigationPersistenceService.class);
        DetectionServiceImpl service = new DetectionServiceImpl(alertRepository, investigations, incidentRepository);

        when(alertRepository.findAll()).thenReturn(List.of(
                alert(HOST, EVENT_TIME.minusMinutes(2)),
                alert(HOST, EVENT_TIME.minusMinutes(1)),
                alert(HOST, EVENT_TIME)
        ));

        assertNull(service.evaluate(alert(HOST, EVENT_TIME)));

        verify(incidentRepository, never()).save(org.mockito.ArgumentMatchers.any(Incident.class));
        verify(incidentRepository, never()).findFirstByHostAndDetectionAndStatusAndTimestampGreaterThanEqualAndTimestampLessThanEqual(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void doesNotCombineFailedLogonsFromDifferentHosts() {
        AlertRepository alertRepository = mock(AlertRepository.class);
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        AIInvestigationPersistenceService investigations = mock(AIInvestigationPersistenceService.class);
        DetectionServiceImpl service = new DetectionServiceImpl(alertRepository, investigations, incidentRepository);

        when(alertRepository.findAll()).thenReturn(List.of(
                alert(HOST, EVENT_TIME.minusMinutes(4)),
                alert(HOST, EVENT_TIME.minusMinutes(3)),
                alert("host-b", EVENT_TIME.minusMinutes(2)),
                alert("host-b", EVENT_TIME.minusMinutes(1)),
                alert(HOST, EVENT_TIME)
        ));

        assertNull(service.evaluate(alert(HOST, EVENT_TIME)));

        verify(incidentRepository, never()).save(org.mockito.ArgumentMatchers.any(Incident.class));
    }

    @Test
    void excludesEventsOutsideTheFiveMinuteWindowAndAfterTheIncomingEvent() {
        AlertRepository alertRepository = mock(AlertRepository.class);
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        AIInvestigationPersistenceService investigations = mock(AIInvestigationPersistenceService.class);
        DetectionServiceImpl service = new DetectionServiceImpl(alertRepository, investigations, incidentRepository);

        when(alertRepository.findAll()).thenReturn(List.of(
                alert(HOST, EVENT_TIME.minusMinutes(5).minusNanos(1)),
                alert(HOST, EVENT_TIME.minusMinutes(4)),
                alert(HOST, EVENT_TIME.minusMinutes(3)),
                alert(HOST, EVENT_TIME.minusMinutes(2)),
                alert(HOST, EVENT_TIME.plusNanos(1)),
                alert(HOST, EVENT_TIME)
        ));

        assertNull(service.evaluate(alert(HOST, EVENT_TIME)));

        verify(incidentRepository, never()).save(org.mockito.ArgumentMatchers.any(Incident.class));
    }

    @Test
    void nullTimestampDoesNotThrowOrAttemptCorrelation() {
        AlertRepository alertRepository = mock(AlertRepository.class);
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        AIInvestigationPersistenceService investigations = mock(AIInvestigationPersistenceService.class);
        DetectionServiceImpl service = new DetectionServiceImpl(alertRepository, investigations, incidentRepository);

        assertNull(service.evaluate(alert(HOST, null)));

        verify(alertRepository, never()).findAll();
        verify(incidentRepository, never()).save(org.mockito.ArgumentMatchers.any(Incident.class));
    }

    @Test
    void returnsExistingOpenIncidentInsteadOfCreatingAnotherForRepeatedQualifyingAlerts() {
        AlertRepository alertRepository = mock(AlertRepository.class);
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        AIInvestigationPersistenceService investigations = mock(AIInvestigationPersistenceService.class);
        DetectionServiceImpl service = new DetectionServiceImpl(alertRepository, investigations, incidentRepository);
        Alert incoming = alert(HOST, EVENT_TIME);
        Incident existing = incident();

        when(alertRepository.findAll()).thenReturn(List.of(
                alert(HOST, EVENT_TIME.minusMinutes(4)),
                alert(HOST, EVENT_TIME.minusMinutes(3)),
                alert(HOST, EVENT_TIME.minusMinutes(2)),
                alert(HOST, EVENT_TIME.minusMinutes(1)),
                incoming
        ));
        when(incidentRepository.findFirstByHostAndDetectionAndStatusAndTimestampGreaterThanEqualAndTimestampLessThanEqual(
                eq(HOST),
                eq("Multiple Failed Logins"),
                eq("OPEN"),
                eq(EVENT_TIME.minusMinutes(5)),
                eq(EVENT_TIME)
        )).thenReturn(Optional.of(existing));

        assertSame(existing, service.evaluate(incoming));

        verify(incidentRepository, never()).save(org.mockito.ArgumentMatchers.any(Incident.class));
        verify(investigations, never()).investigateAndPersist(org.mockito.ArgumentMatchers.any(Incident.class));
    }

    private Alert alert(String host, LocalDateTime timestamp) {
        return new Alert(
                "alert-" + host + "-" + timestamp,
                "WINDOWS_SECURITY",
                "FAILED_LOGON",
                "MEDIUM",
                host,
                "Failed logon",
                timestamp
        );
    }

    private Incident incident() {
        return new Incident(
                "INC-test",
                "Possible Brute Force Attack",
                "HIGH",
                "OPEN",
                HOST,
                "Multiple Failed Logins",
                EVENT_TIME
        );
    }
}
