package ai_soc_backend.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in migration verification against explicitly fixed disposable targets.
 * Existing mode uses -Daisoc.v2.migration-test=true. Fresh mode additionally
 * requires -Daisoc.v2.fresh-database-test=true. Both require the test-only
 * AISOC_V2_TEST_PASSWORD environment variable.
 */
@EnabledIfSystemProperty(named = "aisoc.v2.migration-test", matches = "true")
class V2MigrationPostgresTest {

    private static final String EXISTING_DATABASE = "aisoc_test";
    private static final String FRESH_DATABASE = "aisoc_v2_fresh_test";
    private static final String URL_PREFIX = "jdbc:postgresql://127.0.0.1:55432/";
    private static final String EXISTING_URL = URL_PREFIX + EXISTING_DATABASE;
    private static final String FRESH_URL = URL_PREFIX + FRESH_DATABASE;
    private static final String USER = "aisoc_test";
    private static final String PASSWORD_ENV = "AISOC_V2_TEST_PASSWORD";
    private static final String FRESH_MODE_PROPERTY = "aisoc.v2.fresh-database-test";
    private static String activeUrl;
    private static boolean freshDatabaseCreatedByThisRun;
    private static long freshDatabaseOid = -1;

    @BeforeAll
    static void applyMigrationsToVerifiedDisposableDatabase() throws Exception {
        String password = System.getenv(PASSWORD_ENV);
        assertNotNull(password, PASSWORD_ENV + " must be set for the opt-in migration test");

        if (Boolean.getBoolean(FRESH_MODE_PROPERTY)) {
            try {
                createFreshDatabase(password);
                activeUrl = FRESH_URL;
                verifyConnectedDatabase(activeUrl, password, FRESH_DATABASE);
                migrate(activeUrl, password);
            } catch (Exception | Error failure) {
                try {
                    cleanupFreshDatabase(password);
                } catch (Exception | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        } else {
            activeUrl = EXISTING_URL;
            verifyConnectedDatabase(activeUrl, password, EXISTING_DATABASE);
            migrate(activeUrl, password);
        }
    }

    @AfterAll
    static void cleanupFreshDatabaseCreatedByThisRun() throws Exception {
        if (freshDatabaseCreatedByThisRun) {
            cleanupFreshDatabase(System.getenv(PASSWORD_ENV));
        }
    }

    private static void migrate(String url, String password) {
        Flyway.configure()
                .dataSource(url, USER, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static void createFreshDatabase(String password) throws Exception {
        if (!FRESH_DATABASE.equals("aisoc_v2_fresh_test")) {
            throw new IllegalStateException("Fresh database target is not allow-listed");
        }
        try (Connection maintenance = DriverManager.getConnection(EXISTING_URL, USER, password)) {
            verifyIdentity(maintenance, EXISTING_DATABASE);
            try (Statement statement = maintenance.createStatement();
                 ResultSet privilege = statement.executeQuery(
                         "SELECT rolcreatedb OR rolsuper FROM pg_roles WHERE rolname = current_user")) {
                assertTrue(privilege.next() && privilege.getBoolean(1),
                        "Connected test role must have CREATEDB or SUPERUSER privilege");
            }
            try (Statement statement = maintenance.createStatement();
                 ResultSet existing = statement.executeQuery(
                         "SELECT count(*) FROM pg_database WHERE datname = 'aisoc_v2_fresh_test'")) {
                assertTrue(existing.next());
                assertEquals(0, existing.getInt(1),
                        "Pre-existing fresh-test database must be inspected manually; it is never reused or dropped");
            }
            try (Statement statement = maintenance.createStatement()) {
                statement.execute("CREATE DATABASE aisoc_v2_fresh_test OWNER aisoc_test");
                freshDatabaseCreatedByThisRun = true;
            }
            try (Statement statement = maintenance.createStatement();
                 ResultSet created = statement.executeQuery(
                         "SELECT oid FROM pg_database WHERE datname = 'aisoc_v2_fresh_test'")) {
                assertTrue(created.next(), "Created fresh database is not visible in pg_database");
                freshDatabaseOid = created.getLong(1);
            }
        }
    }

    private static void cleanupFreshDatabase(String password) throws Exception {
        if (!freshDatabaseCreatedByThisRun || !FRESH_DATABASE.equals("aisoc_v2_fresh_test")) {
            return;
        }
        try (Connection maintenance = DriverManager.getConnection(EXISTING_URL, USER, password)) {
            verifyIdentity(maintenance, EXISTING_DATABASE);
            try (Statement statement = maintenance.createStatement();
                 ResultSet privilege = statement.executeQuery(
                         "SELECT rolcreatedb OR rolsuper FROM pg_roles WHERE rolname = current_user")) {
                assertTrue(privilege.next() && privilege.getBoolean(1),
                        "Connected test role must retain CREATEDB or SUPERUSER privilege for cleanup");
            }
            try (Statement statement = maintenance.createStatement();
                 ResultSet target = statement.executeQuery(
                         "SELECT oid, datname, pg_get_userbyid(datdba) FROM pg_database "
                                 + "WHERE datname = 'aisoc_v2_fresh_test'")) {
                assertTrue(target.next(), "Fresh database vanished before cleanup");
                assertEquals(freshDatabaseOid, target.getLong(1),
                        "Refusing to drop a database other than the one created by this test invocation");
                assertEquals(FRESH_DATABASE, target.getString(2));
                assertEquals(USER, target.getString(3));
                assertTrue(!target.next(), "Exact fresh database name must identify one database");
            }
            try (Statement statement = maintenance.createStatement();
                 ResultSet activeSessions = statement.executeQuery(
                         "SELECT count(*) FROM pg_stat_activity "
                                 + "WHERE datname = 'aisoc_v2_fresh_test' AND pid <> pg_backend_pid()")) {
                assertTrue(activeSessions.next());
                assertEquals(0, activeSessions.getInt(1),
                        "Fresh database has other sessions; refusing to terminate them or drop the database");
            }
            try (Statement statement = maintenance.createStatement()) {
                statement.execute("DROP DATABASE aisoc_v2_fresh_test");
            }
            freshDatabaseCreatedByThisRun = false;
            freshDatabaseOid = -1;
            try (Statement statement = maintenance.createStatement();
                 ResultSet remaining = statement.executeQuery(
                         "SELECT count(*) FROM pg_database WHERE datname = 'aisoc_v2_fresh_test'")) {
                assertTrue(remaining.next());
                assertEquals(0, remaining.getInt(1));
            }
            assertSuccessfulV1V2History(maintenance);
            try (Statement statement = maintenance.createStatement();
                 ResultSet tables = statement.executeQuery(
                         "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' "
                                 + "AND table_name IN ('alerts', 'incidents', 'ai_investigations', "
                                 + "'ai_investigation_evidence', 'alert_detection_work', "
                                 + "'incident_investigation_work')")) {
                assertTrue(tables.next());
                assertEquals(6, tables.getInt(1), "Original aisoc_test schema must remain intact");
            }
        }
    }

    private static void verifyConnectedDatabase(String url, String password, String expectedDatabase)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, USER, password)) {
            verifyIdentity(connection, expectedDatabase);
        }
    }

    private static void verifyIdentity(Connection connection, String expectedDatabase) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet identity = statement.executeQuery(
                     "SELECT current_database(), session_user, current_setting('server_version')")) {
            assertTrue(identity.next());
            assertEquals(expectedDatabase, identity.getString(1));
            assertEquals(USER, identity.getString(2));
            assertTrue(identity.getString(3).startsWith("18.3"), identity.getString(3));
        }
    }

    private static void assertSuccessfulV1V2History(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet history = statement.executeQuery(
                     "SELECT version, success FROM public.flyway_schema_history "
                             + "WHERE version IN ('1', '2') ORDER BY version")) {
            assertTrue(history.next());
            assertEquals("1", history.getString(1));
            assertTrue(history.getBoolean(2));
            assertTrue(history.next());
            assertEquals("2", history.getString(1));
            assertTrue(history.getBoolean(2));
            assertTrue(!history.next());
        }
    }

    @Test
    void recordsV1AndV2AndPreservesV1TimestampType() throws Exception {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            try (ResultSet history = statement.executeQuery(
                    "SELECT version, success FROM public.flyway_schema_history "
                            + "WHERE version IN ('1', '2') ORDER BY version")) {
                assertTrue(history.next());
                assertEquals("1", history.getString(1));
                assertTrue(history.getBoolean(2));
                assertTrue(history.next());
                assertEquals("2", history.getString(1));
                assertTrue(history.getBoolean(2));
                assertTrue(!history.next());
            }

            assertColumn(statement, "alerts", "timestamp", "timestamp without time zone", "YES");
            assertColumn(statement, "alerts", "event_timestamp_utc", "timestamp with time zone", "YES");
            assertColumn(statement, "incidents", "correlation_key", "character varying", "YES");
            assertEquals(6, columnPrecision(statement, "alerts", "timestamp"));
        }
    }

    @Test
    void installsExpectedWorkConstraintsIndexesAndForeignKeys() throws Exception {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertEquals(1, scalarInt(statement,
                    "SELECT count(*) FROM pg_constraint WHERE conname = 'uq_incidents_correlation_key' "
                            + "AND contype = 'u'"));
            assertEquals(6, scalarInt(statement,
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' "
                            + "AND table_name IN ('alerts', 'incidents', 'ai_investigations', "
                            + "'ai_investigation_evidence', 'alert_detection_work', "
                            + "'incident_investigation_work')"));
            assertEquals(6, scalarInt(statement,
                    "SELECT count(*) FROM pg_constraint WHERE contype = 'p' AND conrelid IN ("
                            + "SELECT c.oid FROM pg_class c WHERE c.relnamespace = 'public'::regnamespace "
                            + "AND c.relname IN ('alerts', 'incidents', 'ai_investigations', "
                            + "'ai_investigation_evidence', 'alert_detection_work', "
                            + "'incident_investigation_work'))"));
            assertEquals(4, scalarInt(statement,
                    "SELECT count(*) FROM pg_constraint WHERE contype = 'u' AND conrelid IN ("
                            + "SELECT c.oid FROM pg_class c WHERE c.relnamespace = 'public'::regnamespace "
                            + "AND c.relname IN ('alerts', 'incidents', 'ai_investigations'))"));
            assertEquals(4, scalarInt(statement,
                    "SELECT count(*) FROM pg_constraint WHERE contype = 'f' AND conrelid IN ("
                            + "SELECT c.oid FROM pg_class c WHERE c.relnamespace = 'public'::regnamespace "
                            + "AND c.relname IN ('ai_investigations', 'ai_investigation_evidence', "
                            + "'alert_detection_work', 'incident_investigation_work'))"));
            assertEquals(2, scalarInt(statement,
                    "SELECT count(*) FROM pg_constraint WHERE conname IN ("
                            + "'fk_alert_detection_work_alert', 'fk_incident_investigation_work_incident') "
                            + "AND contype = 'f'"));
            assertIndex(statement, "uq_incidents_correlation_key", true,
                    "create unique index uq_incidents_correlation_key on public.incidents using btree (correlation_key)",
                    null);
            assertIndex(statement, "ix_alert_detection_work_available", false,
                    "create index ix_alert_detection_work_available on public.alert_detection_work "
                            + "using btree (available_at, created_at) where ((status)::text = 'pending'::text)",
                    "((status)::text = 'pending'::text)");
            assertIndex(statement, "ix_alert_detection_work_expired_lease", false,
                    "create index ix_alert_detection_work_expired_lease on public.alert_detection_work "
                            + "using btree (lease_expires_at) where ((status)::text = 'in_progress'::text)",
                    "((status)::text = 'in_progress'::text)");
            assertIndex(statement, "ix_incident_investigation_work_available", false,
                    "create index ix_incident_investigation_work_available "
                            + "on public.incident_investigation_work using btree (available_at, created_at) "
                            + "where ((status)::text = 'pending'::text)",
                    "((status)::text = 'pending'::text)");
            assertIndex(statement, "ix_incident_investigation_work_expired_lease", false,
                    "create index ix_incident_investigation_work_expired_lease "
                            + "on public.incident_investigation_work using btree (lease_expires_at) "
                            + "where ((status)::text = 'in_progress'::text)",
                    "((status)::text = 'in_progress'::text)");
            assertEquals(1, scalarInt(statement,
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' "
                            + "AND table_name = 'alert_detection_work' AND column_name = 'available_at' "
                            + "AND data_type = 'timestamp with time zone' AND is_nullable = 'NO'"));
            assertEquals(1, scalarInt(statement,
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' "
                            + "AND table_name = 'incident_investigation_work' AND column_name = 'lease_expires_at' "
                            + "AND data_type = 'timestamp with time zone' AND is_nullable = 'YES'"));
            assertEquals(7, scalarInt(statement,
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' "
                            + "AND table_name = 'alert_detection_work' AND is_nullable = 'NO'"));
            assertEquals(7, scalarInt(statement,
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' "
                            + "AND table_name = 'incident_investigation_work' AND is_nullable = 'NO'"));
            assertEquals(13, scalarInt(statement,
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' "
                            + "AND data_type = 'timestamp with time zone' AND table_name IN ("
                            + "'alerts', 'alert_detection_work', 'incident_investigation_work')"));
            assertEquals(10, scalarInt(statement,
                    "SELECT count(*) FROM pg_constraint WHERE conrelid = "
                            + "'public.alert_detection_work'::regclass AND contype = 'c'"));
            assertEquals(10, scalarInt(statement,
                    "SELECT count(*) FROM pg_constraint WHERE conrelid = "
                            + "'public.incident_investigation_work'::regclass AND contype = 'c'"));
        }
    }

    @Test
    void allowsMultipleNullCorrelationKeysButRejectsDuplicateNonNullKeys() throws Exception {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                insertIncident(connection, "null-key-a", null, "CLOSED");
                insertIncident(connection, "null-key-b", null, "CLOSED");
                String key = "a".repeat(64);
                insertIncident(connection, "key-owner", key, "CLOSED");
                assertConstraintViolation(connection, "23505",
                        () -> insertIncident(connection, "key-duplicate", key, "OPEN"));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void enforcesDetectionAndInvestigationWorkChecks() throws Exception {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                long alertPk = insertAlert(connection);
                long incidentPk = insertIncident(connection, "work-parent", null, "OPEN");

                execute(connection, "INSERT INTO public.alert_detection_work "
                        + "(alert_pk, status, result_code, finished_at) VALUES (" + alertPk
                        + ", 'COMPLETED', 'NOT_APPLICABLE', CURRENT_TIMESTAMP)");
                execute(connection, "INSERT INTO public.incident_investigation_work "
                        + "(incident_pk, status, result_code, finished_at) VALUES (" + incidentPk
                        + ", 'COMPLETED', 'NO_INVESTIGATION_REQUIRED', CURRENT_TIMESTAMP)");

                assertConstraintViolation(connection, "23505", () -> execute(connection,
                        "INSERT INTO public.alert_detection_work (alert_pk) VALUES (" + alertPk + ")"));
                assertConstraintViolation(connection, "23505", () -> execute(connection,
                        "INSERT INTO public.incident_investigation_work (incident_pk) VALUES ("
                                + incidentPk + ")"));
                assertConstraintViolation(connection, "23514", () -> execute(connection,
                        "UPDATE public.alert_detection_work SET result_code = NULL WHERE alert_pk = "
                                + alertPk));
                assertConstraintViolation(connection, "23514", () -> execute(connection,
                        "UPDATE public.alert_detection_work SET status = 'UNKNOWN' WHERE alert_pk = "
                                + alertPk));
                assertConstraintViolation(connection, "23514", () -> execute(connection,
                        "UPDATE public.alert_detection_work SET attempt_count = 6 WHERE alert_pk = "
                                + alertPk));
                assertConstraintViolation(connection, "23514", () -> execute(connection,
                        "UPDATE public.alert_detection_work SET status = 'PENDING', attempt_count = 5, "
                                + "finished_at = NULL, result_code = NULL WHERE alert_pk = " + alertPk));
                assertConstraintViolation(connection, "23514", () -> execute(connection,
                        "UPDATE public.alert_detection_work SET status = 'IN_PROGRESS', attempt_count = 1, "
                                + "finished_at = NULL, result_code = NULL, lease_expires_at = CURRENT_TIMESTAMP, "
                                + "claim_token = NULL WHERE alert_pk = " + alertPk));
                assertConstraintViolation(connection, "23514", () -> execute(connection,
                        "UPDATE public.alert_detection_work SET status = 'FAILED', result_code = NULL, "
                                + "finished_at = CURRENT_TIMESTAMP, last_error_code = '   ', "
                                + "lease_expires_at = NULL, claim_token = NULL WHERE alert_pk = " + alertPk));
                assertConstraintViolation(connection, "23514", () -> execute(connection,
                        "UPDATE public.alert_detection_work SET operator_requeue_count = 1, "
                                + "last_requeued_at = CURRENT_TIMESTAMP, last_requeued_by = NULL "
                                + "WHERE alert_pk = " + alertPk));
                assertConstraintViolation(connection, "23514", () -> execute(connection,
                        "UPDATE public.incident_investigation_work SET result_code = NULL "
                                + "WHERE incident_pk = " + incidentPk));
                assertConstraintViolation(connection, "23514", () -> execute(connection,
                        "UPDATE public.incident_investigation_work SET status = 'UNKNOWN' "
                                + "WHERE incident_pk = " + incidentPk));
                assertConstraintViolation(connection, "23514", () -> execute(connection,
                        "UPDATE public.incident_investigation_work SET attempt_count = 6 "
                                + "WHERE incident_pk = " + incidentPk));
                assertConstraintViolation(connection, "23514", () -> execute(connection,
                        "UPDATE public.incident_investigation_work SET status = 'PENDING', attempt_count = 5, "
                                + "finished_at = NULL, result_code = NULL WHERE incident_pk = " + incidentPk));
                assertConstraintViolation(connection, "23514", () -> execute(connection,
                        "UPDATE public.incident_investigation_work SET status = 'IN_PROGRESS', attempt_count = 1, "
                                + "finished_at = NULL, result_code = NULL, lease_expires_at = CURRENT_TIMESTAMP, "
                                + "claim_token = NULL WHERE incident_pk = " + incidentPk));
                assertConstraintViolation(connection, "23514", () -> execute(connection,
                        "UPDATE public.incident_investigation_work SET status = 'FAILED', result_code = NULL, "
                                + "finished_at = CURRENT_TIMESTAMP, last_error_code = '   ', "
                                + "lease_expires_at = NULL, claim_token = NULL WHERE incident_pk = " + incidentPk));
                assertConstraintViolation(connection, "23514", () -> execute(connection,
                        "UPDATE public.incident_investigation_work SET operator_requeue_count = 1, "
                                + "last_requeued_at = CURRENT_TIMESTAMP, last_requeued_by = NULL "
                                + "WHERE incident_pk = " + incidentPk));
                assertConstraintViolation(connection, "23503", () -> execute(connection,
                        "INSERT INTO public.incident_investigation_work (incident_pk) VALUES (-999999)"));
                assertConstraintViolation(connection, "23503", () -> execute(connection,
                        "INSERT INTO public.alert_detection_work (alert_pk) VALUES (-999999)"));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void rejectsEveryTerminalAndLeaseInvariantForBothWorkTables() throws Exception {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                long alertPk = insertAlert(connection);
                long incidentPk = insertIncident(connection, "terminal-parent", null, "OPEN");
                execute(connection, "INSERT INTO public.alert_detection_work "
                        + "(alert_pk, status, result_code, finished_at) VALUES (" + alertPk
                        + ", 'COMPLETED', 'NOT_APPLICABLE', CURRENT_TIMESTAMP)");
                execute(connection, "INSERT INTO public.incident_investigation_work "
                        + "(incident_pk, status, result_code, finished_at) VALUES (" + incidentPk
                        + ", 'COMPLETED', 'NO_INVESTIGATION_REQUIRED', CURRENT_TIMESTAMP)");

                assertWorkStateChecks(connection, "alert_detection_work", "alert_pk", alertPk,
                        "NOT_APPLICABLE");
                assertWorkStateChecks(connection, "incident_investigation_work", "incident_pk", incidentPk,
                        "NO_INVESTIGATION_REQUIRED");
            } finally {
                connection.rollback();
            }
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(activeUrl, USER, System.getenv(PASSWORD_ENV));
    }

    private static void assertColumn(
            Statement statement, String table, String column, String type, String nullable
    ) throws SQLException {
        try (ResultSet result = statement.executeQuery(
                "SELECT data_type, is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = '" + table
                        + "' AND column_name = '" + column + "'")) {
            assertTrue(result.next(), table + "." + column + " exists");
            assertEquals(type, result.getString(1));
            assertEquals(nullable, result.getString(2));
        }
    }

    private static int columnPrecision(Statement statement, String table, String column) throws SQLException {
        try (ResultSet result = statement.executeQuery(
                "SELECT datetime_precision FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = '" + table
                        + "' AND column_name = '" + column + "'")) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static int scalarInt(Statement statement, String sql) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static void assertIndex(
            Statement statement, String name, boolean unique, String expectedDefinition, String expectedPredicate
    ) throws SQLException {
        try (var query = statement.getConnection().prepareStatement(
                "SELECT i.indisunique, am.amname, pg_get_indexdef(i.indexrelid), "
                        + "pg_get_expr(i.indpred, i.indrelid) "
                        + "FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "JOIN pg_am am ON am.oid = c.relam "
                        + "WHERE n.nspname = 'public' AND c.relname = ?")) {
            query.setString(1, name);
            try (ResultSet result = query.executeQuery()) {
                assertTrue(result.next(), "Missing index " + name);
                assertEquals(unique, result.getBoolean(1), name + " uniqueness");
                assertEquals("btree", result.getString(2), name + " method");
                assertEquals(normalizeSql(expectedDefinition), normalizeSql(result.getString(3)),
                        name + " definition");
                assertEquals(expectedPredicate == null ? null : normalizeSql(expectedPredicate),
                        result.getString(4) == null ? null : normalizeSql(result.getString(4)),
                        name + " predicate");
                assertTrue(!result.next(), "Index name must identify exactly one index: " + name);
            }
        }
    }

    private static String normalizeSql(String sql) {
        return sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private static void assertWorkStateChecks(
            Connection connection, String table, String parentColumn, long parentId, String validResult
    ) throws Exception {
        String where = " WHERE " + parentColumn + " = " + parentId;
        assertCheckFailure(connection, table, where, "result_code = NULL");
        assertCheckFailure(connection, table, where, "result_code = 'UNSUPPORTED'");
        assertCheckFailure(connection, table, where,
                "status = 'PENDING', result_code = 'NOT_APPLICABLE', finished_at = NULL");
        assertCheckFailure(connection, table, where,
                "status = 'FAILED', result_code = NULL, last_error_code = NULL, "
                        + "finished_at = CURRENT_TIMESTAMP");
        assertCheckFailure(connection, table, where,
                "status = 'FAILED', result_code = NULL, last_error_code = '   ', "
                        + "finished_at = CURRENT_TIMESTAMP");
        assertCheckFailure(connection, table, where,
                "status = 'IN_PROGRESS', attempt_count = 1, result_code = NULL, finished_at = NULL, "
                        + "lease_expires_at = NULL, claim_token = '00000000-0000-0000-0000-000000000001'");
        assertCheckFailure(connection, table, where,
                "status = 'IN_PROGRESS', attempt_count = 1, result_code = NULL, finished_at = NULL, "
                        + "lease_expires_at = CURRENT_TIMESTAMP, claim_token = NULL");
        assertCheckFailure(connection, table, where,
                "lease_expires_at = CURRENT_TIMESTAMP, "
                        + "claim_token = '00000000-0000-0000-0000-000000000001'");
        assertCheckFailure(connection, table, where,
                "status = 'PENDING', result_code = NULL, finished_at = CURRENT_TIMESTAMP");
        assertCheckFailure(connection, table, where,
                "status = 'IN_PROGRESS', attempt_count = 1, result_code = NULL, "
                        + "lease_expires_at = CURRENT_TIMESTAMP, "
                        + "claim_token = '00000000-0000-0000-0000-000000000001', "
                        + "finished_at = CURRENT_TIMESTAMP");
        assertCheckFailure(connection, table, where, "finished_at = NULL");
        assertCheckFailure(connection, table, where,
                "status = 'FAILED', result_code = NULL, last_error_code = 'FAILED', finished_at = NULL");
        assertCheckFailure(connection, table, where, "operator_requeue_count = -1");
        assertCheckFailure(connection, table, where,
                "operator_requeue_count = 1, last_requeued_at = CURRENT_TIMESTAMP, last_requeued_by = NULL");
        assertCheckFailure(connection, table, where,
                "operator_requeue_count = 0, last_requeued_at = CURRENT_TIMESTAMP, "
                        + "last_requeued_by = 'operator'");
        assertCheckFailure(connection, table, where,
                "status = 'PENDING', attempt_count = 5, result_code = NULL, finished_at = NULL");
        assertCheckFailure(connection, table, where,
                "status = 'IN_PROGRESS', attempt_count = 0, result_code = NULL, finished_at = NULL, "
                        + "lease_expires_at = CURRENT_TIMESTAMP, "
                        + "claim_token = '00000000-0000-0000-0000-000000000001'");
        assertCheckFailure(connection, table, where, "attempt_count = 6");
        assertConstraintViolation(connection, "23502", () -> execute(connection,
                "UPDATE public." + table + " SET status = NULL" + where));

        execute(connection, "UPDATE public." + table + " SET status = 'PENDING', result_code = NULL, "
                + "finished_at = NULL, lease_expires_at = NULL, claim_token = NULL" + where);
        execute(connection, "UPDATE public." + table + " SET status = 'COMPLETED', result_code = '"
                + validResult + "', finished_at = CURRENT_TIMESTAMP, lease_expires_at = NULL, "
                + "claim_token = NULL" + where);
    }

    private static void assertCheckFailure(
            Connection connection, String table, String where, String assignments
    ) throws Exception {
        assertConstraintViolation(connection, "23514", () -> execute(connection,
                "UPDATE public." + table + " SET " + assignments + where));
    }

    private static long insertAlert(Connection connection) throws SQLException {
        String id = "migration-test-" + UUID.randomUUID();
        try (var statement = connection.prepareStatement(
                "INSERT INTO public.alerts (alert_id, source, rule, severity) "
                        + "VALUES (?, 'TEST', 'FAILED_LOGON', 'LOW') RETURNING id")) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static long insertIncident(Connection connection, String id, String key, String status)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO public.incidents "
                        + "(incident_id, title, severity, status, correlation_key) "
                        + "VALUES (?, 'Migration test', 'LOW', ?, ?) RETURNING id")) {
            statement.setString(1, id + "-" + UUID.randomUUID());
            statement.setString(2, status);
            statement.setString(3, key);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static void assertConstraintViolation(
            Connection connection, String expectedState, SqlAction action
    ) throws Exception {
        Savepoint savepoint = connection.setSavepoint();
        SQLException error = assertThrows(SQLException.class, action::run);
        assertEquals(expectedState, error.getSQLState(), error.getMessage());
        connection.rollback(savepoint);
        connection.releaseSavepoint(savepoint);
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws Exception;
    }
}
