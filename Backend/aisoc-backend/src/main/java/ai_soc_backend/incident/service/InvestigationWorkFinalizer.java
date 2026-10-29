package ai_soc_backend.incident.service;

import ai_soc_backend.incident.dto.AIInvestigationResponse;
import ai_soc_backend.incident.entity.AIInvestigation;
import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.repository.AIInvestigationRepository;
import ai_soc_backend.incident.repository.IncidentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class InvestigationWorkFinalizer {
    private final JdbcTemplate jdbc;
    private final IncidentRepository incidents;
    private final AIInvestigationRepository investigations;

    public InvestigationWorkFinalizer(JdbcTemplate jdbc, IncidentRepository incidents,
                                      AIInvestigationRepository investigations) {
        this.jdbc = jdbc;
        this.incidents = incidents;
        this.investigations = investigations;
    }

    @Transactional(timeout = 30)
    public boolean persistAndComplete(InvestigationWorkClaim claim, AIInvestigationResponse response) {
        if (!ownsLiveClaim(claim)) return false;
        Incident incident = incidents.findById(claim.incidentPk()).orElseThrow();
        AIInvestigation existing = investigations.findByIncident_IncidentId(incident.getIncidentId()).orElse(null);
        String resultCode;
        if (existing != null) {
            resultCode = "ALREADY_PERSISTED";
        } else {
            AIInvestigation created = new AIInvestigation(incident, response);
            investigations.saveAndFlush(created);
            resultCode = "INVESTIGATION_PERSISTED";
        }
        return complete(claim, resultCode);
    }

    @Transactional(timeout = 30)
    public boolean completeAlreadyPersisted(InvestigationWorkClaim claim) {
        if (!ownsLiveClaim(claim)) return false;
        Incident incident = incidents.findById(claim.incidentPk()).orElseThrow();
        if (investigations.findByIncident_IncidentId(incident.getIncidentId()).isEmpty()) return false;
        return complete(claim, "ALREADY_PERSISTED");
    }

    private boolean ownsLiveClaim(InvestigationWorkClaim claim) {
        UUID current;
        try {
            current = jdbc.queryForObject("""
                    SELECT claim_token FROM public.incident_investigation_work
                     WHERE incident_pk=? AND status='IN_PROGRESS'
                       AND lease_expires_at>CURRENT_TIMESTAMP FOR UPDATE
                    """, UUID.class, claim.incidentPk());
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            return false;
        }
        return claim.token().equals(current);
    }

    private boolean complete(InvestigationWorkClaim claim, String resultCode) {
        return jdbc.update("""
                UPDATE public.incident_investigation_work
                   SET status='COMPLETED', result_code=?, lease_expires_at=NULL, claim_token=NULL,
                       last_error_code=NULL, last_error_detail=NULL, updated_at=CURRENT_TIMESTAMP,
                       finished_at=CURRENT_TIMESTAMP
                 WHERE incident_pk=? AND status='IN_PROGRESS' AND claim_token=?
                """, resultCode, claim.incidentPk(), claim.token()) == 1;
    }
}
