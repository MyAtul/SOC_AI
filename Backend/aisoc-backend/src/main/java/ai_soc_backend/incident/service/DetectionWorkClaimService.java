package ai_soc_backend.incident.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DetectionWorkClaimService {
    private final JdbcTemplate jdbc;

    public DetectionWorkClaimService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public List<DetectionWorkClaim> claim(int limit) {
        // An expired fifth attempt is terminal; the schema forbids it returning to PENDING.
        jdbc.update("""
                UPDATE public.alert_detection_work
                   SET status='FAILED', lease_expires_at=NULL, claim_token=NULL,
                       last_error_code='LEASE_EXPIRED_ATTEMPT_EXHAUSTED',
                       last_error_detail='Lease expired after final attempt',
                       updated_at=CURRENT_TIMESTAMP, finished_at=CURRENT_TIMESTAMP
                 WHERE status='IN_PROGRESS' AND attempt_count >= 5
                   AND lease_expires_at <= CURRENT_TIMESTAMP
                """);
        List<DetectionWorkClaim> claims = jdbc.query("""
                SELECT alert_pk, attempt_count
                  FROM public.alert_detection_work
                 WHERE (status='PENDING' AND available_at <= CURRENT_TIMESTAMP AND attempt_count < 5)
                    OR (status='IN_PROGRESS' AND lease_expires_at <= CURRENT_TIMESTAMP AND attempt_count < 5)
                 ORDER BY available_at, created_at
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new DetectionWorkClaim(rs.getLong(1), null, rs.getShort(2)), limit);
        List<DetectionWorkClaim> issued = new ArrayList<>(claims.size());
        for (DetectionWorkClaim candidate : claims) {
            UUID token = UUID.randomUUID();
            short attempt = (short) (candidate.attempt() + 1);
            jdbc.update("""
                    UPDATE public.alert_detection_work
                       SET status='IN_PROGRESS', attempt_count=?, claim_token=?,
                           lease_expires_at=CURRENT_TIMESTAMP + INTERVAL '2 minutes',
                           updated_at=CURRENT_TIMESTAMP, finished_at=NULL, result_code=NULL
                     WHERE alert_pk=?
                    """, attempt, token, candidate.alertPk());
            issued.add(new DetectionWorkClaim(candidate.alertPk(), token, attempt));
        }
        return issued;
    }
}
