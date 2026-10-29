package ai_soc_backend.incident.service;

import ai_soc_backend.incident.dto.PolicyDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MockPolicyServiceTest {

    private final MockPolicyService service = new MockPolicyService();

    @Test
    void allowsHostIsolationOnlyWithHumanApprovalForInc001() {
        assertEquals(new PolicyDecision(
                "INC-001",
                "ISOLATE_HOST",
                true,
                true,
                "High-risk host isolation requires human approval."
        ), service.evaluate("INC-001"));
    }

    @Test
    void allowsSourceIpBlockingWithoutHumanApprovalForInc002() {
        assertEquals(new PolicyDecision(
                "INC-002",
                "BLOCK_SOURCE_IP",
                true,
                false,
                "Medium-risk source IP blocking is permitted without manual approval."
        ), service.evaluate("INC-002"));
    }

    @Test
    void allowsAccountDisablingOnlyWithHumanApprovalForInc003() {
        assertEquals(new PolicyDecision(
                "INC-003",
                "DISABLE_ACCOUNT",
                true,
                true,
                "Critical account action requires human approval."
        ), service.evaluate("INC-003"));
    }

    @Test
    void deniesUnknownAndEmptyIncidentIds() {
        PolicyDecision expectedUnknown = new PolicyDecision(
                "INC-404",
                "UNKNOWN_ACTION",
                false,
                true,
                "Unknown incident. Action is not permitted."
        );
        assertEquals(expectedUnknown, service.evaluate("INC-404"));
        assertEquals(new PolicyDecision(
                "",
                "UNKNOWN_ACTION",
                false,
                true,
                "Unknown incident. Action is not permitted."
        ), service.evaluate(""));
    }
}
