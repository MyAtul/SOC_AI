-- PROPOSAL ONLY. Do not run until the database owner has reviewed the live
-- catalog, backup, preflight output, and this plan.
--
-- Target scope: restore V2's correlation-key unique constraint, six missing
-- work-table defaults, work-table foreign keys/CHECK constraints, and partial
-- indexes. This script does not alter application rows, drop/recreate tables,
-- add/drop columns, modify V1/V2, or touch Flyway history. Its only column
-- definition changes are the six explicitly listed missing defaults. It
-- assumes the tables and V2 columns already exist.
-- This narrow repair does NOT establish full V2 schema parity for column
-- types/nullability generally, or any other V2 differences; verify those
-- separately before considering the database aligned with V2. The six work
-- defaults and incident correlation-key unique constraint are the only such
-- additions explicitly handled below.
--
-- Before any future execution:
--   * Invocation: psql -X -v ON_ERROR_STOP=1 -f <script>. Do NOT use
--     psql --single-transaction/-1, or any wrapper that puts this whole file
--     in one transaction: CREATE INDEX CONCURRENTLY must run outside a block.
--   * Use a maintenance window and a verified recoverable backup.
--   * Connect explicitly to localhost:5432/aisoc and verify database/user.
--   * The catalog guard below fails closed if any FK/CHECK or non-PK index is
--     present on either work table, regardless of its name. Review its printed
--     definitions and prepare a separately reviewed selective repair; nothing
--     is automatically dropped, renamed, or recreated.
--   * Stop application writers while running the preflight and constraint
--     transaction. New writes are checked after NOT VALID constraints are
--     added, but the preflight itself is only a point-in-time observation.
--   * Run with psql ON_ERROR_STOP enabled. Do not run from the application or
--     as a Flyway migration.
--   * Flyway history adoption is a separate owner decision after schema
--     comparison. This proposal never creates or edits history records.

-- Snapshot row counts for a manual before/after comparison. These are SELECTs.
SELECT 'alerts' AS table_name, count(*) AS row_count FROM public.alerts
UNION ALL SELECT 'incidents', count(*) FROM public.incidents
UNION ALL SELECT 'alert_detection_work', count(*) FROM public.alert_detection_work
UNION ALL SELECT 'incident_investigation_work', count(*) FROM public.incident_investigation_work
ORDER BY table_name;

-- Catalog preflight: print every constraint on the work tables (including
-- definitions) before the fail-closed guard below.
SELECT c.conrelid::regclass AS table_name,
       c.conname,
       c.contype,
       c.convalidated,
       pg_get_constraintdef(c.oid, true) AS definition
FROM pg_catalog.pg_constraint c
WHERE c.conrelid IN ('public.alert_detection_work'::regclass,
                     'public.incident_investigation_work'::regclass)
ORDER BY table_name, c.conname;

-- Catalog preflight: print every index on both tables, regardless of name.
SELECT schemaname, tablename, indexname, indexdef
FROM pg_catalog.pg_indexes
WHERE schemaname = 'public'
  AND tablename IN ('alert_detection_work', 'incident_investigation_work')
ORDER BY tablename, indexname;

-- Catalog preflight for the V2 correlation-key uniqueness. Print constraints
-- that either use the key or occupy the expected name, plus every unique index
-- whose definition mentions correlation_key. Review equivalent definitions
-- under alternate names; the guard below will stop rather than duplicate one.
SELECT c.conname, c.contype, c.convalidated,
       pg_get_constraintdef(c.oid, true) AS definition
FROM pg_catalog.pg_constraint c
WHERE c.conrelid = 'public.incidents'::regclass
  AND (c.conname = 'uq_incidents_correlation_key'
       OR (c.contype = 'u'
           AND (SELECT a.attnum
                FROM pg_catalog.pg_attribute a
                WHERE a.attrelid = c.conrelid
                  AND a.attname = 'correlation_key'
                  AND NOT a.attisdropped) = ANY (c.conkey)))
ORDER BY c.conname;

SELECT ix.relname AS index_name, pg_get_indexdef(i.indexrelid) AS definition,
       i.indisunique, i.indisvalid, i.indisready,
       pg_get_expr(i.indpred, i.indrelid, true) AS predicate
FROM pg_catalog.pg_index i
JOIN pg_catalog.pg_class ix ON ix.oid = i.indexrelid
WHERE (i.indrelid = 'public.incidents'::regclass AND i.indisunique
       AND pg_get_indexdef(i.indexrelid) ILIKE '%correlation_key%')
   OR (ix.relnamespace = 'public'::regnamespace
       AND ix.relname = 'uq_incidents_correlation_key')
ORDER BY ix.relname;

