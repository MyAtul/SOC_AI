package ai_soc_backend.incident.service;

import ai_soc_backend.incident.dto.AIInvestigationResponse;
import ai_soc_backend.incident.dto.IncidentResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MockAIInvestigationService implements AIInvestigationService {

    @Override
    public AIInvestigationResponse investigate(IncidentResponse incident) {

        return switch (incident.incidentId()) {

            // =========================
            // INCIDENT 1
            // =========================
            case "INC-001" -> new AIInvestigationResponse(
                    "INC-001",
                    "Suspicious Command Execution",
                    "HIGH",
                    0.91,
                    "T1059 - Command and Scripting Interpreter",

                    List.of(
                            "Encoded PowerShell command detected",
                            "PowerShell spawned by unusual parent process",
                            "Unusual outbound network connection detected"
                    ),

                    "The incident contains multiple indicators of suspicious command execution and possible malicious PowerShell activity.",

                    "ISOLATE_HOST",

                    "HIGH",
                    true
            );

            // =========================
            // INCIDENT 2
            // =========================
            case "INC-002" -> new AIInvestigationResponse(
                    "INC-002",
                    "Brute Force Authentication Attempt",
                    "MEDIUM",
                    0.87,
                    "T1110 - Brute Force",

                    List.of(
                            "Multiple failed login attempts detected",
                            "Login attempts originated from the same external IP",
                            "Authentication attempts targeted multiple accounts"
                    ),

                    "The incident shows a repeated authentication pattern consistent with a possible brute-force attack against the organization's accounts.",

                    "BLOCK_SOURCE_IP",

                    "MEDIUM",
                    false
            );

            // =========================
            // INCIDENT 3
            // =========================
            case "INC-003" -> new AIInvestigationResponse(
                    "INC-003",
                    "Privilege Escalation",
                    "CRITICAL",
                    0.96,
                    "T1548 - Abuse Elevation Control Mechanism",

                    List.of(
                            "User account gained elevated privileges",
                            "Privilege change occurred outside the normal access pattern",
                            "Sensitive administrative resource accessed after privilege escalation"
                    ),

                    "The incident indicates a potentially unauthorized privilege escalation that may provide access to sensitive administrative resources.",

                    "DISABLE_ACCOUNT",

                    "CRITICAL",
                    true
            );

            // =========================
            // UNKNOWN INCIDENT
            // =========================
            default -> new AIInvestigationResponse(
                    incident.incidentId(),
                    "Unknown Activity",
                    "LOW",
                    0.50,
                    "Unknown",

                    List.of(
                            "Insufficient evidence available"
                    ),

                    "The system does not have enough evidence to confidently classify this incident.",

                    "REVIEW_MANUALLY",

                    "LOW",
                    false
            );
        };
    }
}