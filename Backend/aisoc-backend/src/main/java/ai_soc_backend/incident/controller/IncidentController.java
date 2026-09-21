package ai_soc_backend.incident.controller;

import ai_soc_backend.incident.dto.AIInvestigationResponse;
import ai_soc_backend.incident.dto.IncidentResponse;
import ai_soc_backend.incident.service.AIInvestigationService;
import ai_soc_backend.incident.service.ApprovalService;
import ai_soc_backend.incident.service.ResponsePlanService;
import org.springframework.web.bind.annotation.*;
import ai_soc_backend.incident.dto.ApprovalResponse;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
@CrossOrigin(origins = "http://localhost:5173")
public class IncidentController {

    private final AIInvestigationService aiInvestigationService;

    private final ApprovalService approvalService;

    private final ResponsePlanService responsePlanService;

    public IncidentController(
            AIInvestigationService aiInvestigationService, ApprovalService approvalService, ResponsePlanService responsePlanService
    ) {
        this.aiInvestigationService = aiInvestigationService;
        this.approvalService = approvalService;
        this.responsePlanService = responsePlanService;
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

        return List.of(

                new IncidentResponse(
                        "INC-001",
                        "Suspicious PowerShell Activity",
                        "HIGH",
                        "INVESTIGATING",
                        "WIN-CLIENT-01",
                        "Suspicious PowerShell",
                        "2026-09-20T17:30:00"
                ),

                new IncidentResponse(
                        "INC-002",
                        "Multiple Failed Logins",
                        "MEDIUM",
                        "OPEN",
                        "SERVER-01",
                        "Brute Force Detection",
                        "2026-09-20T16:45:00"
                ),

                new IncidentResponse(
                        "INC-003",
                        "Privilege Escalation",
                        "CRITICAL",
                        "AWAITING_APPROVAL",
                        "SERVER-02",
                        "Privilege Escalation Detection",
                        "2026-09-20T15:20:00"
                )
        );
    }

    @GetMapping("/{incidentId}")
    public IncidentResponse getIncident(
            @PathVariable String incidentId
    ) {
        return findIncident(incidentId);
    }

    @PostMapping("/{incidentId}/investigate")
    public AIInvestigationResponse investigate(
            @PathVariable String incidentId
    ) {

        IncidentResponse incident = findIncident(incidentId);

        return aiInvestigationService.investigate(incident);
    }

    private IncidentResponse findIncident(String incidentId) {

        return switch (incidentId) {

            case "INC-001" -> new IncidentResponse(
                    "INC-001",
                    "Suspicious PowerShell Activity",
                    "HIGH",
                    "INVESTIGATING",
                    "WIN-CLIENT-01",
                    "Suspicious PowerShell",
                    "2026-09-20T17:30:00"
            );

            case "INC-002" -> new IncidentResponse(
                    "INC-002",
                    "Multiple Failed Logins",
                    "MEDIUM",
                    "OPEN",
                    "SERVER-01",
                    "Brute Force Detection",
                    "2026-09-20T16:45:00"
            );

            case "INC-003" -> new IncidentResponse(
                    "INC-003",
                    "Privilege Escalation",
                    "CRITICAL",
                    "AWAITING_APPROVAL",
                    "SERVER-02",
                    "Privilege Escalation Detection",
                    "2026-09-20T15:20:00"
            );

            default -> throw new IllegalArgumentException(
                    "Incident not found: " + incidentId
            );
        };
    }
}