-- The next section verifies each expected default against V2 and reports the
-- current catalog default before any ALTER statement is considered.
WITH expected_defaults(table_name, column_name, expected_type,
                       expected_length, expected_catalog_default) AS (
    VALUES
      ('alert_detection_work', 'status', 'character varying', 16,
       '''PENDING''::character varying'),
      ('alert_detection_work', 'attempt_count', 'smallint', NULL::integer, '0'),
      ('alert_detection_work', 'available_at', 'timestamp with time zone', NULL::integer, 'CURRENT_TIMESTAMP'),
      ('alert_detection_work', 'created_at', 'timestamp with time zone', NULL::integer, 'CURRENT_TIMESTAMP'),
      ('alert_detection_work', 'updated_at', 'timestamp with time zone', NULL::integer, 'CURRENT_TIMESTAMP'),
      ('alert_detection_work', 'operator_requeue_count', 'integer', NULL::integer, '0'),
      ('incident_investigation_work', 'status', 'character varying', 16,
       '''PENDING''::character varying'),
      ('incident_investigation_work', 'attempt_count', 'smallint', NULL::integer, '0'),
      ('incident_investigation_work', 'available_at', 'timestamp with time zone', NULL::integer, 'CURRENT_TIMESTAMP'),
      ('incident_investigation_work', 'created_at', 'timestamp with time zone', NULL::integer, 'CURRENT_TIMESTAMP'),
      ('incident_investigation_work', 'updated_at', 'timestamp with time zone', NULL::integer, 'CURRENT_TIMESTAMP'),
      ('incident_investigation_work', 'operator_requeue_count', 'integer', NULL::integer, '0')
)
SELECT e.table_name, e.column_name, e.expected_type, e.expected_length,
       c.data_type AS actual_type,
       c.character_maximum_length AS actual_length,
       c.is_nullable AS actual_is_nullable,
       c.column_default AS actual_default,
       e.expected_catalog_default
FROM expected_defaults e
LEFT JOIN information_schema.columns c
  ON c.table_schema = 'public'
 AND c.table_name = e.table_name
 AND c.column_name = e.column_name
ORDER BY e.table_name, e.column_name;

-- Row-level preflight. Only primary IDs and state metadata are returned; error
-- details are deliberately omitted. Any returned row is a stop condition.
BEGIN;

-- Full V2 work-table column preflight. All 30 expected columns are checked
-- before persistent DDL. Defaults use exact comparisons against PostgreSQL's
-- expected deparsed expressions, selected from the V2 column type. For the
-- six defaults this proposal repairs, NULL is permitted at preflight; any
-- non-NULL value must match V2 exactly. No whitespace or case normalization
-- is applied, including inside literals. NULL expected_default means V2
-- declares no default and actual_default must also be NULL.
DO $$
DECLARE column_mismatches text;
BEGIN
    WITH expected(table_name, column_name, data_type, udt_name, char_length,
                  is_nullable, expected_default) AS (
        VALUES
          ('alert_detection_work','alert_pk','bigint','int8',NULL::integer,'NO',NULL::text),
          ('alert_detection_work','status','character varying','varchar',16,'NO','''PENDING''::character varying'),
          ('alert_detection_work','attempt_count','smallint','int2',NULL::integer,'NO','0'),
          ('alert_detection_work','available_at','timestamp with time zone','timestamptz',NULL::integer,'NO','CURRENT_TIMESTAMP'),
          ('alert_detection_work','lease_expires_at','timestamp with time zone','timestamptz',NULL::integer,'YES',NULL::text),
          ('alert_detection_work','claim_token','uuid','uuid',NULL::integer,'YES',NULL::text),
          ('alert_detection_work','last_error_code','character varying','varchar',64,'YES',NULL::text),
          ('alert_detection_work','last_error_detail','character varying','varchar',1000,'YES',NULL::text),
          ('alert_detection_work','result_code','character varying','varchar',64,'YES',NULL::text),
          ('alert_detection_work','created_at','timestamp with time zone','timestamptz',NULL::integer,'NO','CURRENT_TIMESTAMP'),
          ('alert_detection_work','updated_at','timestamp with time zone','timestamptz',NULL::integer,'NO','CURRENT_TIMESTAMP'),
          ('alert_detection_work','finished_at','timestamp with time zone','timestamptz',NULL::integer,'YES',NULL::text),
          ('alert_detection_work','operator_requeue_count','integer','int4',NULL::integer,'NO','0'),
          ('alert_detection_work','last_requeued_at','timestamp with time zone','timestamptz',NULL::integer,'YES',NULL::text),
          ('alert_detection_work','last_requeued_by','character varying','varchar',128,'YES',NULL::text),
          ('incident_investigation_work','incident_pk','bigint','int8',NULL::integer,'NO',NULL::text),
          ('incident_investigation_work','status','character varying','varchar',16,'NO','''PENDING''::character varying'),
          ('incident_investigation_work','attempt_count','smallint','int2',NULL::integer,'NO','0'),
          ('incident_investigation_work','available_at','timestamp with time zone','timestamptz',NULL::integer,'NO','CURRENT_TIMESTAMP'),
          ('incident_investigation_work','lease_expires_at','timestamp with time zone','timestamptz',NULL::integer,'YES',NULL::text),
          ('incident_investigation_work','claim_token','uuid','uuid',NULL::integer,'YES',NULL::text),
          ('incident_investigation_work','last_error_code','character varying','varchar',64,'YES',NULL::text),
          ('incident_investigation_work','last_error_detail','character varying','varchar',1000,'YES',NULL::text),
          ('incident_investigation_work','result_code','character varying','varchar',64,'YES',NULL::text),
          ('incident_investigation_work','created_at','timestamp with time zone','timestamptz',NULL::integer,'NO','CURRENT_TIMESTAMP'),
          ('incident_investigation_work','updated_at','timestamp with time zone','timestamptz',NULL::integer,'NO','CURRENT_TIMESTAMP'),
          ('incident_investigation_work','finished_at','timestamp with time zone','timestamptz',NULL::integer,'YES',NULL::text),
          ('incident_investigation_work','operator_requeue_count','integer','int4',NULL::integer,'NO','0'),
          ('incident_investigation_work','last_requeued_at','timestamp with time zone','timestamptz',NULL::integer,'YES',NULL::text),
          ('incident_investigation_work','last_requeued_by','character varying','varchar',128,'YES',NULL::text)
    ), compared AS (
        SELECT e.*, c.column_name AS actual_column, c.data_type AS actual_type,
               c.udt_name AS actual_udt, c.character_maximum_length AS actual_length,
               c.is_nullable AS actual_nullable, c.column_default AS actual_default
        FROM expected e
        LEFT JOIN information_schema.columns c
          ON c.table_schema = 'public' AND c.table_name = e.table_name
         AND c.column_name = e.column_name
    )
    SELECT string_agg(format(
        '%s.%s expected type=%s/%s length=%s nullable=%s default=%s; actual type=%s/%s length=%s nullable=%s default=%s',
        table_name, column_name, data_type, udt_name,
        COALESCE(char_length::text,'<n/a>'), is_nullable,
        COALESCE(expected_default,'<NULL>'),
        COALESCE(actual_type,'<missing>'), COALESCE(actual_udt,'<missing>'),
        COALESCE(actual_length::text,'<n/a>'), COALESCE(actual_nullable,'<missing>'),
        COALESCE(actual_default,'<NULL>')), E'\n' ORDER BY table_name, column_name)
      INTO column_mismatches
      FROM compared
     WHERE actual_column IS NULL
        OR actual_type IS DISTINCT FROM data_type
        OR actual_udt IS DISTINCT FROM udt_name
        OR actual_length IS DISTINCT FROM char_length
        OR actual_nullable IS DISTINCT FROM is_nullable
        OR (expected_default IS NULL AND actual_default IS NOT NULL)
        OR (expected_default IS NOT NULL AND
            CASE data_type
              WHEN 'character varying' THEN
                actual_default IS NOT NULL
                AND actual_default IS DISTINCT FROM expected_default
              WHEN 'smallint' THEN
                actual_default IS NOT NULL
                AND actual_default IS DISTINCT FROM expected_default
              WHEN 'integer' THEN
                actual_default IS NOT NULL
                AND actual_default IS DISTINCT FROM expected_default
              WHEN 'timestamp with time zone' THEN
                actual_default IS NOT NULL
                AND actual_default IS DISTINCT FROM expected_default
              ELSE TRUE
            END);

    IF column_mismatches IS NOT NULL THEN
        RAISE EXCEPTION E'V2 repair stopped: work-table column preflight found missing or mismatched definitions; no persistent DDL may proceed:\n%',
            column_mismatches;
    END IF;
END
$$;

CREATE TEMP TABLE aisoc_v2_repair_violations ON COMMIT PRESERVE ROWS AS
    -- Required non-NULL columns in alert_detection_work.
    SELECT 'alert_detection_work required columns'::text AS check_name,
           'alert_detection_work'::text AS table_name,
           COALESCE(alert_pk::text, '<NULL>') AS row_key,
           'NULL in a V2 NOT NULL column'::text AS detail
      FROM public.alert_detection_work
     WHERE alert_pk IS NULL OR status IS NULL OR attempt_count IS NULL
        OR available_at IS NULL OR created_at IS NULL OR updated_at IS NULL
        OR operator_requeue_count IS NULL
    UNION ALL
    -- Required non-NULL columns in incident_investigation_work.
    SELECT 'incident_investigation_work required columns',
           'incident_investigation_work', COALESCE(incident_pk::text, '<NULL>'),
           'NULL in a V2 NOT NULL column'
      FROM public.incident_investigation_work
     WHERE incident_pk IS NULL OR status IS NULL OR attempt_count IS NULL
        OR available_at IS NULL OR created_at IS NULL OR updated_at IS NULL
        OR operator_requeue_count IS NULL
    UNION ALL
    -- Foreign-key orphan checks.
    SELECT 'fk_alert_detection_work_alert', 'alert_detection_work', w.alert_pk::text,
           'No matching alerts.id'
      FROM public.alert_detection_work w
      LEFT JOIN public.alerts a ON a.id = w.alert_pk
     WHERE a.id IS NULL
    UNION ALL
    SELECT 'fk_incident_investigation_work_incident', 'incident_investigation_work', w.incident_pk::text,
           'No matching incidents.id'
      FROM public.incident_investigation_work w
      LEFT JOIN public.incidents i ON i.id = w.incident_pk
     WHERE i.id IS NULL
    UNION ALL
    -- ck_alert_detection_work_status.
    SELECT 'ck_alert_detection_work_status', 'alert_detection_work', alert_pk::text,
           'Unsupported or NULL status'
      FROM public.alert_detection_work
     WHERE status IS NULL OR status NOT IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')
    UNION ALL
    -- ck_alert_detection_work_attempts.
    SELECT 'ck_alert_detection_work_attempts', 'alert_detection_work', alert_pk::text,
           'attempt_count outside 0..5 or NULL'
      FROM public.alert_detection_work
     WHERE attempt_count IS NULL OR attempt_count NOT BETWEEN 0 AND 5
    UNION ALL
    -- ck_alert_detection_work_pending_attempts.
    SELECT 'ck_alert_detection_work_pending_attempts', 'alert_detection_work', alert_pk::text,
           'PENDING must have attempt_count < 5'
      FROM public.alert_detection_work
     WHERE status = 'PENDING' AND attempt_count >= 5
    UNION ALL
    -- ck_alert_detection_work_in_progress_attempts.
    SELECT 'ck_alert_detection_work_in_progress_attempts', 'alert_detection_work', alert_pk::text,
           'IN_PROGRESS must have attempt_count 1..5'
      FROM public.alert_detection_work
     WHERE status = 'IN_PROGRESS' AND (attempt_count IS NULL OR attempt_count NOT BETWEEN 1 AND 5)
    UNION ALL
    -- ck_alert_detection_work_lease.
    SELECT 'ck_alert_detection_work_lease', 'alert_detection_work', alert_pk::text,
           'Claim token and lease must both exist only while IN_PROGRESS'
      FROM public.alert_detection_work
     WHERE (status = 'IN_PROGRESS' AND (lease_expires_at IS NULL OR claim_token IS NULL))
        OR (status <> 'IN_PROGRESS' AND (lease_expires_at IS NOT NULL OR claim_token IS NOT NULL))
    UNION ALL
    -- ck_alert_detection_work_finished.
    SELECT 'ck_alert_detection_work_finished', 'alert_detection_work', alert_pk::text,
           'finished_at disagrees with terminal/nonterminal status'
      FROM public.alert_detection_work
     WHERE (status IN ('COMPLETED', 'FAILED') AND finished_at IS NULL)
        OR (status IN ('PENDING', 'IN_PROGRESS') AND finished_at IS NOT NULL)
    UNION ALL
    -- ck_alert_detection_work_result.
    SELECT 'ck_alert_detection_work_result', 'alert_detection_work', alert_pk::text,
           'Invalid result_code for status'
      FROM public.alert_detection_work
     WHERE (status = 'COMPLETED' AND (result_code IS NULL OR result_code NOT IN (
                'INCIDENT_CREATED', 'INCIDENT_REUSED_ACTIVE', 'INCIDENT_REUSED_CLOSED',
                'BELOW_THRESHOLD', 'SKIPPED_LEGACY_TIMESTAMP', 'SKIPPED_MISSING_TIMESTAMP',
                'SKIPPED_UNIDENTIFIED_HOST', 'NOT_APPLICABLE')))
        OR (status <> 'COMPLETED' AND result_code IS NOT NULL)
    UNION ALL
    -- ck_alert_detection_work_failed_error.
    SELECT 'ck_alert_detection_work_failed_error', 'alert_detection_work', alert_pk::text,
           'FAILED requires nonblank last_error_code'
      FROM public.alert_detection_work
     WHERE status = 'FAILED' AND (last_error_code IS NULL OR btrim(last_error_code) = '')
    UNION ALL
    -- ck_alert_detection_work_requeue_count.
    SELECT 'ck_alert_detection_work_requeue_count', 'alert_detection_work', alert_pk::text,
           'operator_requeue_count must be nonnegative and non-NULL'
      FROM public.alert_detection_work
     WHERE operator_requeue_count IS NULL OR operator_requeue_count < 0
    UNION ALL
    -- ck_alert_detection_work_requeue_audit.
    SELECT 'ck_alert_detection_work_requeue_audit', 'alert_detection_work', alert_pk::text,
           'Requeue count and audit fields disagree'
      FROM public.alert_detection_work
     WHERE (operator_requeue_count = 0 AND
               (last_requeued_at IS NOT NULL OR last_requeued_by IS NOT NULL))
        OR (operator_requeue_count > 0 AND
               (last_requeued_at IS NULL OR last_requeued_by IS NULL OR btrim(last_requeued_by) = ''))
    UNION ALL
    -- ck_incident_investigation_work_status.
    SELECT 'ck_incident_investigation_work_status', 'incident_investigation_work', incident_pk::text,
           'Unsupported or NULL status'
      FROM public.incident_investigation_work
     WHERE status IS NULL OR status NOT IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')
    UNION ALL
    -- ck_incident_investigation_work_attempts.
    SELECT 'ck_incident_investigation_work_attempts', 'incident_investigation_work', incident_pk::text,
           'attempt_count outside 0..5 or NULL'
      FROM public.incident_investigation_work
     WHERE attempt_count IS NULL OR attempt_count NOT BETWEEN 0 AND 5
    UNION ALL
    -- ck_incident_investigation_work_pending_attempts.
    SELECT 'ck_incident_investigation_work_pending_attempts', 'incident_investigation_work', incident_pk::text,
           'PENDING must have attempt_count < 5'
      FROM public.incident_investigation_work
     WHERE status = 'PENDING' AND attempt_count >= 5
    UNION ALL
    -- ck_incident_investigation_work_in_progress_attempts.
    SELECT 'ck_incident_investigation_work_in_progress_attempts', 'incident_investigation_work', incident_pk::text,
           'IN_PROGRESS must have attempt_count 1..5'
      FROM public.incident_investigation_work
     WHERE status = 'IN_PROGRESS' AND (attempt_count IS NULL OR attempt_count NOT BETWEEN 1 AND 5)
    UNION ALL
    -- ck_incident_investigation_work_lease.
    SELECT 'ck_incident_investigation_work_lease', 'incident_investigation_work', incident_pk::text,
           'Claim token and lease must both exist only while IN_PROGRESS'
      FROM public.incident_investigation_work
     WHERE (status = 'IN_PROGRESS' AND (lease_expires_at IS NULL OR claim_token IS NULL))
        OR (status <> 'IN_PROGRESS' AND (lease_expires_at IS NOT NULL OR claim_token IS NOT NULL))
    UNION ALL
    -- ck_incident_investigation_work_finished.
    SELECT 'ck_incident_investigation_work_finished', 'incident_investigation_work', incident_pk::text,
           'finished_at disagrees with terminal/nonterminal status'
      FROM public.incident_investigation_work
     WHERE (status IN ('COMPLETED', 'FAILED') AND finished_at IS NULL)
        OR (status IN ('PENDING', 'IN_PROGRESS') AND finished_at IS NOT NULL)
    UNION ALL
    -- ck_incident_investigation_work_result.
    SELECT 'ck_incident_investigation_work_result', 'incident_investigation_work', incident_pk::text,
           'Invalid result_code for status'
      FROM public.incident_investigation_work
     WHERE (status = 'COMPLETED' AND (result_code IS NULL OR result_code NOT IN (
                'INVESTIGATION_PERSISTED', 'ALREADY_PERSISTED', 'NO_INVESTIGATION_REQUIRED')))
        OR (status <> 'COMPLETED' AND result_code IS NOT NULL)
    UNION ALL
    -- ck_incident_investigation_work_failed_error.
    SELECT 'ck_incident_investigation_work_failed_error', 'incident_investigation_work', incident_pk::text,
           'FAILED requires nonblank last_error_code'
      FROM public.incident_investigation_work
     WHERE status = 'FAILED' AND (last_error_code IS NULL OR btrim(last_error_code) = '')
    UNION ALL
    -- ck_incident_investigation_work_requeue_count.
    SELECT 'ck_incident_investigation_work_requeue_count', 'incident_investigation_work', incident_pk::text,
           'operator_requeue_count must be nonnegative and non-NULL'
      FROM public.incident_investigation_work
     WHERE operator_requeue_count IS NULL OR operator_requeue_count < 0
    UNION ALL
    -- ck_incident_investigation_work_requeue_audit.
    SELECT 'ck_incident_investigation_work_requeue_audit', 'incident_investigation_work', incident_pk::text,
           'Requeue count and audit fields disagree'
      FROM public.incident_investigation_work
     WHERE (operator_requeue_count = 0 AND
               (last_requeued_at IS NOT NULL OR last_requeued_by IS NOT NULL))
        OR (operator_requeue_count > 0 AND
               (last_requeued_at IS NULL OR last_requeued_by IS NULL OR btrim(last_requeued_by) = ''));

SELECT check_name, table_name, row_key, detail
FROM pg_temp.aisoc_v2_repair_violations
ORDER BY table_name, check_name, row_key;

DO $$
DECLARE violation_count bigint;
BEGIN
    SELECT count(*) INTO violation_count FROM pg_temp.aisoc_v2_repair_violations;
    IF violation_count <> 0 THEN
        RAISE EXCEPTION 'V2 repair stopped: % invalid/orphan rows found; preserve and review them before retrying',
            violation_count;
    END IF;
END
$$;

-- The primary keys are not repaired here. V2 names them, but the names are not
-- required for key semantics. Accept an existing validated PK on exactly the
-- expected key column even if PostgreSQL assigned a different constraint
-- name; stop if the key definition is absent or differs.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_constraint
         WHERE conrelid = 'public.alert_detection_work'::regclass
           AND contype = 'p' AND convalidated
           AND conkey = ARRAY[(SELECT attnum FROM pg_catalog.pg_attribute
                               WHERE attrelid = 'public.alert_detection_work'::regclass
                                 AND attname = 'alert_pk')]::smallint[]
    ) THEN
        RAISE EXCEPTION 'Expected primary key on alert_detection_work(alert_pk) is missing or differs; stop for manual review';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_constraint
         WHERE conrelid = 'public.incident_investigation_work'::regclass
           AND contype = 'p' AND convalidated
           AND conkey = ARRAY[(SELECT attnum FROM pg_catalog.pg_attribute
                               WHERE attrelid = 'public.incident_investigation_work'::regclass
                                 AND attname = 'incident_pk')]::smallint[]
    ) THEN
        RAISE EXCEPTION 'Expected primary key on incident_investigation_work(incident_pk) is missing or differs; stop for manual review';
    END IF;
