package ai_soc_backend.incident.service;

import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.repository.AIInvestigationRepository;
import ai_soc_backend.incident.repository.IncidentRepository;
import org.springframework.stereotype.Service;

@Service
public class InvestigationWorkProcessor {
    private final IncidentRepository incidents;
    private final AIInvestigationRepository investigations;
    private final AIInvestigationPersistenceService provider;
    private final InvestigationWorkFinalizer finalizer;

    public InvestigationWorkProcessor(IncidentRepository incidents, AIInvestigationRepository investigations,
                                      AIInvestigationPersistenceService provider,
                                      InvestigationWorkFinalizer finalizer) {
        this.incidents = incidents;
        this.investigations = investigations;
        this.provider = provider;
        this.finalizer = finalizer;
    }

    /** Provider work runs outside a DB transaction; only finalization is transactional. */
    public boolean process(InvestigationWorkClaim claim) {
        Incident incident = incidents.findById(claim.incidentPk()).orElseThrow(
                () -> new IllegalStateException("Claimed incident row is missing"));
        if (investigations.findByIncident_IncidentId(incident.getIncidentId()).isPresent()) {
            return finalizer.completeAlreadyPersisted(claim);
        }
        var response = provider.investigate(incident);
        if (response == null) throw new IllegalStateException("AI investigation returned no result");
        return finalizer.persistAndComplete(claim, response);
    }
}
