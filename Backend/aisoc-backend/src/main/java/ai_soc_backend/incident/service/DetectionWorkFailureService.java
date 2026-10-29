package ai_soc_backend.incident.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DetectionWorkFailureService {
    private final JdbcTemplate jdbc;

    public DetectionWorkFailureService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void fail(DetectionWorkClaim claim, RuntimeException failure) {
        boolean permanent = failure instanceof IllegalArgumentException;
        boolean exhausted = claim.attempt() >= 5;
        boolean terminal = permanent || exhausted;
        long delaySeconds = Math.min(60, 1L << Math.max(0, claim.attempt() - 1));
        String code = permanent ? "PERMANENT_PROCESSING_ERROR" : "RETRYABLE_PROCESSING_ERROR";
        String detail = failure.getClass().getSimpleName(); // Never persist exception messages or payloads.
        jdbc.update("""
                UPDATE public.alert_detection_work
                   SET status=?, available_at=CASE WHEN ? THEN available_at
                         ELSE CURRENT_TIMESTAMP + (? * INTERVAL '1 second') END,
                       lease_expires_at=NULL, claim_token=NULL, result_code=NULL,
                       last_error_code=?, last_error_detail=?, updated_at=CURRENT_TIMESTAMP,
                       finished_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END
                 WHERE alert_pk=? AND status='IN_PROGRESS' AND claim_token=?
                """, terminal ? "FAILED" : "PENDING", terminal, delaySeconds,
                terminal && exhausted && !permanent ? "ATTEMPTS_EXHAUSTED" : code,
                detail, terminal, claim.alertPk(), claim.token());
    }
}
