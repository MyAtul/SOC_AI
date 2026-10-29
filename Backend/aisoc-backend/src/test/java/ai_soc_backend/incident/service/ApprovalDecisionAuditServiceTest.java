package ai_soc_backend.incident.service;

import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.entity.IncidentApprovalDecision;
import ai_soc_backend.incident.repository.IncidentApprovalDecisionRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalDecisionAuditServiceTest {
    @Test
    void recordsDecisionWithoutInventingAnUnauthenticatedActor() {
        IncidentApprovalDecisionRepository repository = mock(IncidentApprovalDecisionRepository.class);
        when(repository.save(any(IncidentApprovalDecision.class))).thenAnswer(call -> call.getArgument(0));
        ApprovalDecisionAuditService service = new ApprovalDecisionAuditService(repository);
        Incident incident = new Incident("INC-1", "title", "HIGH", "OPEN", "host", "rule", null);

        service.record(incident, "APPROVED");

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(record ->
                record.getIncident() == incident && record.getDecision().equals("APPROVED")
                        && record.getDecidedAt() != null && record.getActorId() == null));
    }
}
