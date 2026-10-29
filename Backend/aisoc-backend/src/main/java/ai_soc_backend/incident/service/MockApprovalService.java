package ai_soc_backend.incident.service;

import ai_soc_backend.incident.dto.ApprovalResponse;
import ai_soc_backend.incident.dto.PolicyDecision;
import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class MockApprovalService implements ApprovalService {

    private static final Set<String> DECIDABLE_STATUSES = Set.of(
            "OPEN", "INVESTIGATING", "AWAITING_APPROVAL");

    private final PolicyService policyService;
    private final IncidentRepository incidentRepository;
    private final ApprovalDecisionAuditService decisionAudit;

    public MockApprovalService(PolicyService policyService, IncidentRepository incidentRepository,
                               ApprovalDecisionAuditService decisionAudit) {
        this.policyService = policyService;
        this.incidentRepository = incidentRepository;
        this.decisionAudit = decisionAudit;
    }

    @Override
    @Transactional
    public ApprovalResponse approve(String incidentId) {
        return decide(incidentId, true);
    }

    @Override
    @Transactional
    public ApprovalResponse deny(String incidentId) {
        return decide(incidentId, false);
    }

    private ApprovalResponse decide(String incidentId, boolean approve) {
        Incident incident = incidentRepository.findByIncidentIdForUpdate(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
        PolicyDecision policy = policyService.evaluate(incidentId);
        if (!policy.allowed()) {
            return new ApprovalResponse(incidentId, policy.action(), "REJECTED", "POLICY_DENIED", policy.reason());
        }
        if (!policy.approvalRequired()) {
            return new ApprovalResponse(
                    incidentId,
                    policy.action(),
                    "NOT_REQUIRED",
                    "AUTO_ALLOWED",
                    approve
                            ? "This action does not require human approval according to the current policy."
                            : "This incident does not require human approval."
            );
        }

        String requestedStatus = approve ? "APPROVED" : "DENIED";
        if (requestedStatus.equals(incident.getStatus())) {
            return decisionResponse(incidentId, policy, approve);
        }
        if ("APPROVED".equals(incident.getStatus()) || "DENIED".equals(incident.getStatus())
                || !DECIDABLE_STATUSES.contains(incident.getStatus())) {
            throw new ApprovalDecisionConflictException(incidentId, incident.getStatus());
        }

        incident.setStatus(requestedStatus);
        incidentRepository.save(incident);
        decisionAudit.record(incident, requestedStatus);
        return decisionResponse(incidentId, policy, approve);
    }

    private static ApprovalResponse decisionResponse(String incidentId, PolicyDecision policy, boolean approve) {
        return new ApprovalResponse(
                incidentId,
                policy.action(),
                approve ? "APPROVED" : "DENIED",
                approve ? "APPROVED" : "DENIED",
                approve
                        ? "Approval decision recorded. No response action was executed."
                        : "Denial decision recorded. No response action was executed."
        );
    }
}