END
$$;

-- Fail closed on any FK/CHECK constraint or non-primary index already
-- attached to either work table, whatever its name or definition. The SELECTs
-- above have printed the definitions for owner review. Do not automatically
-- drop, rename, or recreate an object; prepare a selective reviewed repair.
DO $$
DECLARE existing_objects text;
BEGIN
    SELECT string_agg(details.object_description, E'\n' ORDER BY details.object_description)
      INTO existing_objects
      FROM (
          SELECT format('constraint %I on %s: %s', c.conname,
                        c.conrelid::regclass,
                        pg_get_constraintdef(c.oid, true)) AS object_description
            FROM pg_catalog.pg_constraint c
           WHERE c.conrelid IN ('public.alert_detection_work'::regclass,
                                'public.incident_investigation_work'::regclass)
             AND c.contype IN ('f', 'c')
          UNION ALL
          SELECT format('index %I on %s: %s', idx.relname,
                        i.indrelid::regclass, pg_get_indexdef(i.indexrelid))
            FROM pg_catalog.pg_index i
            JOIN pg_catalog.pg_class idx ON idx.oid = i.indexrelid
           WHERE i.indrelid IN ('public.alert_detection_work'::regclass,
                                'public.incident_investigation_work'::regclass)
             AND NOT i.indisprimary
      ) AS details;
    IF existing_objects IS NOT NULL THEN
        RAISE EXCEPTION E'V2 repair stopped: work-table FK/CHECK constraint(s) or non-primary index(es) already exist. Review catalog output and use a selective owner-reviewed repair. Objects:\n%',
            existing_objects;
    END IF;
