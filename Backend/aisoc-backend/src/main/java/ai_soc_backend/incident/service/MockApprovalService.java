package ai_soc_backend.incident.service;

import ai_soc_backend.incident.dto.ApprovalResponse;
import ai_soc_backend.incident.dto.PolicyDecision;
import org.springframework.stereotype.Service;

@Service
public class MockApprovalService implements ApprovalService {

    private final PolicyService policyService;

    public MockApprovalService(PolicyService policyService) {
        this.policyService = policyService;
    }

    @Override
    public ApprovalResponse approve(String incidentId) {

        PolicyDecision policy = policyService.evaluate(incidentId);

        // Policy does not allow this action
        if (!policy.allowed()) {
            return new ApprovalResponse(
                    incidentId,
                    policy.action(),
                    "REJECTED",
                    "POLICY_DENIED",
                    policy.reason()
            );
        }

        // This incident does not require human approval
        if (!policy.approvalRequired()) {
            return new ApprovalResponse(
                    incidentId,
                    policy.action(),
                    "NOT_REQUIRED",
                    "AUTO_ALLOWED",
                    "This action does not require human approval according to the current policy."
            );
        }

        // Human approval is required and has been granted
        return new ApprovalResponse(
                incidentId,
                policy.action(),
                "APPROVED",
                "APPROVED",
                "The recommended response action has been approved by the analyst."
        );
    }

    @Override
    public ApprovalResponse deny(String incidentId) {

        PolicyDecision policy = policyService.evaluate(incidentId);

        if (!policy.allowed()) {
            return new ApprovalResponse(
                    incidentId,
                    policy.action(),
                    "REJECTED",
                    "POLICY_DENIED",
                    policy.reason()
            );
        }

        if (!policy.approvalRequired()) {
            return new ApprovalResponse(
                    incidentId,
                    policy.action(),
                    "NOT_REQUIRED",
                    "AUTO_ALLOWED",
                    "This incident does not require human approval."
            );
        }

        return new ApprovalResponse(
                incidentId,
                policy.action(),
                "DENIED",
                "DENIED",
                "The recommended response action has been rejected by the analyst."
        );
    }
}