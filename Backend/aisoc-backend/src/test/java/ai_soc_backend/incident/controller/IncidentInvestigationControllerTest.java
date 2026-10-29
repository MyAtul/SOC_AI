package ai_soc_backend.incident.controller;

import ai_soc_backend.incident.dto.AIInvestigationResponse;
import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.service.AIInvestigationPersistenceService;
import ai_soc_backend.incident.service.ApprovalService;
import ai_soc_backend.incident.service.IncidentService;
import ai_soc_backend.incident.service.ResponsePlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class IncidentInvestigationControllerTest {

    private AIInvestigationPersistenceService investigationService;
    private IncidentService incidentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        investigationService = mock(AIInvestigationPersistenceService.class);
        incidentService = mock(IncidentService.class);
        IncidentController controller = new IncidentController(
                investigationService,
                mock(ApprovalService.class),
                mock(ResponsePlanService.class),
                incidentService,
                mock(ai_soc_backend.incident.service.ApprovalDecisionAuditService.class)
        );
        mockMvc = standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsPersistedInvestigation() throws Exception {
        when(investigationService.getPersisted("INC-101"))
                .thenReturn(Optional.of(new AIInvestigationResponse(
                        "INC-101",
                        "Credential Brute Force",
                        "HIGH",
                        0.87,
                        "T1110 - Brute Force",
                        List.of("Repeated authentication failures"),
                        "Repeated login failures were observed.",
                        "REVIEW_MANUALLY",
                        "MEDIUM",
                        true
                )));

        mockMvc.perform(get("/api/incidents/INC-101/investigation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value("INC-101"))
                .andExpect(jsonPath("$.classification").value("Credential Brute Force"))
                .andExpect(jsonPath("$.confidence").value(0.87))
                .andExpect(jsonPath("$.evidence[0]").value("Repeated authentication failures"))
                .andExpect(jsonPath("$.approvalRequired").value(true));
    }

    @Test
    void returnsNotFoundWhenInvestigationIsMissing() throws Exception {
        when(investigationService.getPersisted("INC-101")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/incidents/INC-101/investigation"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundErrorBodyWhenIncidentDoesNotExist() throws Exception {
        when(incidentService.getIncidentById("missing"))
                .thenThrow(new ai_soc_backend.incident.service.IncidentNotFoundException("missing"));

        mockMvc.perform(get("/api/incidents/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Incident not found"));
    }

    @Test
    void returnsNotFoundWhenInvestigatedIncidentDoesNotExist() throws Exception {
        when(incidentService.getIncidentById("missing"))
                .thenThrow(new ai_soc_backend.incident.service.IncidentNotFoundException("missing"));

        mockMvc.perform(post("/api/incidents/missing/investigate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void serializesNullIncidentTimestampAsJsonNull() throws Exception {
        when(incidentService.getIncidentById("INC-102"))
                .thenReturn(new Incident("INC-102", "Test incident", "LOW", "OPEN", "host-1", "test", null));

        mockMvc.perform(get("/api/incidents/INC-102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").value(nullValue()));
    }

    @Test
    void returnsSafeInternalErrorBodyForUnexpectedFailure() throws Exception {
        when(incidentService.getIncidentById("INC-500"))
                .thenThrow(new IllegalStateException("database password must not leak"));

        mockMvc.perform(get("/api/incidents/INC-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected server error occurred"));
    }
}
