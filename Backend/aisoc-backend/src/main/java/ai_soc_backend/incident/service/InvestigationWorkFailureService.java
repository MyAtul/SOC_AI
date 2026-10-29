package ai_soc_backend.incident.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestigationWorkFailureService {
    private final JdbcTemplate jdbc;
    public InvestigationWorkFailureService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void fail(InvestigationWorkClaim claim, RuntimeException failure) {
        boolean permanent = failure instanceof IllegalArgumentException;
        boolean terminal = permanent || claim.attempt() >= 5;
        long delay = Math.min(60, 1L << Math.max(0, claim.attempt() - 1));
        String code = terminal && !permanent ? "ATTEMPTS_EXHAUSTED"
                : permanent ? "PERMANENT_PROCESSING_ERROR" : "RETRYABLE_PROCESSING_ERROR";
        jdbc.update("""
                UPDATE public.incident_investigation_work
                   SET status=?, available_at=CASE WHEN ? THEN available_at
                         ELSE CURRENT_TIMESTAMP + (? * INTERVAL '1 second') END,
                       lease_expires_at=NULL, claim_token=NULL, result_code=NULL,
                       last_error_code=?, last_error_detail=?, updated_at=CURRENT_TIMESTAMP,
                       finished_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END
                 WHERE incident_pk=? AND status='IN_PROGRESS' AND claim_token=?
                   AND lease_expires_at>CURRENT_TIMESTAMP
                """, terminal ? "FAILED" : "PENDING", terminal, delay, code,
                failure.getClass().getSimpleName(), terminal, claim.incidentPk(), claim.token());
    }
}
