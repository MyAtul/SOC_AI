package ai_soc_backend.incident.controller;

import ai_soc_backend.incident.dto.AIInvestigationResponse;
import ai_soc_backend.incident.dto.ApprovalResponse;
import ai_soc_backend.incident.dto.IncidentResponse;
import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.service.AIInvestigationPersistenceService;
import ai_soc_backend.incident.service.ApprovalService;
import ai_soc_backend.incident.service.IncidentService;
import ai_soc_backend.incident.service.ResponsePlanService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
@CrossOrigin(origins = "http://localhost:5173")
public class IncidentController {

    private final AIInvestigationPersistenceService aiInvestigationPersistenceService;
    private final ApprovalService approvalService;
    private final ResponsePlanService responsePlanService;
    private final IncidentService incidentService;

    public IncidentController(
            AIInvestigationPersistenceService aiInvestigationPersistenceService,
            ApprovalService approvalService,
            ResponsePlanService responsePlanService,
            IncidentService incidentService
    ) {
        this.aiInvestigationPersistenceService = aiInvestigationPersistenceService;
        this.approvalService = approvalService;
        this.responsePlanService = responsePlanService;
        this.incidentService = incidentService;
    }

    @PostMapping("/{incidentId}/approve")
    public ApprovalResponse approve(
            @PathVariable String incidentId
    ) {
        return approvalService.approve(incidentId);
    }

    @PostMapping("/{incidentId}/deny")
    public ApprovalResponse deny(
            @PathVariable String incidentId
    ) {
        return approvalService.deny(incidentId);
    }

    @GetMapping
    public List<IncidentResponse> getIncidents() {

        return incidentService.getAllIncidents()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{incidentId}")
    public IncidentResponse getIncident(
            @PathVariable String incidentId
    ) {
        return toResponse(
                incidentService.getIncidentById(incidentId)
        );
    }

    @PostMapping("/{incidentId}/investigate")
    public AIInvestigationResponse investigate(
            @PathVariable String incidentId
    ) {
        Incident incident = incidentService.getIncidentById(incidentId);
        return aiInvestigationPersistenceService.investigateAndPersist(incident);
    }

    @GetMapping("/{incidentId}/investigation")
    public ResponseEntity<AIInvestigationResponse> getInvestigation(
            @PathVariable String incidentId
    ) {
        return ResponseEntity.of(aiInvestigationPersistenceService.getPersisted(incidentId));
    }

    private IncidentResponse toResponse(Incident incident) {

        return new IncidentResponse(
                incident.getIncidentId(),
                incident.getTitle(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getHost(),
                incident.getDetection(),
                incident.getTimestamp().toString()
        );
    }
}
