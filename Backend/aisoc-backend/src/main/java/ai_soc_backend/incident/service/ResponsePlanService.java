package ai_soc_backend.incident.service;

import ai_soc_backend.incident.dto.ResponsePlanResponse;
import ai_soc_backend.incident.dto.IncidentResponse;

public interface ResponsePlanService {

    ResponsePlanResponse generatePlan(IncidentResponse incident);
}