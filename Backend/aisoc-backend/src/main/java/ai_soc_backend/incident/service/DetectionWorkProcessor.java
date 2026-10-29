package ai_soc_backend.incident.service;

import ai_soc_backend.incident.entity.Alert;
import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.repository.AlertRepository;
import ai_soc_backend.incident.repository.IncidentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class DetectionWorkProcessor {
    private static final String FAILED_LOGON = "FAILED_LOGON";
    private static final int THRESHOLD = 5;
    private static final long BUCKET_SECONDS = 300;
    private static final String ACTIVE = "OPEN,INVESTIGATING,AWAITING_APPROVAL";

    private final JdbcTemplate jdbc;
    private final AlertRepository alerts;
    private final IncidentRepository incidents;

    public DetectionWorkProcessor(JdbcTemplate jdbc, AlertRepository alerts, IncidentRepository incidents) {
        this.jdbc = jdbc;
        this.alerts = alerts;
        this.incidents = incidents;
    }

    /** One transaction covers ownership check, incident, investigation enqueue and completion. */
    @Transactional(timeout = 90)
    public boolean process(DetectionWorkClaim claim) {
        UUID currentToken;
        try {
            currentToken = jdbc.queryForObject("""
                    SELECT claim_token FROM public.alert_detection_work
                     WHERE alert_pk=? AND status='IN_PROGRESS'
                       AND lease_expires_at > CURRENT_TIMESTAMP
                     FOR UPDATE
                    """, UUID.class, claim.alertPk());
        } catch (EmptyResultDataAccessException missing) {
            return false;
        }
        if (!claim.token().equals(currentToken)) return false;

        Alert alert = alerts.findById(claim.alertPk()).orElseThrow(
                () -> new IllegalStateException("Claimed alert row is missing"));
        String result;
        if (!FAILED_LOGON.equals(alert.getRule())) {
            result = "NOT_APPLICABLE";
        } else if (alert.getEventTimestampUtc() == null) {
            result = alert.getTimestamp() == null ? "SKIPPED_MISSING_TIMESTAMP" : "SKIPPED_LEGACY_TIMESTAMP";
        } else {
            String host = canonicalHost(alert.getHost());
            if (host == null) {
                result = "SKIPPED_UNIDENTIFIED_HOST";
            } else {
                result = correlate(alert, host);
            }
        }

        int completed = jdbc.update("""
                UPDATE public.alert_detection_work
                   SET status='COMPLETED', result_code=?, lease_expires_at=NULL, claim_token=NULL,
                       last_error_code=NULL, last_error_detail=NULL, updated_at=CURRENT_TIMESTAMP,
                       finished_at=CURRENT_TIMESTAMP
                 WHERE alert_pk=? AND status='IN_PROGRESS' AND claim_token=?
                """, result, claim.alertPk(), claim.token());
        if (completed != 1) throw new IllegalStateException("Detection claim ownership was lost");
        return true;
    }

    private String correlate(Alert current, String host) {
        Instant eventTime = current.getEventTimestampUtc();
        long bucketEpoch = bucketStartEpoch(eventTime.getEpochSecond());
        Instant start = Instant.ofEpochSecond(bucketEpoch);
        Instant end = start.plusSeconds(BUCKET_SECONDS);
        long count = alerts.countByRuleAndHostAndEventTimestampUtcGreaterThanEqualAndEventTimestampUtcLessThan(
                FAILED_LOGON, host, start, end);
        if (count < THRESHOLD) return "BELOW_THRESHOLD";

        String key = correlationKey(host, bucketEpoch);
        IncidentAndCreated incident = findOrCreateIncident(key, host, eventTime);
        enqueueInvestigation(incident.incident());
        return incident.created() ? "INCIDENT_CREATED"
                : isActive(incident.incident().getStatus()) ? "INCIDENT_REUSED_ACTIVE" : "INCIDENT_REUSED_CLOSED";
    }

    private IncidentAndCreated findOrCreateIncident(String key, String host, Instant eventTime) {
        var existing = incidents.findByCorrelationKey(key);
        if (existing.isPresent()) return new IncidentAndCreated(existing.get(), false);

        String incidentId = "INC-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime utcTimestamp = LocalDateTime.ofInstant(eventTime, ZoneOffset.UTC);
        Long id;
        try {
            id = jdbc.queryForObject("""
                    INSERT INTO public.incidents
                        (incident_id, title, severity, status, host, detection, timestamp, correlation_key)
                    VALUES (?, 'Possible Brute Force Attack', 'HIGH', 'OPEN', ?,
                            'Multiple Failed Logins', ?, ?)
                    RETURNING id
                    """, Long.class, incidentId, host, utcTimestamp, key);
        } catch (DataIntegrityViolationException conflict) {
            if (isCorrelationKeyConflict(conflict)) throw new CorrelationKeyConflictException(conflict);
            throw conflict;
        }
        Incident incident = incidents.findById(id).orElseThrow();
        return new IncidentAndCreated(incident, true);
    }

    private static boolean isCorrelationKeyConflict(Throwable failure) {
        boolean uniqueViolation = false;
        boolean expectedConstraint = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) uniqueViolation = true;
            if (cause.getMessage() != null
                    && cause.getMessage().contains("uq_incidents_correlation_key")) expectedConstraint = true;
        }
        return uniqueViolation && expectedConstraint;
    }

    private void enqueueInvestigation(Incident incident) {
        boolean active = isActive(incident.getStatus());
        boolean alreadyPersisted = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM public.ai_investigations WHERE incident_pk=?)",
                Boolean.class, incident.getId()));
        String status = active && !alreadyPersisted ? "PENDING" : "COMPLETED";
        String result = active ? "ALREADY_PERSISTED" : "NO_INVESTIGATION_REQUIRED";
        jdbc.update("""
                INSERT INTO public.incident_investigation_work
                    (incident_pk, status, attempt_count, available_at, created_at, updated_at,
                     finished_at, result_code)
                VALUES (?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                        CASE WHEN ?='COMPLETED' THEN CURRENT_TIMESTAMP ELSE NULL END,
                        CASE WHEN ?='COMPLETED' THEN ? ELSE NULL END)
                ON CONFLICT (incident_pk) DO NOTHING
                """, incident.getId(), status, status, status, result);
    }

    private static boolean isActive(String status) {
        return List.of(ACTIVE.split(",")).contains(status);
    }

    public static String canonicalHost(String raw) {
        return HostIdentity.canonicalize(raw);
    }

    public static String correlationKey(String canonicalHost, long bucketStartEpoch) {
        String material = "ai-soc|v1|FAILED_LOGON|" + canonicalHost + "|" + bucketStartEpoch;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static long bucketStartEpoch(long epochSeconds) {
        return Math.floorDiv(epochSeconds, BUCKET_SECONDS) * BUCKET_SECONDS;
    }

    private record IncidentAndCreated(Incident incident, boolean created) { }
}
