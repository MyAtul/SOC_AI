package ai_soc_backend.incident.controller;

import ai_soc_backend.incident.entity.Alert;
import ai_soc_backend.incident.service.AlertService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AlertControllerTest {
    @Test
    void acceptsOffsetAwareUtcInputAndKeepsEventInstantOutOfResponse() throws Exception {
        AlertService service = mock(AlertService.class);
        when(service.processAlert(any(Alert.class))).thenAnswer(call -> call.getArgument(0));
        MockMvc mvc = standaloneSetup(new AlertController(service)).build();

        mvc.perform(post("/api/alerts").contentType(APPLICATION_JSON).content("""
                        {"alertId":"api-1","source":"test","rule":"FAILED_LOGON","severity":"LOW",
                         "host":"host-1","description":"test","timestamp":"2026-10-28T12:00:00",
                         "eventTimestampUtc":"2026-10-28T12:00:00+02:00"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertId").value("api-1"))
                .andExpect(jsonPath("$.eventTimestampUtc").doesNotExist());

        org.mockito.ArgumentCaptor<Alert> captured = org.mockito.ArgumentCaptor.forClass(Alert.class);
        org.mockito.Mockito.verify(service).processAlert(captured.capture());
        assertEquals(Instant.parse("2026-10-28T10:00:00Z"), captured.getValue().getEventTimestampUtc());
    }
}
