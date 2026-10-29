package ai_soc_backend.incident.service;

import ai_soc_backend.incident.dto.ApprovalResponse;
import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MockApprovalServiceTest {

    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final ApprovalDecisionAuditService audit = mock(ApprovalDecisionAuditService.class);
    private final MockApprovalService service = new MockApprovalService(new MockPolicyService(), incidents, audit);

    @Test
    void approvePersistsApprovedStatusWithoutExecutingAction() {
        Incident incident = incident("INC-001", "AWAITING_APPROVAL");
        when(incidents.findByIncidentIdForUpdate("INC-001")).thenReturn(Optional.of(incident));

        ApprovalResponse response = service.approve("INC-001");

        assertEquals("APPROVED", response.decision());
        assertEquals("APPROVED", incident.getStatus());
        verify(incidents).save(incident);
        verify(audit).record(incident, "APPROVED");
    }

    @Test
    void denyPersistsDeniedStatus() {
        Incident incident = incident("INC-003", "AWAITING_APPROVAL");
        when(incidents.findByIncidentIdForUpdate("INC-003")).thenReturn(Optional.of(incident));

        ApprovalResponse response = service.deny("INC-003");

        assertEquals("DENIED", response.decision());
        assertEquals("DENIED", incident.getStatus());
        verify(incidents).save(incident);
        verify(audit).record(incident, "DENIED");
    }

    @Test
    void repeatedSameDecisionIsIdempotentButOpposingDecisionConflicts() {
        Incident incident = incident("INC-001", "AWAITING_APPROVAL");
        when(incidents.findByIncidentIdForUpdate("INC-001")).thenReturn(Optional.of(incident));

        ApprovalResponse first = service.approve("INC-001");
        ApprovalResponse repeated = service.approve("INC-001");

        assertEquals(first, repeated);
        assertEquals("APPROVED", incident.getStatus());
        assertThrows(ApprovalDecisionConflictException.class, () -> service.deny("INC-001"));
        verify(incidents, times(1)).save(incident);
        verify(audit, times(1)).record(incident, "APPROVED");
    }

    @Test
    void policyDeniedAndNoApprovalRequiredDoNotChangeIncidentStatus() {
        Incident unsupported = incident("INC-999", "OPEN");
        Incident autoAllowed = incident("INC-002", "OPEN");
        when(incidents.findByIncidentIdForUpdate("INC-999")).thenReturn(Optional.of(unsupported));
        when(incidents.findByIncidentIdForUpdate("INC-002")).thenReturn(Optional.of(autoAllowed));

        assertEquals("POLICY_DENIED", service.approve("INC-999").status());
        assertEquals("AUTO_ALLOWED", service.deny("INC-002").status());
        verify(incidents, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingIncidentIsNotReportedAsPolicyDenial() {
        when(incidents.findByIncidentIdForUpdate("missing")).thenReturn(Optional.empty());

        assertThrows(IncidentNotFoundException.class, () -> service.approve("missing"));
        verify(incidents, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void policyAllowedIncidentMissingFromPersistenceReturnsNotFound() {
        when(incidents.findByIncidentIdForUpdate("INC-001")).thenReturn(Optional.empty());

        assertThrows(IncidentNotFoundException.class, () -> service.approve("INC-001"));
        verify(incidents, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static Incident incident(String id, String status) {
        return new Incident(id, "Test incident", "HIGH", status, "host", "test", null);
    }
}
