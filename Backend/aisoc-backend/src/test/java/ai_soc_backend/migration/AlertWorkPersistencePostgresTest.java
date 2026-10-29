package ai_soc_backend.migration;

import ai_soc_backend.incident.entity.Alert;
import ai_soc_backend.incident.entity.AlertDetectionWork;
import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.entity.IncidentInvestigationWork;
import ai_soc_backend.incident.repository.AlertDetectionWorkRepository;
import ai_soc_backend.incident.repository.AlertRepository;
import ai_soc_backend.incident.repository.IncidentInvestigationWorkRepository;
import ai_soc_backend.incident.repository.IncidentRepository;
import ai_soc_backend.incident.service.AlertServiceImpl;
import ai_soc_backend.incident.service.AlertPersistenceService;
import ai_soc_backend.incident.service.AlertIdConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Opt-in JPA mapping and transaction-boundary checks against only the disposable
 * PostgreSQL 18.3 aisoc_test database. Requires AISOC_V2_TEST_PASSWORD and
 * -Daisoc.phase2.postgres-test=true. It never runs Flyway or Hibernate DDL.
 */
@EnabledIfSystemProperty(named = "aisoc.phase2.postgres-test", matches = "true")
@SpringBootTest(classes = AlertWorkPersistencePostgresTest.TestApplication.class)
class AlertWorkPersistencePostgresTest {

    private static final String DATABASE_URL = "jdbc:postgresql://127.0.0.1:55432/aisoc_test";
    private static final String DATABASE_USER = "aisoc_test";
    private static final String PASSWORD_ENV = "AISOC_V2_TEST_PASSWORD";

