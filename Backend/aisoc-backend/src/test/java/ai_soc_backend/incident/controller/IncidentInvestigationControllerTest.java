package ai_soc_backend.incident.controller;

import ai_soc_backend.incident.dto.AIInvestigationResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class IncidentInvestigationControllerTest {

    private AIInvestigationPersistenceService investigationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        investigationService = mock(AIInvestigationPersistenceService.class);
        IncidentController controller = new IncidentController(
                investigationService,
                mock(ApprovalService.class),
                mock(ResponsePlanService.class),
                mock(IncidentService.class)
        );
        mockMvc = standaloneSetup(controller).build();
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
}
