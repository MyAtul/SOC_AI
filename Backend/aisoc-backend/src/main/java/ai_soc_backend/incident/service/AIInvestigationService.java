package ai_soc_backend.incident.service;

import ai_soc_backend.incident.dto.AIInvestigationResponse;
import ai_soc_backend.incident.dto.IncidentResponse;

public interface AIInvestigationService {

    AIInvestigationResponse investigate(IncidentResponse incident);
}