    @DynamicPropertySource
    static void configureOnlyDisposableDatabase(DynamicPropertyRegistry properties) {
        String password = System.getenv(PASSWORD_ENV);
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(PASSWORD_ENV + " is required for this opt-in test");
        }
        verifyDisposableDatabaseIdentity(password);
        properties.add("spring.datasource.url", () -> DATABASE_URL);
        properties.add("spring.datasource.username", () -> DATABASE_USER);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.flyway.enabled", () -> "false");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        properties.add("spring.jpa.open-in-view", () -> "false");
    }

    private static void verifyDisposableDatabaseIdentity(String password) {
        try (var connection = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, password);
             var statement = connection.createStatement();
             var identity = statement.executeQuery(
                     "SELECT current_database(), session_user, current_setting('server_version')")) {
            if (!identity.next()
                    || !"aisoc_test".equals(identity.getString(1))
                    || !DATABASE_USER.equals(identity.getString(2))
                    || !identity.getString(3).startsWith("18.3")) {
                throw new IllegalStateException("Refusing database test: target is not disposable aisoc_test on PostgreSQL 18.3");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not verify the fixed disposable PostgreSQL test target", exception);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private AlertPersistenceService persistenceService;

    @org.springframework.beans.factory.annotation.Autowired
    private AlertRepository alertRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private AlertDetectionWorkRepository detectionWorkRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private IncidentRepository incidentRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private IncidentInvestigationWorkRepository investigationWorkRepository;

    @MockitoSpyBean
    private AlertDetectionWorkRepository detectionWorkRepositorySpy;

    @org.springframework.beans.factory.annotation.Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void mapsDetectionAndInvestigationWorkAndPersistsAlertWithPendingWorkAtomically() {
        String alertId = "phase2-map-" + UUID.randomUUID();
        String incidentId = "phase2-map-incident-" + UUID.randomUUID();
        Alert savedAlert = null;
        Incident savedIncident = null;
        long detectionWorkCountBefore = detectionWorkRepository.count();
        try {
            savedAlert = persistenceService.saveNewAlertWithDetectionWork(
                    new Alert(alertId, "TEST", "FAILED_LOGON", "LOW", "test-host", "test", LocalDateTime.now()));
            AlertDetectionWork detectionWork = detectionWorkRepository.findById(savedAlert.getId()).orElseThrow();
            assertEquals(savedAlert.getId(), detectionWork.getAlertPk());
            assertEquals("PENDING", detectionWork.getStatus());
            assertEquals(0, detectionWork.getAttemptCount());
            assertNotNull(detectionWork.getAvailableAt());
            assertNotNull(detectionWork.getCreatedAt());
            assertNotNull(detectionWork.getUpdatedAt());
            assertEquals(detectionWorkCountBefore + 1, detectionWorkRepository.count());

            Instant completedAt = Instant.parse("2026-10-28T08:32:00.222333Z");
            detectionWork.setStatus("COMPLETED");
            detectionWork.setAttemptCount((short) 3);
            detectionWork.setLastErrorCode("TEMPORARY_FAILURE");
            detectionWork.setLastErrorDetail("sanitized failure");
            detectionWork.setResultCode("BELOW_THRESHOLD");
            detectionWork.setUpdatedAt(completedAt);
            detectionWork.setFinishedAt(completedAt);
            detectionWorkRepository.saveAndFlush(detectionWork);

            assertThrows(AlertIdConflictException.class,
                    () -> persistenceService.saveNewAlertWithDetectionWork(new Alert(
                            alertId, "TEST", "FAILED_LOGON", "LOW", "test-host", "racing duplicate",
                            LocalDateTime.now())));

            AlertServiceImpl alertService = new AlertServiceImpl(alertRepository, persistenceService);
            alertService.processAlert(new Alert(
                    alertId, "REDELIVERED", "FAILED_LOGON", "HIGH", "different-payload", "ignored",
                    LocalDateTime.now()));
            AlertDetectionWork afterRedelivery = detectionWorkRepository.findById(savedAlert.getId()).orElseThrow();
            assertEquals("COMPLETED", afterRedelivery.getStatus());
            assertEquals(3, afterRedelivery.getAttemptCount());
            assertEquals("TEMPORARY_FAILURE", afterRedelivery.getLastErrorCode());
            assertEquals("sanitized failure", afterRedelivery.getLastErrorDetail());
            assertEquals("BELOW_THRESHOLD", afterRedelivery.getResultCode());
            assertEquals(completedAt, afterRedelivery.getUpdatedAt());
            assertEquals(completedAt, afterRedelivery.getFinishedAt());
            assertEquals(detectionWorkCountBefore + 1, detectionWorkRepository.count());

            savedIncident = incidentRepository.saveAndFlush(new Incident(
                    incidentId, "Mapping test", "LOW", "OPEN", "test-host", "test",
                    LocalDateTime.now()));
            Instant available = Instant.parse("2026-10-28T08:30:00.123456Z");
            Instant lease = Instant.parse("2026-10-28T08:31:00.654321Z");
            Instant created = Instant.parse("2026-10-28T08:29:00.000001Z");
            UUID claim = UUID.randomUUID();
            IncidentInvestigationWork investigationWork = new IncidentInvestigationWork(savedIncident.getId());
            investigationWork.setStatus("IN_PROGRESS");
            investigationWork.setAttemptCount((short) 1);
            investigationWork.setAvailableAt(available);
            investigationWork.setLeaseExpiresAt(lease);
            investigationWork.setClaimToken(claim);
            investigationWork.setLastErrorCode("RETRYABLE_TEST");
            investigationWork.setLastErrorDetail("sanitized test detail");
            investigationWork.setCreatedAt(created);
            investigationWork.setUpdatedAt(created);
            investigationWork.setOperatorRequeueCount(1);
            investigationWork.setLastRequeuedAt(created);
            investigationWork.setLastRequeuedBy("test-operator");
            investigationWorkRepository.saveAndFlush(investigationWork);

            IncidentInvestigationWork loaded = investigationWorkRepository.findById(savedIncident.getId()).orElseThrow();
            assertEquals(savedIncident.getId(), loaded.getIncidentPk());
            assertEquals("IN_PROGRESS", loaded.getStatus());
            assertEquals(1, loaded.getAttemptCount());
            assertEquals(available, loaded.getAvailableAt());
            assertEquals(lease, loaded.getLeaseExpiresAt());
            assertEquals(claim, loaded.getClaimToken());
            assertEquals("RETRYABLE_TEST", loaded.getLastErrorCode());
            assertEquals("sanitized test detail", loaded.getLastErrorDetail());
            assertEquals(created, loaded.getCreatedAt());
            assertEquals(created, loaded.getUpdatedAt());
            assertEquals(1, loaded.getOperatorRequeueCount());
            assertEquals(created, loaded.getLastRequeuedAt());
            assertEquals("test-operator", loaded.getLastRequeuedBy());
        } finally {
            Long alertPk = savedAlert == null ? alertRepository.findByAlertId(alertId).map(Alert::getId).orElse(null)
                    : savedAlert.getId();
            Long incidentPk = savedIncident == null ? incidentRepository.findByIncidentId(incidentId)
                    .map(Incident::getId).orElse(null) : savedIncident.getId();
            cleanup(alertPk, incidentPk);
        }
    }

    @Test
    void rollsBackAlertWhenDetectionWorkInsertFails() {
        String alertId = "phase2-rollback-" + UUID.randomUUID();
        doThrow(new DataIntegrityViolationException("simulated detection-work insert failure"))
                .when(detectionWorkRepositorySpy).saveAndFlush(any(AlertDetectionWork.class));

        assertThrows(DataIntegrityViolationException.class, () -> persistenceService.saveNewAlertWithDetectionWork(
                new Alert(alertId, "TEST", "FAILED_LOGON", "LOW", "test-host", "test", LocalDateTime.now())));

        assertTrue(alertRepository.findByAlertId(alertId).isEmpty(),
                "The alert insert must roll back with failed detection-work persistence");
    }

    @Test
    void concurrentDuplicateRequestsPersistOneAlertAndOneWorkRowAndReturnTheWinner() throws Exception {
        String alertId = "phase2-concurrent-" + UUID.randomUUID();
        String password = System.getenv(PASSWORD_ENV);
        verifyDisposableDatabaseIdentity(password);
        AlertServiceImpl alertService = new AlertServiceImpl(alertRepository, persistenceService);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService requests = Executors.newFixedThreadPool(2);

        try {
            try (Connection blocker = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, password)) {
                blocker.setAutoCommit(false);
                try (var lock = blocker.createStatement()) {
                    lock.execute("LOCK TABLE public.alerts IN SHARE ROW EXCLUSIVE MODE");
                }

                Future<Alert> first = requests.submit(() -> submitAfterBarrier(alertService, alertId, ready, start));
                Future<Alert> second = requests.submit(() -> submitAfterBarrier(alertService, alertId, ready, start));
                assertTrue(ready.await(5, TimeUnit.SECONDS), "Both request threads must reach the start barrier");
                start.countDown();
                awaitTwoBlockedAlertInserts(password);

                // Both requests passed the initial alertId lookup and are blocked at
                // distinct INSERT transactions before the database lock is released.
                blocker.commit();

                Alert firstResult = first.get(15, TimeUnit.SECONDS);
                Alert secondResult = second.get(15, TimeUnit.SECONDS);
                assertEquals(alertId, firstResult.getAlertId());
                assertEquals(firstResult.getId(), secondResult.getId());

                try (Connection verification = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, password)) {
                    assertEquals(1, countForAlert(verification,
                            "SELECT count(*) FROM public.alerts WHERE alert_id = ?", alertId));
                    assertEquals(1, countForAlert(verification,
                            "SELECT count(*) FROM public.alert_detection_work w "
                                    + "JOIN public.alerts a ON a.id = w.alert_pk WHERE a.alert_id = ?", alertId));
                    assertEquals("PENDING", valueForAlert(verification,
                            "SELECT w.status FROM public.alert_detection_work w "
                                    + "JOIN public.alerts a ON a.id = w.alert_pk WHERE a.alert_id = ?", alertId));
                }
            }
        } finally {
            start.countDown();
            requests.shutdownNow();
            requests.awaitTermination(10, TimeUnit.SECONDS);
            Long alertPk = alertRepository.findByAlertId(alertId).map(Alert::getId).orElse(null);
            cleanup(alertPk, null);
        }
    }

    private Alert submitAfterBarrier(
            AlertServiceImpl service, String alertId, CountDownLatch ready, CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Request start barrier timed out");
        }
        return service.processAlert(new Alert(
                alertId, "TEST", "FAILED_LOGON", "LOW", "test-host", "concurrent test", LocalDateTime.now()));
    }

    private void awaitTwoBlockedAlertInserts(String password) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        int blocked = 0;
        while (System.nanoTime() < deadline) {
            try (Connection monitor = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, password);
                 var statement = monitor.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT count(*) FROM pg_stat_activity "
                                 + "WHERE datname = current_database() AND usename = current_user "
                                 + "AND pid <> pg_backend_pid() AND wait_event_type = 'Lock' "
                                 + "AND query ILIKE 'insert%into%alerts%'")) {
                result.next();
                blocked = result.getInt(1);
            }
            if (blocked >= 2) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Expected two overlapping alert INSERT transactions; observed " + blocked);
    }

    private int countForAlert(Connection connection, String sql, String alertId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, alertId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private String valueForAlert(Connection connection, String sql, String alertId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, alertId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private void cleanup(Long alertPk, Long incidentPk) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            if (incidentPk != null) {
                investigationWorkRepository.deleteById(incidentPk);
                incidentRepository.deleteById(incidentPk);
            }
            if (alertPk != null) {
                detectionWorkRepository.deleteById(alertPk);
                alertRepository.deleteById(alertPk);
            }
        });
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("ai_soc_backend.incident.entity")
    @EnableJpaRepositories("ai_soc_backend.incident.repository")
    @Import(AlertPersistenceService.class)
    static class TestApplication {
    }
}
