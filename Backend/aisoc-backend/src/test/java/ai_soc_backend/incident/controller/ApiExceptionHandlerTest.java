package ai_soc_backend.incident.controller;

import ai_soc_backend.incident.entity.Alert;
import ai_soc_backend.incident.service.AlertService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ApiExceptionHandlerTest {

    @Test
    void returnsBadRequestForValidationFailure() throws Exception {
        MockMvc mockMvc = standaloneSetup(new ValidationProbeController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(post("/validation-probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"));
    }

    @Test
    void returnsBadRequestForMalformedJson() throws Exception {
        AlertService alertService = mock(AlertService.class);
        org.mockito.Mockito.when(alertService.processAlert(any(Alert.class))).thenAnswer(call -> call.getArgument(0));
        MockMvc mockMvc = standaloneSetup(new AlertController(alertService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(post("/api/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @RestController
    static class ValidationProbeController {
        @PostMapping("/validation-probe")
        void validate(@Valid @RequestBody ValidationProbe request) {
        }
    }

    record ValidationProbe(@NotBlank String value) {
    }
}
