package ai_soc_backend.incident.service;

import ai_soc_backend.incident.dto.AIInvestigationResponse;
import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.repository.AIInvestigationRepository;
import ai_soc_backend.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class InvestigationWorkProcessorTest {
    @Test
    void callsProviderOutsideClaimTransactionThenDelegatesAtomicFinalization() {
        IncidentRepository incidents = mock(IncidentRepository.class);
        AIInvestigationRepository persisted = mock(AIInvestigationRepository.class);
        AIInvestigationPersistenceService provider = mock(AIInvestigationPersistenceService.class);
        InvestigationWorkFinalizer finalizer = mock(InvestigationWorkFinalizer.class);
        Incident incident = new Incident("INC-1", "title", "HIGH", "OPEN", "host", "detect", null);
        InvestigationWorkClaim claim = new InvestigationWorkClaim(7L, UUID.randomUUID(), (short) 1);
        var response = new AIInvestigationResponse("INC-1", "classification", "HIGH", .8,
                "T1110", List.of("evidence"), "summary", "review", "MEDIUM", false);
        when(incidents.findById(7L)).thenReturn(Optional.of(incident));
        when(persisted.findByIncident_IncidentId("INC-1")).thenReturn(Optional.empty());
        when(provider.investigate(incident)).thenReturn(response);
        when(finalizer.persistAndComplete(claim, response)).thenReturn(true);

        assertTrue(new InvestigationWorkProcessor(incidents, persisted, provider, finalizer).process(claim));
        verify(provider).investigate(incident);
        verify(finalizer).persistAndComplete(claim, response);
    }

    @Test
    void skipsProviderAndCompletesAlreadyPersistedResultIdempotently() {
        IncidentRepository incidents = mock(IncidentRepository.class);
        AIInvestigationRepository persisted = mock(AIInvestigationRepository.class);
        AIInvestigationPersistenceService provider = mock(AIInvestigationPersistenceService.class);
        InvestigationWorkFinalizer finalizer = mock(InvestigationWorkFinalizer.class);
        Incident incident = new Incident("INC-2", "title", "HIGH", "OPEN", "host", "detect", null);
        InvestigationWorkClaim claim = new InvestigationWorkClaim(8L, UUID.randomUUID(), (short) 2);
        when(incidents.findById(8L)).thenReturn(Optional.of(incident));
        when(persisted.findByIncident_IncidentId("INC-2")).thenReturn(Optional.of(mock(ai_soc_backend.incident.entity.AIInvestigation.class)));
        when(finalizer.completeAlreadyPersisted(claim)).thenReturn(true);

        assertTrue(new InvestigationWorkProcessor(incidents, persisted, provider, finalizer).process(claim));
        verifyNoInteractions(provider);
        verify(finalizer).completeAlreadyPersisted(claim);
    }

    @Test
    void providerFailurePropagatesForDurableRetry() {
        IncidentRepository incidents = mock(IncidentRepository.class);
        AIInvestigationRepository persisted = mock(AIInvestigationRepository.class);
        AIInvestigationPersistenceService provider = mock(AIInvestigationPersistenceService.class);
        InvestigationWorkFinalizer finalizer = mock(InvestigationWorkFinalizer.class);
        Incident incident = new Incident("INC-3", "title", "HIGH", "OPEN", "host", "detect", null);
        InvestigationWorkClaim claim = new InvestigationWorkClaim(9L, UUID.randomUUID(), (short) 1);
        when(incidents.findById(9L)).thenReturn(Optional.of(incident));
        when(persisted.findByIncident_IncidentId("INC-3")).thenReturn(Optional.empty());
        when(provider.investigate(incident)).thenThrow(new IllegalStateException("provider unavailable"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new InvestigationWorkProcessor(incidents, persisted, provider, finalizer).process(claim));
        verifyNoInteractions(finalizer);
    }
}
