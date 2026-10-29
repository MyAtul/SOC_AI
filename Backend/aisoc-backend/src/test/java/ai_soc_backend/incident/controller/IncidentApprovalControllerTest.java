package ai_soc_backend.incident.controller;

import ai_soc_backend.incident.dto.ApprovalResponse;
import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.service.AIInvestigationPersistenceService;
import ai_soc_backend.incident.service.ApprovalDecisionConflictException;
import ai_soc_backend.incident.service.ApprovalService;
import ai_soc_backend.incident.service.IncidentService;
import ai_soc_backend.incident.service.ResponsePlanService;
import ai_soc_backend.incident.service.ApprovalDecisionAuditService;
import ai_soc_backend.incident.dto.ApprovalDecisionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class IncidentApprovalControllerTest {

    private ApprovalService approvalService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        approvalService = mock(ApprovalService.class);
        IncidentController controller = new IncidentController(
                mock(AIInvestigationPersistenceService.class),
                approvalService,
                mock(ResponsePlanService.class),
                mock(IncidentService.class),
                mock(ai_soc_backend.incident.service.ApprovalDecisionAuditService.class)
        );
        mockMvc = standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void approveEndpointReturnsApprovalResponseAndDelegatesToApprove() throws Exception {
        when(approvalService.approve("INC-001")).thenReturn(new ApprovalResponse(
                "INC-001",
                "ISOLATE_HOST",
                "APPROVED",
                "APPROVED",
                "Approval decision recorded. No response action was executed."
        ));

        mockMvc.perform(post("/api/incidents/INC-001/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value("INC-001"))
                .andExpect(jsonPath("$.action").value("ISOLATE_HOST"))
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.message").value(
                        "Approval decision recorded. No response action was executed."));

        verify(approvalService).approve("INC-001");
        verify(approvalService, never()).deny("INC-001");
    }

    @Test
    void denyEndpointReturnsDenialResponseAndDelegatesToDeny() throws Exception {
        when(approvalService.deny("INC-003")).thenReturn(new ApprovalResponse(
                "INC-003",
                "DISABLE_ACCOUNT",
                "DENIED",
                "DENIED",
                "Denial decision recorded. No response action was executed."
        ));

        mockMvc.perform(post("/api/incidents/INC-003/deny"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value("INC-003"))
                .andExpect(jsonPath("$.action").value("DISABLE_ACCOUNT"))
                .andExpect(jsonPath("$.decision").value("DENIED"))
                .andExpect(jsonPath("$.status").value("DENIED"))
                .andExpect(jsonPath("$.message").value(
                        "Denial decision recorded. No response action was executed."));

        verify(approvalService).deny("INC-003");
        verify(approvalService, never()).approve("INC-003");
    }

    @Test
    void denyEndpointReturnsNotFoundForUnknownIncident() throws Exception {
        org.mockito.Mockito.doThrow(new ai_soc_backend.incident.service.IncidentNotFoundException("INC-404"))
                .when(approvalService).deny("INC-404");

        mockMvc.perform(post("/api/incidents/INC-404/deny"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Incident not found"));

        verify(approvalService).deny("INC-404");
        verify(approvalService, never()).approve("INC-404");
    }

    @Test
    void conflictingDecisionReturnsHttpConflict() throws Exception {
        org.mockito.Mockito.doThrow(new ApprovalDecisionConflictException("INC-001", "APPROVED"))
                .when(approvalService).deny("INC-001");

        mockMvc.perform(post("/api/incidents/INC-001/deny"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DECISION_CONFLICT"));
    }

    @Test
    void incidentListReturnsPersistedDecisionStatusForDashboardRefresh() throws Exception {
        var incidentService = mock(IncidentService.class);
        Incident approved = new Incident(
                "INC-001", "Incident", "HIGH", "APPROVED", "host", "detection", null);
        IncidentController controller = new IncidentController(
                mock(AIInvestigationPersistenceService.class),
                approvalService,
                mock(ResponsePlanService.class),
                incidentService,
                mock(ai_soc_backend.incident.service.ApprovalDecisionAuditService.class)
        );
        when(incidentService.getAllIncidents()).thenReturn(List.of(approved));
        MockMvc listMvc = standaloneSetup(controller).build();

        listMvc.perform(get("/api/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].incidentId").value("INC-001"))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    void approvalDecisionHistoryReturnsPersistedActorAndTimestampFields() throws Exception {
        IncidentService incidents = mock(IncidentService.class);
        ApprovalDecisionAuditService audit = mock(ApprovalDecisionAuditService.class);
        when(incidents.getIncidentById("INC-001"))
                .thenReturn(new Incident("INC-001", "Incident", "HIGH", "APPROVED", "host", "rule", null));
        when(audit.listForIncident("INC-001")).thenReturn(List.of(new ApprovalDecisionResponse(
                "INC-001", "APPROVED", Instant.parse("2026-10-29T10:00:00Z"), null)));
        IncidentController controller = new IncidentController(
                mock(AIInvestigationPersistenceService.class), approvalService,
                mock(ResponsePlanService.class), incidents, audit);

        standaloneSetup(controller).build().perform(get("/api/incidents/INC-001/approval-decisions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].incidentId").value("INC-001"))
                .andExpect(jsonPath("$[0].decision").value("APPROVED"))
                .andExpect(jsonPath("$[0].decidedAt").value("2026-10-29T10:00:00Z"))
                .andExpect(jsonPath("$[0].actorId").value(org.hamcrest.Matchers.nullValue()));
        verify(incidents).getIncidentById("INC-001");
        verify(audit).listForIncident("INC-001");
    }
}
