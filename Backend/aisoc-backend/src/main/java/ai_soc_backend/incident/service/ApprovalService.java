package ai_soc_backend.incident.service;

import ai_soc_backend.incident.dto.ApprovalResponse;

public interface ApprovalService {

    ApprovalResponse approve(String incidentId);

    ApprovalResponse deny(String incidentId);
}