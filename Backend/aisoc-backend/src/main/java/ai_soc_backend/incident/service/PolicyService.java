package ai_soc_backend.incident.service;

import ai_soc_backend.incident.dto.PolicyDecision;

public interface PolicyService {

    PolicyDecision evaluate(String incidentId);
}