END
$$;

-- V2 incidents unique-key preflight and equivalence guard. The duplicate
-- check excludes NULLs, as PostgreSQL UNIQUE permits multiple NULL keys.
-- Any unique constraint/index involving correlation_key (even under another
-- name or with a predicate), or any same-name catalog object, requires owner
-- review; never duplicate or replace it automatically.
DO $$
DECLARE
    duplicate_groups bigint;
    existing_objects text;
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'incidents'
          AND column_name = 'correlation_key'
          AND data_type = 'character varying'
          AND character_maximum_length = 64
          AND is_nullable = 'YES'
    ) THEN
        RAISE EXCEPTION 'V2 repair stopped: incidents.correlation_key is missing or differs from varchar(64) NULL; review before proceeding';
    END IF;

    SELECT count(*) INTO duplicate_groups
    FROM (
        SELECT correlation_key
        FROM public.incidents
        WHERE correlation_key IS NOT NULL
        GROUP BY correlation_key
        HAVING count(*) > 1
    ) AS duplicate_keys;
    IF duplicate_groups <> 0 THEN
        RAISE EXCEPTION 'V2 repair stopped: % duplicate non-NULL incidents.correlation_key groups; preserve rows and review before proceeding',
            duplicate_groups;
    END IF;

    SELECT string_agg(details.object_description, E'\n' ORDER BY details.object_description)
      INTO existing_objects
      FROM (
          SELECT format('constraint %I on %s: %s', c.conname,
                        c.conrelid::regclass, pg_get_constraintdef(c.oid, true))
                 AS object_description
          FROM pg_catalog.pg_constraint c
          WHERE c.conrelid = 'public.incidents'::regclass
            AND (c.conname = 'uq_incidents_correlation_key'
                 OR (c.contype = 'u'
                     AND (SELECT a.attnum
                          FROM pg_catalog.pg_attribute a
                          WHERE a.attrelid = c.conrelid
                            AND a.attname = 'correlation_key'
                            AND NOT a.attisdropped) = ANY (c.conkey)))
          UNION ALL
          SELECT format('unique index %I: %s', ix.relname,
                        pg_get_indexdef(i.indexrelid))
          FROM pg_catalog.pg_index i
          JOIN pg_catalog.pg_class ix ON ix.oid = i.indexrelid
          WHERE i.indrelid = 'public.incidents'::regclass
            AND i.indisunique
            AND pg_get_indexdef(i.indexrelid) ILIKE '%correlation_key%'
          UNION ALL
          SELECT format('public relation name collision %I (%s)', r.relname,
                        r.relkind)
          FROM pg_catalog.pg_class r
          WHERE r.relnamespace = 'public'::regnamespace
            AND r.relname = 'uq_incidents_correlation_key'
      ) AS details;
    IF existing_objects IS NOT NULL THEN
        RAISE EXCEPTION E'V2 repair stopped: possible equivalent or conflicting correlation-key object(s) exist. Review definitions and prepare a selective plan; objects:\n%',
            existing_objects;
    END IF;
END
$$;

