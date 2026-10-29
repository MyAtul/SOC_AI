-- Durable alert detection and incident investigation work state.
-- Legacy timezone-less timestamps and existing incidents are intentionally
-- preserved; event_timestamp_utc and correlation_key remain NULL for them.

ALTER TABLE public.alerts
    ADD COLUMN event_timestamp_utc timestamptz NULL;

ALTER TABLE public.incidents
    ADD COLUMN correlation_key varchar(64) NULL;

-- A correlation key belongs to one incident permanently, including after
-- closure. PostgreSQL permits multiple NULL values in a UNIQUE constraint.
ALTER TABLE public.incidents
    ADD CONSTRAINT uq_incidents_correlation_key UNIQUE (correlation_key);

CREATE TABLE public.alert_detection_work (
    alert_pk bigint NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    attempt_count smallint NOT NULL DEFAULT 0,
    available_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_expires_at timestamptz NULL,
    claim_token uuid NULL,
    last_error_code varchar(64) NULL,
    last_error_detail varchar(1000) NULL,
    result_code varchar(64) NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at timestamptz NULL,
    operator_requeue_count integer NOT NULL DEFAULT 0,
    last_requeued_at timestamptz NULL,
    last_requeued_by varchar(128) NULL,

    CONSTRAINT pk_alert_detection_work PRIMARY KEY (alert_pk),
    CONSTRAINT fk_alert_detection_work_alert
        FOREIGN KEY (alert_pk) REFERENCES public.alerts (id),
    CONSTRAINT ck_alert_detection_work_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_alert_detection_work_attempts
        CHECK (attempt_count BETWEEN 0 AND 5),
    CONSTRAINT ck_alert_detection_work_pending_attempts
        CHECK (status <> 'PENDING' OR attempt_count < 5),
    CONSTRAINT ck_alert_detection_work_in_progress_attempts
        CHECK (status <> 'IN_PROGRESS' OR attempt_count BETWEEN 1 AND 5),
    CONSTRAINT ck_alert_detection_work_lease
        CHECK (
            (status = 'IN_PROGRESS'
                AND lease_expires_at IS NOT NULL
                AND claim_token IS NOT NULL)
            OR
            (status <> 'IN_PROGRESS'
                AND lease_expires_at IS NULL
                AND claim_token IS NULL)
        ),
    CONSTRAINT ck_alert_detection_work_finished
        CHECK (
            (status IN ('COMPLETED', 'FAILED') AND finished_at IS NOT NULL)
            OR
            (status IN ('PENDING', 'IN_PROGRESS') AND finished_at IS NULL)
        ),
    CONSTRAINT ck_alert_detection_work_result
        CHECK (
            (status = 'COMPLETED'
                AND result_code IS NOT NULL
                AND result_code IN (
                    'INCIDENT_CREATED',
                    'INCIDENT_REUSED_ACTIVE',
                    'INCIDENT_REUSED_CLOSED',
                    'BELOW_THRESHOLD',
                    'SKIPPED_LEGACY_TIMESTAMP',
                    'SKIPPED_MISSING_TIMESTAMP',
                    'SKIPPED_UNIDENTIFIED_HOST',
                    'NOT_APPLICABLE'
                ))
            OR
            (status <> 'COMPLETED' AND result_code IS NULL)
        ),
    CONSTRAINT ck_alert_detection_work_failed_error
        CHECK (
            status <> 'FAILED'
            OR (last_error_code IS NOT NULL AND btrim(last_error_code) <> '')
        ),
    CONSTRAINT ck_alert_detection_work_requeue_count
        CHECK (operator_requeue_count >= 0),
    CONSTRAINT ck_alert_detection_work_requeue_audit
        CHECK (
            (operator_requeue_count = 0
                AND last_requeued_at IS NULL
                AND last_requeued_by IS NULL)
            OR
            (operator_requeue_count > 0
                AND last_requeued_at IS NOT NULL
                AND last_requeued_by IS NOT NULL
                AND btrim(last_requeued_by) <> '')
        )
);

CREATE INDEX ix_alert_detection_work_available
    ON public.alert_detection_work (available_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX ix_alert_detection_work_expired_lease
    ON public.alert_detection_work (lease_expires_at)
    WHERE status = 'IN_PROGRESS';

CREATE TABLE public.incident_investigation_work (
    incident_pk bigint NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    attempt_count smallint NOT NULL DEFAULT 0,
    available_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_expires_at timestamptz NULL,
    claim_token uuid NULL,
    last_error_code varchar(64) NULL,
    last_error_detail varchar(1000) NULL,
    result_code varchar(64) NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at timestamptz NULL,
    operator_requeue_count integer NOT NULL DEFAULT 0,
    last_requeued_at timestamptz NULL,
    last_requeued_by varchar(128) NULL,

    CONSTRAINT pk_incident_investigation_work PRIMARY KEY (incident_pk),
    CONSTRAINT fk_incident_investigation_work_incident
        FOREIGN KEY (incident_pk) REFERENCES public.incidents (id),
    CONSTRAINT ck_incident_investigation_work_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_incident_investigation_work_attempts
        CHECK (attempt_count BETWEEN 0 AND 5),
    CONSTRAINT ck_incident_investigation_work_pending_attempts
        CHECK (status <> 'PENDING' OR attempt_count < 5),
    CONSTRAINT ck_incident_investigation_work_in_progress_attempts
        CHECK (status <> 'IN_PROGRESS' OR attempt_count BETWEEN 1 AND 5),
    CONSTRAINT ck_incident_investigation_work_lease
        CHECK (
            (status = 'IN_PROGRESS'
                AND lease_expires_at IS NOT NULL
                AND claim_token IS NOT NULL)
            OR
            (status <> 'IN_PROGRESS'
                AND lease_expires_at IS NULL
                AND claim_token IS NULL)
        ),
    CONSTRAINT ck_incident_investigation_work_finished
        CHECK (
            (status IN ('COMPLETED', 'FAILED') AND finished_at IS NOT NULL)
            OR
            (status IN ('PENDING', 'IN_PROGRESS') AND finished_at IS NULL)
        ),
    CONSTRAINT ck_incident_investigation_work_result
        CHECK (
            (status = 'COMPLETED'
                AND result_code IS NOT NULL
                AND result_code IN (
                    'INVESTIGATION_PERSISTED',
                    'ALREADY_PERSISTED',
                    'NO_INVESTIGATION_REQUIRED'
                ))
            OR
            (status <> 'COMPLETED' AND result_code IS NULL)
        ),
    CONSTRAINT ck_incident_investigation_work_failed_error
        CHECK (
            status <> 'FAILED'
            OR (last_error_code IS NOT NULL AND btrim(last_error_code) <> '')
        ),
    CONSTRAINT ck_incident_investigation_work_requeue_count
        CHECK (operator_requeue_count >= 0),
    CONSTRAINT ck_incident_investigation_work_requeue_audit
        CHECK (
            (operator_requeue_count = 0
                AND last_requeued_at IS NULL
                AND last_requeued_by IS NULL)
            OR
            (operator_requeue_count > 0
                AND last_requeued_at IS NOT NULL
                AND last_requeued_by IS NOT NULL
                AND btrim(last_requeued_by) <> '')
        )
);

CREATE INDEX ix_incident_investigation_work_available
    ON public.incident_investigation_work (available_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX ix_incident_investigation_work_expired_lease
    ON public.incident_investigation_work (lease_expires_at)
    WHERE status = 'IN_PROGRESS';
