package ai_soc_backend.incident.service;

import ai_soc_backend.incident.dto.AIInvestigationResponse;
import ai_soc_backend.incident.dto.IncidentResponse;
import ai_soc_backend.incident.entity.AIInvestigation;
import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.repository.AIInvestigationRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AIInvestigationPersistenceServiceTest {

    @Test
    void persistsInvestigationForIncident() {
        AIInvestigationService aiService = mock(AIInvestigationService.class);
        AIInvestigationRepository repository = mock(AIInvestigationRepository.class);
        AIInvestigationPersistenceService service =
                new AIInvestigationPersistenceService(aiService, repository);
        Incident incident = incident();
        AIInvestigationResponse result = response("Credential Brute Force", 0.87);

        when(aiService.investigate(any(IncidentResponse.class))).thenReturn(result);
        when(repository.findByIncident_IncidentId("INC-101")).thenReturn(Optional.empty());
        when(repository.save(any(AIInvestigation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AIInvestigationResponse persisted = service.investigateAndPersist(incident);

        assertEquals(result, persisted);
        verify(repository).save(any(AIInvestigation.class));
    }

    @Test
    void updatesExistingInvestigationInsteadOfCreatingAnotherRecord() {
        AIInvestigationService aiService = mock(AIInvestigationService.class);
        AIInvestigationRepository repository = mock(AIInvestigationRepository.class);
        AIInvestigationPersistenceService service =
                new AIInvestigationPersistenceService(aiService, repository);
        Incident incident = incident();
        AIInvestigation existing = new AIInvestigation(incident, response("Old result", 0.40));
        AIInvestigationResponse refreshed = response("Updated result", 0.93);

        when(aiService.investigate(any(IncidentResponse.class))).thenReturn(refreshed);
        when(repository.findByIncident_IncidentId("INC-101")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        AIInvestigationResponse persisted = service.investigateAndPersist(incident);

        assertEquals(refreshed, persisted);
        assertEquals("Updated result", existing.getClassification());
        verify(repository).save(same(existing));
    }

    private Incident incident() {
        Incident incident = new Incident(
                "INC-101",
                "Failed login burst",
                "HIGH",
                "OPEN",
                "HOST-1",
                "Multiple failed logins",
                LocalDateTime.parse("2026-10-20T10:00:00")
        );
        incident.setId(1L);
        return incident;
    }

    private AIInvestigationResponse response(String classification, double confidence) {
        return new AIInvestigationResponse(
                "INC-101",
                classification,
                "HIGH",
                confidence,
                "T1110 - Brute Force",
                List.of("Repeated authentication failures", "Single source host"),
                "Repeated login failures were observed.",
                "REVIEW_MANUALLY",
                "MEDIUM",
                true
        );
    }
}
