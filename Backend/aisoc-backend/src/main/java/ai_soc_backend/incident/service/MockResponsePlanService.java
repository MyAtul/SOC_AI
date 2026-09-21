package ai_soc_backend.incident.service;

import ai_soc_backend.incident.dto.IncidentResponse;
import ai_soc_backend.incident.dto.ResponsePlanResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MockResponsePlanService implements ResponsePlanService {

    @Override
    public ResponsePlanResponse generatePlan(IncidentResponse incident) {

        return new ResponsePlanResponse(
                incident.incidentId(),
                "ISOLATE_HOST",
                "HIGH",
                true,
                "PENDING_APPROVAL",
                List.of(
                        "Validate incident evidence",
                        "Verify affected host",
                        "Isolate the affected host from the network",
                        "Continue monitoring the incident",
                        "Record the action in the audit log"
                ),
                "Host isolation is recommended because the incident contains indicators of suspicious PowerShell execution and unusual network activity."
        );
    }
}