-- Separately labeled default preflight. Match V2's types/nullability and
-- require the six confirmed-missing defaults to remain absent. Any existing
-- default, missing column, or schema mismatch stops the transaction for owner
-- review before any ALTER statement runs.
DO $$
DECLARE unexpected_columns text;
BEGIN
    WITH expected_defaults(table_name, column_name, expected_type,
                           expected_length, expected_catalog_default) AS (
        VALUES
          ('alert_detection_work', 'status', 'character varying', 16,
           '''PENDING''::character varying'),
          ('alert_detection_work', 'attempt_count', 'smallint', NULL::integer, '0'),
          ('alert_detection_work', 'available_at', 'timestamp with time zone', NULL::integer, 'CURRENT_TIMESTAMP'),
          ('alert_detection_work', 'created_at', 'timestamp with time zone', NULL::integer, 'CURRENT_TIMESTAMP'),
          ('alert_detection_work', 'updated_at', 'timestamp with time zone', NULL::integer, 'CURRENT_TIMESTAMP'),
          ('alert_detection_work', 'operator_requeue_count', 'integer', NULL::integer, '0'),
          ('incident_investigation_work', 'status', 'character varying', 16,
           '''PENDING''::character varying'),
          ('incident_investigation_work', 'attempt_count', 'smallint', NULL::integer, '0'),
          ('incident_investigation_work', 'available_at', 'timestamp with time zone', NULL::integer, 'CURRENT_TIMESTAMP'),
          ('incident_investigation_work', 'created_at', 'timestamp with time zone', NULL::integer, 'CURRENT_TIMESTAMP'),
          ('incident_investigation_work', 'updated_at', 'timestamp with time zone', NULL::integer, 'CURRENT_TIMESTAMP'),
          ('incident_investigation_work', 'operator_requeue_count', 'integer', NULL::integer, '0')
    )
    SELECT string_agg(
               format('%s.%s expected type=%s length=%s nullable=NO default absent; actual type=%s length=%s nullable=%s default=%s; V2 default=%s',
                      e.table_name, e.column_name, e.expected_type,
                      COALESCE(e.expected_length::text, '<n/a>'),
                      COALESCE(c.data_type, '<missing>'),
                      COALESCE(c.character_maximum_length::text, '<n/a>'),
                      COALESCE(c.is_nullable, '<missing>'),
                      COALESCE(c.column_default, '<absent>'),
                      e.expected_catalog_default), E'\n'
               ORDER BY e.table_name, e.column_name)
      INTO unexpected_columns
      FROM expected_defaults e
      LEFT JOIN information_schema.columns c
        ON c.table_schema = 'public'
       AND c.table_name = e.table_name
       AND c.column_name = e.column_name
     WHERE c.column_name IS NULL
        OR c.data_type IS DISTINCT FROM e.expected_type
        OR (e.expected_length IS NOT NULL
            AND c.character_maximum_length IS DISTINCT FROM e.expected_length)
        OR c.is_nullable IS DISTINCT FROM 'NO'
        OR c.column_default IS NOT NULL;

    IF unexpected_columns IS NOT NULL THEN
        RAISE EXCEPTION E'V2 repair stopped: default preflight found unexpected work-table column state; no schema changes made:\n%',
            unexpected_columns;
    END IF;
END
$$;

-- Constraint/default transaction. All data and catalog guards above run
-- before DDL. NOT VALID avoids an initial table scan during CHECK/FK ADD;
-- VALIDATE below checks preserved rows. SET DEFAULT changes no existing row.
SET LOCAL lock_timeout = '5s';

ALTER TABLE public.alert_detection_work
    ADD CONSTRAINT fk_alert_detection_work_alert
    FOREIGN KEY (alert_pk) REFERENCES public.alerts (id) NOT VALID;
ALTER TABLE public.alert_detection_work
    ADD CONSTRAINT ck_alert_detection_work_status
    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')) NOT VALID;
ALTER TABLE public.alert_detection_work
    ADD CONSTRAINT ck_alert_detection_work_attempts
    CHECK (attempt_count BETWEEN 0 AND 5) NOT VALID;
ALTER TABLE public.alert_detection_work
    ADD CONSTRAINT ck_alert_detection_work_pending_attempts
    CHECK (status <> 'PENDING' OR attempt_count < 5) NOT VALID;
ALTER TABLE public.alert_detection_work
    ADD CONSTRAINT ck_alert_detection_work_in_progress_attempts
    CHECK (status <> 'IN_PROGRESS' OR attempt_count BETWEEN 1 AND 5) NOT VALID;
ALTER TABLE public.alert_detection_work
    ADD CONSTRAINT ck_alert_detection_work_lease
    CHECK (
        (status = 'IN_PROGRESS' AND lease_expires_at IS NOT NULL AND claim_token IS NOT NULL)
        OR (status <> 'IN_PROGRESS' AND lease_expires_at IS NULL AND claim_token IS NULL)
    ) NOT VALID;
ALTER TABLE public.alert_detection_work
    ADD CONSTRAINT ck_alert_detection_work_finished
    CHECK (
        (status IN ('COMPLETED', 'FAILED') AND finished_at IS NOT NULL)
        OR (status IN ('PENDING', 'IN_PROGRESS') AND finished_at IS NULL)
    ) NOT VALID;
ALTER TABLE public.alert_detection_work
    ADD CONSTRAINT ck_alert_detection_work_result
    CHECK (
        (status = 'COMPLETED' AND result_code IS NOT NULL AND result_code IN (
            'INCIDENT_CREATED', 'INCIDENT_REUSED_ACTIVE', 'INCIDENT_REUSED_CLOSED',
            'BELOW_THRESHOLD', 'SKIPPED_LEGACY_TIMESTAMP', 'SKIPPED_MISSING_TIMESTAMP',
            'SKIPPED_UNIDENTIFIED_HOST', 'NOT_APPLICABLE'))
        OR (status <> 'COMPLETED' AND result_code IS NULL)
    ) NOT VALID;
ALTER TABLE public.alert_detection_work
    ADD CONSTRAINT ck_alert_detection_work_failed_error
    CHECK (status <> 'FAILED' OR (last_error_code IS NOT NULL AND btrim(last_error_code) <> '')) NOT VALID;
ALTER TABLE public.alert_detection_work
    ADD CONSTRAINT ck_alert_detection_work_requeue_count
    CHECK (operator_requeue_count >= 0) NOT VALID;
ALTER TABLE public.alert_detection_work
    ADD CONSTRAINT ck_alert_detection_work_requeue_audit
    CHECK (
        (operator_requeue_count = 0 AND last_requeued_at IS NULL AND last_requeued_by IS NULL)
        OR (operator_requeue_count > 0 AND last_requeued_at IS NOT NULL
            AND last_requeued_by IS NOT NULL AND btrim(last_requeued_by) <> '')
    ) NOT VALID;

ALTER TABLE public.incident_investigation_work
    ADD CONSTRAINT fk_incident_investigation_work_incident
    FOREIGN KEY (incident_pk) REFERENCES public.incidents (id) NOT VALID;
ALTER TABLE public.incident_investigation_work
    ADD CONSTRAINT ck_incident_investigation_work_status
    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')) NOT VALID;
ALTER TABLE public.incident_investigation_work
    ADD CONSTRAINT ck_incident_investigation_work_attempts
    CHECK (attempt_count BETWEEN 0 AND 5) NOT VALID;
ALTER TABLE public.incident_investigation_work
    ADD CONSTRAINT ck_incident_investigation_work_pending_attempts
    CHECK (status <> 'PENDING' OR attempt_count < 5) NOT VALID;
ALTER TABLE public.incident_investigation_work
    ADD CONSTRAINT ck_incident_investigation_work_in_progress_attempts
    CHECK (status <> 'IN_PROGRESS' OR attempt_count BETWEEN 1 AND 5) NOT VALID;
ALTER TABLE public.incident_investigation_work
    ADD CONSTRAINT ck_incident_investigation_work_lease
    CHECK (
        (status = 'IN_PROGRESS' AND lease_expires_at IS NOT NULL AND claim_token IS NOT NULL)
        OR (status <> 'IN_PROGRESS' AND lease_expires_at IS NULL AND claim_token IS NULL)
    ) NOT VALID;
ALTER TABLE public.incident_investigation_work
    ADD CONSTRAINT ck_incident_investigation_work_finished
    CHECK (
        (status IN ('COMPLETED', 'FAILED') AND finished_at IS NOT NULL)
        OR (status IN ('PENDING', 'IN_PROGRESS') AND finished_at IS NULL)
    ) NOT VALID;
ALTER TABLE public.incident_investigation_work
    ADD CONSTRAINT ck_incident_investigation_work_result
    CHECK (
        (status = 'COMPLETED' AND result_code IS NOT NULL AND result_code IN (
            'INVESTIGATION_PERSISTED', 'ALREADY_PERSISTED', 'NO_INVESTIGATION_REQUIRED'))
        OR (status <> 'COMPLETED' AND result_code IS NULL)
    ) NOT VALID;
ALTER TABLE public.incident_investigation_work
    ADD CONSTRAINT ck_incident_investigation_work_failed_error
    CHECK (status <> 'FAILED' OR (last_error_code IS NOT NULL AND btrim(last_error_code) <> '')) NOT VALID;
ALTER TABLE public.incident_investigation_work
    ADD CONSTRAINT ck_incident_investigation_work_requeue_count
    CHECK (operator_requeue_count >= 0) NOT VALID;
ALTER TABLE public.incident_investigation_work
    ADD CONSTRAINT ck_incident_investigation_work_requeue_audit
    CHECK (
        (operator_requeue_count = 0 AND last_requeued_at IS NULL AND last_requeued_by IS NULL)
        OR (operator_requeue_count > 0 AND last_requeued_at IS NOT NULL
            AND last_requeued_by IS NOT NULL AND btrim(last_requeued_by) <> '')
    ) NOT VALID;

-- V2 incidents uniqueness repair. Exact V2 definition; duplicate and catalog
-- guards ran above in this same transaction. Multiple NULL legacy keys remain
-- permitted. No incident rows are changed.
ALTER TABLE public.incidents
    ADD CONSTRAINT uq_incidents_correlation_key UNIQUE (correlation_key);

-- V2 work-table defaults repair. This is a separately labeled section within
-- the enclosing transaction. The preflight verified the exact V2 column
-- types/nullability and that all six defaults are currently absent. SET
-- DEFAULT affects future inserts only; it does not update existing rows.
ALTER TABLE public.alert_detection_work
    ALTER COLUMN status SET DEFAULT 'PENDING',
    ALTER COLUMN attempt_count SET DEFAULT 0,
    ALTER COLUMN available_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN operator_requeue_count SET DEFAULT 0;

ALTER TABLE public.incident_investigation_work
    ALTER COLUMN status SET DEFAULT 'PENDING',
    ALTER COLUMN attempt_count SET DEFAULT 0,
    ALTER COLUMN available_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN operator_requeue_count SET DEFAULT 0;

ALTER TABLE public.alert_detection_work
    VALIDATE CONSTRAINT fk_alert_detection_work_alert;
ALTER TABLE public.alert_detection_work
    VALIDATE CONSTRAINT ck_alert_detection_work_status;
ALTER TABLE public.alert_detection_work
    VALIDATE CONSTRAINT ck_alert_detection_work_attempts;
ALTER TABLE public.alert_detection_work
    VALIDATE CONSTRAINT ck_alert_detection_work_pending_attempts;
ALTER TABLE public.alert_detection_work
    VALIDATE CONSTRAINT ck_alert_detection_work_in_progress_attempts;
ALTER TABLE public.alert_detection_work
    VALIDATE CONSTRAINT ck_alert_detection_work_lease;
ALTER TABLE public.alert_detection_work
    VALIDATE CONSTRAINT ck_alert_detection_work_finished;
ALTER TABLE public.alert_detection_work
    VALIDATE CONSTRAINT ck_alert_detection_work_result;
ALTER TABLE public.alert_detection_work
    VALIDATE CONSTRAINT ck_alert_detection_work_failed_error;
ALTER TABLE public.alert_detection_work
    VALIDATE CONSTRAINT ck_alert_detection_work_requeue_count;
ALTER TABLE public.alert_detection_work
    VALIDATE CONSTRAINT ck_alert_detection_work_requeue_audit;

ALTER TABLE public.incident_investigation_work
    VALIDATE CONSTRAINT fk_incident_investigation_work_incident;
ALTER TABLE public.incident_investigation_work
    VALIDATE CONSTRAINT ck_incident_investigation_work_status;
ALTER TABLE public.incident_investigation_work
    VALIDATE CONSTRAINT ck_incident_investigation_work_attempts;
ALTER TABLE public.incident_investigation_work
    VALIDATE CONSTRAINT ck_incident_investigation_work_pending_attempts;
ALTER TABLE public.incident_investigation_work
    VALIDATE CONSTRAINT ck_incident_investigation_work_in_progress_attempts;
ALTER TABLE public.incident_investigation_work
    VALIDATE CONSTRAINT ck_incident_investigation_work_lease;
ALTER TABLE public.incident_investigation_work
    VALIDATE CONSTRAINT ck_incident_investigation_work_finished;
ALTER TABLE public.incident_investigation_work
    VALIDATE CONSTRAINT ck_incident_investigation_work_result;
ALTER TABLE public.incident_investigation_work
    VALIDATE CONSTRAINT ck_incident_investigation_work_failed_error;
ALTER TABLE public.incident_investigation_work
    VALIDATE CONSTRAINT ck_incident_investigation_work_requeue_count;
ALTER TABLE public.incident_investigation_work
    VALIDATE CONSTRAINT ck_incident_investigation_work_requeue_audit;

COMMIT;

-- All four CREATE INDEX CONCURRENTLY statements below are intentionally
-- outside explicit transactions. If a build fails or is interrupted, STOP and
-- inspect pg_index (especially indisvalid and indisready) and the index
-- definition. Do not rerun the whole script and do not automatically drop or
-- rebuild an invalid index. The database owner must review a selective recovery
-- plan; an invalid concurrent-build artifact can retain its name and block a
-- later CREATE INDEX with that name.

CREATE INDEX CONCURRENTLY ix_alert_detection_work_available
    ON public.alert_detection_work (available_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX CONCURRENTLY ix_alert_detection_work_expired_lease
    ON public.alert_detection_work (lease_expires_at)
    WHERE status = 'IN_PROGRESS';

CREATE INDEX CONCURRENTLY ix_incident_investigation_work_available
    ON public.incident_investigation_work (available_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX CONCURRENTLY ix_incident_investigation_work_expired_lease
    ON public.incident_investigation_work (lease_expires_at)
    WHERE status = 'IN_PROGRESS';

-- Post-repair verification. Compare constraint definitions to V2 and confirm
-- every expected constraint is validated. FK actions should remain NO ACTION.
SELECT c.conrelid::regclass AS table_name, c.conname, c.contype, c.convalidated,
       c.confupdtype, c.confdeltype, pg_get_constraintdef(c.oid, true) AS definition
FROM pg_catalog.pg_constraint c
WHERE c.conrelid IN ('public.alert_detection_work'::regclass,
                     'public.incident_investigation_work'::regclass)
ORDER BY table_name, c.conname;

-- Expected index definitions and validity. Confirm all four are valid and the
-- predicates/keys match V2 exactly.
SELECT i.indexrelid::regclass AS index_name, am.amname AS access_method,
       ix.indisunique, ix.indisvalid, ix.indisready,
       pg_get_indexdef(ix.indexrelid) AS definition,
       pg_get_expr(ix.indpred, ix.indrelid) AS predicate
FROM pg_catalog.pg_index ix
JOIN pg_catalog.pg_class i ON i.oid = ix.indexrelid
JOIN pg_catalog.pg_am am ON am.oid = i.relam
WHERE ix.indrelid IN ('public.alert_detection_work'::regclass,
                      'public.incident_investigation_work'::regclass)
ORDER BY index_name;

-- Explicit expected-object verification. This emits one row for every V2 FK,
-- CHECK, and partial index, including missing objects. Compare the actual
-- pg_get_* definition and action/predicate fields with expected_v2_definition
-- below; a matching name alone is never considered proof of correctness.
-- PostgreSQL deparses CHECK and index expressions into canonical syntax, so
-- review both actual and expected expressions when comparing their semantics.
WITH expected_constraints(table_name, object_name, object_type, expected_v2_definition) AS (
    VALUES
      ('incidents', 'uq_incidents_correlation_key', 'UNIQUE',
       'UNIQUE (correlation_key)'),
      ('alert_detection_work', 'fk_alert_detection_work_alert', 'FOREIGN KEY',
       'FOREIGN KEY (alert_pk) REFERENCES public.alerts(id)'),
      ('alert_detection_work', 'ck_alert_detection_work_status', 'CHECK',
       'CHECK (status IN (''PENDING'', ''IN_PROGRESS'', ''COMPLETED'', ''FAILED''))'),
      ('alert_detection_work', 'ck_alert_detection_work_attempts', 'CHECK',
       'CHECK (attempt_count BETWEEN 0 AND 5)'),
      ('alert_detection_work', 'ck_alert_detection_work_pending_attempts', 'CHECK',
       'CHECK (status <> ''PENDING'' OR attempt_count < 5)'),
      ('alert_detection_work', 'ck_alert_detection_work_in_progress_attempts', 'CHECK',
       'CHECK (status <> ''IN_PROGRESS'' OR attempt_count BETWEEN 1 AND 5)'),
      ('alert_detection_work', 'ck_alert_detection_work_lease', 'CHECK',
       'CHECK ((status = ''IN_PROGRESS'' AND lease_expires_at IS NOT NULL AND claim_token IS NOT NULL) OR (status <> ''IN_PROGRESS'' AND lease_expires_at IS NULL AND claim_token IS NULL))'),
      ('alert_detection_work', 'ck_alert_detection_work_finished', 'CHECK',
       'CHECK ((status IN (''COMPLETED'', ''FAILED'') AND finished_at IS NOT NULL) OR (status IN (''PENDING'', ''IN_PROGRESS'') AND finished_at IS NULL))'),
      ('alert_detection_work', 'ck_alert_detection_work_result', 'CHECK',
       'CHECK ((status = ''COMPLETED'' AND result_code IS NOT NULL AND result_code IN (''INCIDENT_CREATED'', ''INCIDENT_REUSED_ACTIVE'', ''INCIDENT_REUSED_CLOSED'', ''BELOW_THRESHOLD'', ''SKIPPED_LEGACY_TIMESTAMP'', ''SKIPPED_MISSING_TIMESTAMP'', ''SKIPPED_UNIDENTIFIED_HOST'', ''NOT_APPLICABLE'')) OR (status <> ''COMPLETED'' AND result_code IS NULL))'),
      ('alert_detection_work', 'ck_alert_detection_work_failed_error', 'CHECK',
       'CHECK (status <> ''FAILED'' OR (last_error_code IS NOT NULL AND btrim(last_error_code) <> ''''))'),
      ('alert_detection_work', 'ck_alert_detection_work_requeue_count', 'CHECK',
       'CHECK (operator_requeue_count >= 0)'),
      ('alert_detection_work', 'ck_alert_detection_work_requeue_audit', 'CHECK',
       'CHECK ((operator_requeue_count = 0 AND last_requeued_at IS NULL AND last_requeued_by IS NULL) OR (operator_requeue_count > 0 AND last_requeued_at IS NOT NULL AND last_requeued_by IS NOT NULL AND btrim(last_requeued_by) <> ''''))'),
      ('incident_investigation_work', 'fk_incident_investigation_work_incident', 'FOREIGN KEY',
       'FOREIGN KEY (incident_pk) REFERENCES public.incidents(id)'),
      ('incident_investigation_work', 'ck_incident_investigation_work_status', 'CHECK',
       'CHECK (status IN (''PENDING'', ''IN_PROGRESS'', ''COMPLETED'', ''FAILED''))'),
      ('incident_investigation_work', 'ck_incident_investigation_work_attempts', 'CHECK',
       'CHECK (attempt_count BETWEEN 0 AND 5)'),
      ('incident_investigation_work', 'ck_incident_investigation_work_pending_attempts', 'CHECK',
       'CHECK (status <> ''PENDING'' OR attempt_count < 5)'),
      ('incident_investigation_work', 'ck_incident_investigation_work_in_progress_attempts', 'CHECK',
       'CHECK (status <> ''IN_PROGRESS'' OR attempt_count BETWEEN 1 AND 5)'),
      ('incident_investigation_work', 'ck_incident_investigation_work_lease', 'CHECK',
       'CHECK ((status = ''IN_PROGRESS'' AND lease_expires_at IS NOT NULL AND claim_token IS NOT NULL) OR (status <> ''IN_PROGRESS'' AND lease_expires_at IS NULL AND claim_token IS NULL))'),
      ('incident_investigation_work', 'ck_incident_investigation_work_finished', 'CHECK',
       'CHECK ((status IN (''COMPLETED'', ''FAILED'') AND finished_at IS NOT NULL) OR (status IN (''PENDING'', ''IN_PROGRESS'') AND finished_at IS NULL))'),
      ('incident_investigation_work', 'ck_incident_investigation_work_result', 'CHECK',
       'CHECK ((status = ''COMPLETED'' AND result_code IS NOT NULL AND result_code IN (''INVESTIGATION_PERSISTED'', ''ALREADY_PERSISTED'', ''NO_INVESTIGATION_REQUIRED'')) OR (status <> ''COMPLETED'' AND result_code IS NULL))'),
      ('incident_investigation_work', 'ck_incident_investigation_work_failed_error', 'CHECK',
       'CHECK (status <> ''FAILED'' OR (last_error_code IS NOT NULL AND btrim(last_error_code) <> ''''))'),
      ('incident_investigation_work', 'ck_incident_investigation_work_requeue_count', 'CHECK',
       'CHECK (operator_requeue_count >= 0)'),
      ('incident_investigation_work', 'ck_incident_investigation_work_requeue_audit', 'CHECK',
       'CHECK ((operator_requeue_count = 0 AND last_requeued_at IS NULL AND last_requeued_by IS NULL) OR (operator_requeue_count > 0 AND last_requeued_at IS NOT NULL AND last_requeued_by IS NOT NULL AND btrim(last_requeued_by) <> ''''))')
), actual_constraints AS (
    SELECT n.nspname || '.' || r.relname AS table_name,
           c.conname AS object_name,
           c.contype, c.convalidated, c.confupdtype, c.confdeltype,
           pg_get_constraintdef(c.oid, true) AS actual_definition
      FROM pg_catalog.pg_constraint c
      JOIN pg_catalog.pg_class r ON r.oid = c.conrelid
      JOIN pg_catalog.pg_namespace n ON n.oid = r.relnamespace
     WHERE c.conrelid IN ('public.incidents'::regclass,
                          'public.alert_detection_work'::regclass,
                          'public.incident_investigation_work'::regclass)
)
SELECT e.table_name, e.object_name, e.object_type,
       (a.object_name IS NOT NULL) AS is_present,
       a.convalidated AS is_validated,
       a.confupdtype AS fk_update_action_code,
       a.confdeltype AS fk_delete_action_code,
       a.actual_definition,
       e.expected_v2_definition
  FROM expected_constraints e
  LEFT JOIN actual_constraints a
    ON a.table_name = 'public.' || e.table_name
   AND a.object_name = e.object_name
   AND ((e.object_type = 'FOREIGN KEY' AND a.contype = 'f')
        OR (e.object_type = 'CHECK' AND a.contype = 'c')
        OR (e.object_type = 'UNIQUE' AND a.contype = 'u'))
 ORDER BY e.table_name, e.object_name;

-- Complete post-repair column parity report: one row per V2 work-table
-- column, including missing columns. Expected deparsed defaults are compared
-- exactly by type; no text normalization can change string-literal content.
WITH expected(table_name, column_name, data_type, udt_name, char_length,
              is_nullable, expected_default) AS (
    VALUES
      ('alert_detection_work','alert_pk','bigint','int8',NULL::integer,'NO',NULL::text),
      ('alert_detection_work','status','character varying','varchar',16,'NO','''PENDING''::character varying'),
      ('alert_detection_work','attempt_count','smallint','int2',NULL::integer,'NO','0'),
      ('alert_detection_work','available_at','timestamp with time zone','timestamptz',NULL::integer,'NO','CURRENT_TIMESTAMP'),
      ('alert_detection_work','lease_expires_at','timestamp with time zone','timestamptz',NULL::integer,'YES',NULL::text),
      ('alert_detection_work','claim_token','uuid','uuid',NULL::integer,'YES',NULL::text),
      ('alert_detection_work','last_error_code','character varying','varchar',64,'YES',NULL::text),
      ('alert_detection_work','last_error_detail','character varying','varchar',1000,'YES',NULL::text),
      ('alert_detection_work','result_code','character varying','varchar',64,'YES',NULL::text),
      ('alert_detection_work','created_at','timestamp with time zone','timestamptz',NULL::integer,'NO','CURRENT_TIMESTAMP'),
      ('alert_detection_work','updated_at','timestamp with time zone','timestamptz',NULL::integer,'NO','CURRENT_TIMESTAMP'),
      ('alert_detection_work','finished_at','timestamp with time zone','timestamptz',NULL::integer,'YES',NULL::text),
      ('alert_detection_work','operator_requeue_count','integer','int4',NULL::integer,'NO','0'),
      ('alert_detection_work','last_requeued_at','timestamp with time zone','timestamptz',NULL::integer,'YES',NULL::text),
      ('alert_detection_work','last_requeued_by','character varying','varchar',128,'YES',NULL::text),
      ('incident_investigation_work','incident_pk','bigint','int8',NULL::integer,'NO',NULL::text),
      ('incident_investigation_work','status','character varying','varchar',16,'NO','''PENDING''::character varying'),
      ('incident_investigation_work','attempt_count','smallint','int2',NULL::integer,'NO','0'),
      ('incident_investigation_work','available_at','timestamp with time zone','timestamptz',NULL::integer,'NO','CURRENT_TIMESTAMP'),
      ('incident_investigation_work','lease_expires_at','timestamp with time zone','timestamptz',NULL::integer,'YES',NULL::text),
      ('incident_investigation_work','claim_token','uuid','uuid',NULL::integer,'YES',NULL::text),
      ('incident_investigation_work','last_error_code','character varying','varchar',64,'YES',NULL::text),
      ('incident_investigation_work','last_error_detail','character varying','varchar',1000,'YES',NULL::text),
      ('incident_investigation_work','result_code','character varying','varchar',64,'YES',NULL::text),
      ('incident_investigation_work','created_at','timestamp with time zone','timestamptz',NULL::integer,'NO','CURRENT_TIMESTAMP'),
      ('incident_investigation_work','updated_at','timestamp with time zone','timestamptz',NULL::integer,'NO','CURRENT_TIMESTAMP'),
      ('incident_investigation_work','finished_at','timestamp with time zone','timestamptz',NULL::integer,'YES',NULL::text),
      ('incident_investigation_work','operator_requeue_count','integer','int4',NULL::integer,'NO','0'),
      ('incident_investigation_work','last_requeued_at','timestamp with time zone','timestamptz',NULL::integer,'YES',NULL::text),
      ('incident_investigation_work','last_requeued_by','character varying','varchar',128,'YES',NULL::text)
), compared AS (
    SELECT e.*, c.column_name AS actual_column, c.data_type AS actual_type,
           c.udt_name AS actual_udt, c.character_maximum_length AS actual_length,
           c.is_nullable AS actual_nullable, c.column_default AS actual_default
    FROM expected e
    LEFT JOIN information_schema.columns c
      ON c.table_schema = 'public' AND c.table_name = e.table_name
     AND c.column_name = e.column_name
)
SELECT table_name, column_name,
       data_type AS expected_type, udt_name AS expected_udt,
       char_length AS expected_length, is_nullable AS expected_nullable,
       expected_default,
       actual_column IS NOT NULL AS is_present,
       actual_type, actual_udt, actual_length, actual_nullable, actual_default,
       (actual_column IS NOT NULL
        AND actual_type = data_type AND actual_udt = udt_name
        AND actual_length IS NOT DISTINCT FROM char_length
        AND actual_nullable = is_nullable
        AND ((expected_default IS NULL AND actual_default IS NULL)
             OR (expected_default IS NOT NULL AND
                 CASE data_type
                   WHEN 'character varying' THEN actual_default IS NOT DISTINCT FROM expected_default
                   WHEN 'smallint' THEN actual_default IS NOT DISTINCT FROM expected_default
                   WHEN 'integer' THEN actual_default IS NOT DISTINCT FROM expected_default
                   WHEN 'timestamp with time zone' THEN actual_default IS NOT DISTINCT FROM expected_default
                   ELSE FALSE
                 END))
           AS matches_v2
FROM compared
ORDER BY table_name, column_name;

-- Explicit post-repair verification for all six V2 defaults. This returns
-- one row per expected column, including a row with is_present=false if a
-- column is missing. Compare the actual catalog default to V2's expression;
-- also verify type, length, and nullability so a default alone is not treated
-- as proof of column parity.
WITH expected_defaults(table_name, column_name, expected_type,
                       expected_length, v2_default_ddl,
                       expected_catalog_default) AS (
    VALUES
      ('alert_detection_work', 'status', 'character varying', 16,
       '''PENDING''', '''PENDING''::character varying'),
      ('alert_detection_work', 'attempt_count', 'smallint', NULL::integer,
       '0', '0'),
      ('alert_detection_work', 'available_at', 'timestamp with time zone', NULL::integer,
       'CURRENT_TIMESTAMP', 'CURRENT_TIMESTAMP'),
      ('alert_detection_work', 'created_at', 'timestamp with time zone', NULL::integer,
       'CURRENT_TIMESTAMP', 'CURRENT_TIMESTAMP'),
      ('alert_detection_work', 'updated_at', 'timestamp with time zone', NULL::integer,
       'CURRENT_TIMESTAMP', 'CURRENT_TIMESTAMP'),
      ('alert_detection_work', 'operator_requeue_count', 'integer', NULL::integer,
       '0', '0'),
      ('incident_investigation_work', 'status', 'character varying', 16,
       '''PENDING''', '''PENDING''::character varying'),
      ('incident_investigation_work', 'attempt_count', 'smallint', NULL::integer,
       '0', '0'),
      ('incident_investigation_work', 'available_at', 'timestamp with time zone', NULL::integer,
       'CURRENT_TIMESTAMP', 'CURRENT_TIMESTAMP'),
      ('incident_investigation_work', 'created_at', 'timestamp with time zone', NULL::integer,
       'CURRENT_TIMESTAMP', 'CURRENT_TIMESTAMP'),
      ('incident_investigation_work', 'updated_at', 'timestamp with time zone', NULL::integer,
       'CURRENT_TIMESTAMP', 'CURRENT_TIMESTAMP'),
      ('incident_investigation_work', 'operator_requeue_count', 'integer', NULL::integer,
       '0', '0')
)
SELECT e.table_name, e.column_name,
       (c.column_name IS NOT NULL) AS is_present,
       c.data_type AS actual_type,
       c.character_maximum_length AS actual_length,
       c.is_nullable AS actual_is_nullable,
       c.column_default AS actual_default,
       e.expected_type, e.expected_length, e.v2_default_ddl,
       e.expected_catalog_default,
       (c.column_name IS NOT NULL
        AND c.data_type = e.expected_type
        AND (e.expected_length IS NULL
             OR c.character_maximum_length = e.expected_length)
        AND c.is_nullable = 'NO'
        AND c.column_default = e.expected_catalog_default)
           AS matches_v2_default_and_column_shape
FROM expected_defaults e
LEFT JOIN information_schema.columns c
  ON c.table_schema = 'public'
 AND c.table_name = e.table_name
 AND c.column_name = e.column_name
ORDER BY e.table_name, e.column_name;

-- The FK action codes expected from V2's unspecified ON UPDATE/ON DELETE
-- clauses are 'a' (NO ACTION) for both. CHECK constraints have NULL action
-- codes. A NULL actual_definition/is_validated indicates the named object is
-- absent (or exists with the wrong constraint type, which is a failure).
WITH expected_indexes(table_name, object_name, expected_v2_definition,
                       expected_keys, expected_predicate) AS (
    VALUES
      ('alert_detection_work', 'ix_alert_detection_work_available',
       'CREATE INDEX ix_alert_detection_work_available ON public.alert_detection_work (available_at, created_at) WHERE status = ''PENDING''',
       'available_at, created_at', 'status = ''PENDING'''),
      ('alert_detection_work', 'ix_alert_detection_work_expired_lease',
       'CREATE INDEX ix_alert_detection_work_expired_lease ON public.alert_detection_work (lease_expires_at) WHERE status = ''IN_PROGRESS''',
       'lease_expires_at', 'status = ''IN_PROGRESS'''),
      ('incident_investigation_work', 'ix_incident_investigation_work_available',
       'CREATE INDEX ix_incident_investigation_work_available ON public.incident_investigation_work (available_at, created_at) WHERE status = ''PENDING''',
       'available_at, created_at', 'status = ''PENDING'''),
      ('incident_investigation_work', 'ix_incident_investigation_work_expired_lease',
       'CREATE INDEX ix_incident_investigation_work_expired_lease ON public.incident_investigation_work (lease_expires_at) WHERE status = ''IN_PROGRESS''',
       'lease_expires_at', 'status = ''IN_PROGRESS''')
), actual_indexes AS (
    SELECT n.nspname || '.' || r.relname AS table_name,
           idx.relname AS object_name, ix.indisprimary, ix.indisunique,
           ix.indisvalid, ix.indisready, am.amname AS access_method,
           pg_get_indexdef(ix.indexrelid) AS actual_definition,
           pg_get_expr(ix.indpred, ix.indrelid, true) AS actual_predicate,
           pg_get_indexdef(ix.indexrelid, 1, true) AS first_key,
           CASE WHEN ix.indnkeyatts > 1
                THEN pg_get_indexdef(ix.indexrelid, 2, true) END AS second_key,
           ix.indnkeyatts
      FROM pg_catalog.pg_index ix
      JOIN pg_catalog.pg_class r ON r.oid = ix.indrelid
      JOIN pg_catalog.pg_namespace n ON n.oid = r.relnamespace
      JOIN pg_catalog.pg_class idx ON idx.oid = ix.indexrelid
      JOIN pg_catalog.pg_am am ON am.oid = idx.relam
     WHERE ix.indrelid IN ('public.alert_detection_work'::regclass,
                           'public.incident_investigation_work'::regclass)
)
SELECT e.table_name, e.object_name,
       (a.object_name IS NOT NULL) AS is_present,
       a.indisvalid AS is_valid,
       a.indisready AS is_ready,
       a.indisunique AS is_unique,
       a.indisprimary AS is_primary,
       a.access_method,
       concat_ws(', ', a.first_key, a.second_key) AS actual_keys,
       e.expected_keys,
       a.actual_predicate,
       e.expected_predicate,
       a.actual_definition,
       e.expected_v2_definition
  FROM expected_indexes e
  LEFT JOIN actual_indexes a
    ON a.table_name = 'public.' || e.table_name
   AND a.object_name = e.object_name
 ORDER BY e.table_name, e.object_name;

-- Verify row counts remain unchanged. Compare with the preflight output above.
SELECT 'alerts' AS table_name, count(*) AS row_count FROM public.alerts
UNION ALL SELECT 'incidents', count(*) FROM public.incidents
UNION ALL SELECT 'alert_detection_work', count(*) FROM public.alert_detection_work
UNION ALL SELECT 'incident_investigation_work', count(*) FROM public.incident_investigation_work
ORDER BY table_name;

-- V2-level objects outside this repair scope: verify them separately, but do
-- not create them here. This also reports Flyway-history presence without
-- inserting or changing any history record.
SELECT table_name, column_name, data_type, is_nullable, datetime_precision
FROM information_schema.columns
WHERE table_schema = 'public'
  AND ((table_name = 'alerts' AND column_name = 'event_timestamp_utc')
    OR (table_name = 'incidents' AND column_name = 'correlation_key'))
ORDER BY table_name, column_name;

SELECT conname, contype, convalidated, pg_get_constraintdef(oid, true) AS definition
FROM pg_catalog.pg_constraint
WHERE conrelid = 'public.incidents'::regclass
  AND conname = 'uq_incidents_correlation_key';

SELECT to_regclass('public.flyway_schema_history') AS flyway_history_table;
