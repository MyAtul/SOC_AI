package ai_soc_backend.incident.service;

import ai_soc_backend.incident.dto.PolicyDecision;
import org.springframework.stereotype.Service;

@Service
public class MockPolicyService implements PolicyService {

    @Override
    public PolicyDecision evaluate(String incidentId) {

        return switch (incidentId) {

            case "INC-001" -> new PolicyDecision(
                    "INC-001",
                    "ISOLATE_HOST",
                    true,
                    true,
                    "High-risk host isolation requires human approval."
            );

            case "INC-002" -> new PolicyDecision(
                    "INC-002",
                    "BLOCK_SOURCE_IP",
                    true,
                    false,
                    "Medium-risk source IP blocking is permitted without manual approval."
            );

            case "INC-003" -> new PolicyDecision(
                    "INC-003",
                    "DISABLE_ACCOUNT",
                    true,
                    true,
                    "Critical account action requires human approval."
            );

            default -> new PolicyDecision(
                    incidentId,
                    "UNKNOWN_ACTION",
                    false,
                    true,
                    "Unknown incident. Action is not permitted."
            );
        };
    }
}