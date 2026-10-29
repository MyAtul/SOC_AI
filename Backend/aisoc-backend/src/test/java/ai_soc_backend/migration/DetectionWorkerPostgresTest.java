package ai_soc_backend.migration;

import ai_soc_backend.incident.entity.Alert;
import ai_soc_backend.incident.entity.AlertDetectionWork;
import ai_soc_backend.incident.repository.AlertDetectionWorkRepository;
import ai_soc_backend.incident.repository.AlertRepository;
import ai_soc_backend.incident.service.AlertPersistenceService;
import ai_soc_backend.incident.service.DetectionWorkClaim;
import ai_soc_backend.incident.service.DetectionWorkClaimService;
import ai_soc_backend.incident.service.DetectionWorkFailureService;
import ai_soc_backend.incident.service.DetectionWorkProcessor;
import ai_soc_backend.incident.service.DetectionWorker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@EnabledIfSystemProperty(named = "aisoc.phase2.postgres-test", matches = "true")
@SpringBootTest(classes = DetectionWorkerPostgresTest.TestApplication.class)
class DetectionWorkerPostgresTest {
    private static final String URL = "jdbc:postgresql://127.0.0.1:55432/aisoc_test";
    private static final String USER = "aisoc_test";

    @DynamicPropertySource
    static void configureDisposableDatabase(DynamicPropertyRegistry properties) {
        String password = System.getenv("AISOC_V2_TEST_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalStateException("AISOC_V2_TEST_PASSWORD is required");
        try (var c = DriverManager.getConnection(URL, USER, password);
             var s = c.createStatement();
             var rs = s.executeQuery("SELECT current_database(), session_user, current_setting('server_version')")) {
            if (!rs.next() || !"aisoc_test".equals(rs.getString(1)) || !USER.equals(rs.getString(2))
                    || !rs.getString(3).startsWith("18.3")) {
                throw new IllegalStateException("Refusing worker test outside disposable aisoc_test PostgreSQL 18.3");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not verify disposable PostgreSQL target", e);
        }
        properties.add("spring.datasource.url", () -> URL);
        properties.add("spring.datasource.username", () -> USER);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.flyway.enabled", () -> "false");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        properties.add("spring.jpa.open-in-view", () -> "false");
    }

    @org.springframework.beans.factory.annotation.Autowired AlertPersistenceService persistence;
    @org.springframework.beans.factory.annotation.Autowired AlertRepository alerts;
    @org.springframework.beans.factory.annotation.Autowired AlertDetectionWorkRepository work;
    @org.springframework.beans.factory.annotation.Autowired DetectionWorkClaimService claims;
    @org.springframework.beans.factory.annotation.Autowired DetectionWorkFailureService failures;
    @org.springframework.beans.factory.annotation.Autowired DetectionWorkProcessor processor;
    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired PlatformTransactionManager tx;
    @MockitoSpyBean JdbcTemplate jdbcSpy;

    @Test
    void competingClaimersReceiveDifferentRowsAndExpiredLeaseRotatesToken() throws Exception {
        String prefix = "worker-claim-" + UUID.randomUUID();
        List<Long> ids = new ArrayList<>();
        var pool = Executors.newFixedThreadPool(2);
        try {
            ids.add(create(prefix + "-1", null).getId());
            ids.add(create(prefix + "-2", null).getId());
            makeAvailable(ids);
            assertNoOtherEligibleWork(ids);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var first = pool.submit(() -> claimAfterBarrier(ready, start));
            var second = pool.submit(() -> claimAfterBarrier(ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            DetectionWorkClaim a = first.get(10, TimeUnit.SECONDS);
            DetectionWorkClaim b = second.get(10, TimeUnit.SECONDS);
            assertNotEquals(a.alertPk(), b.alertPk());
            assertNotEquals(a.token(), b.token());

            jdbc.update("UPDATE public.alert_detection_work SET lease_expires_at=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE alert_pk=?", a.alertPk());
            DetectionWorkClaim reclaimed = claims.claim(1).getFirst();
            assertEquals(a.alertPk(), reclaimed.alertPk());
            assertNotEquals(a.token(), reclaimed.token());
            assertEquals(2, reclaimed.attempt());
            assertFalse(processor.process(a), "A stale claim token must not perform detection");
            failures.fail(a, new IllegalStateException("stale worker"));
            AlertDetectionWork stillOwned = work.findById(reclaimed.alertPk()).orElseThrow();
            assertEquals("IN_PROGRESS", stillOwned.getStatus());
            assertEquals(reclaimed.token(), stillOwned.getClaimToken());
            assertTrue(processor.process(reclaimed));

            long exhaustedId = b.alertPk();
            UUID exhaustedToken = UUID.randomUUID();
            jdbc.update("""
                    UPDATE public.alert_detection_work
                       SET status='IN_PROGRESS', attempt_count=5, claim_token=?,
                           lease_expires_at=CURRENT_TIMESTAMP - INTERVAL '1 second'
                     WHERE alert_pk=?
                    """, exhaustedToken, exhaustedId);
            assertTrue(claims.claim(1).isEmpty());
            AlertDetectionWork exhausted = work.findById(exhaustedId).orElseThrow();
            assertEquals("FAILED", exhausted.getStatus());
            assertEquals("LEASE_EXPIRED_ATTEMPT_EXHAUSTED", exhausted.getLastErrorCode());
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            cleanup(ids);
        }
    }

    @Test
    void retryBackoffAndFifthFailureEndInFailedState() {
        Alert saved = create("worker-retry-" + UUID.randomUUID(), null);
        Alert permanent = create("worker-permanent-" + UUID.randomUUID(), null);
        List<Long> ids = List.of(saved.getId(), permanent.getId());
        try {
            assertNoOtherEligibleWork(ids);
            makeAvailable(ids);
            jdbc.update("UPDATE public.alert_detection_work SET available_at=CURRENT_TIMESTAMP + INTERVAL '1 day' WHERE alert_pk=?", permanent.getId());
            for (short expectedAttempt = 1; expectedAttempt <= 5; expectedAttempt++) {
                jdbc.update("UPDATE public.alert_detection_work SET available_at=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE alert_pk=?", saved.getId());
                DetectionWorkClaim claim = claims.claim(1).stream()
                        .filter(c -> c.alertPk() == saved.getId()).findFirst().orElseThrow();
                assertEquals(expectedAttempt, claim.attempt());
                failures.fail(claim, new IllegalStateException("do not persist this message"));
                AlertDetectionWork current = work.findById(saved.getId()).orElseThrow();
                if (expectedAttempt < 5) {
                    assertEquals("PENDING", current.getStatus());
                    assertTrue(current.getAvailableAt().isAfter(Instant.now()));
                    Double delay = jdbc.queryForObject("SELECT EXTRACT(EPOCH FROM (available_at - updated_at)) " +
                            "FROM public.alert_detection_work WHERE alert_pk=?", Double.class, saved.getId());
                    assertEquals(1 << (expectedAttempt - 1), delay.intValue());
                } else {
                    assertEquals("FAILED", current.getStatus());
                    assertNotNull(current.getFinishedAt());
                    assertEquals("ATTEMPTS_EXHAUSTED", current.getLastErrorCode());
                }
                assertEquals("IllegalStateException", current.getLastErrorDetail());
            }
            makeAvailable(List.of(permanent.getId()));
            DetectionWorkClaim claim = claims.claim(1).stream()
                    .filter(c -> c.alertPk() == permanent.getId()).findFirst().orElseThrow();
            failures.fail(claim, new IllegalArgumentException("invalid state"));
            AlertDetectionWork permanentFailure = work.findById(permanent.getId()).orElseThrow();
            assertEquals("FAILED", permanentFailure.getStatus());
            assertEquals(1, permanentFailure.getAttemptCount());
            assertEquals("PERMANENT_PROCESSING_ERROR", permanentFailure.getLastErrorCode());
        } finally {
            cleanup(ids);
        }
    }

    @Test
    void legacyTimestampIsCompletedAsSkippedAndFiveAlertsAtomicallyCreateOneIncidentAndInvestigationWork() throws Exception {
        String prefix = "worker-correlation-" + UUID.randomUUID();
        Instant event = Instant.parse("2026-10-28T12:03:00Z");
        List<Long> ids = new ArrayList<>();
        try {
            Alert legacy = create(prefix + "-legacy", null);
            ids.add(legacy.getId());
            makeAvailable(List.of(legacy.getId()));
            assertNoOtherEligibleWork(ids);
            DetectionWorkClaim legacyClaim = claims.claim(1).stream().filter(c -> c.alertPk() == legacy.getId()).findFirst().orElseThrow();
            assertTrue(processor.process(legacyClaim));
            assertEquals("SKIPPED_LEGACY_TIMESTAMP", work.findById(legacy.getId()).orElseThrow().getResultCode());

            for (int i = 0; i < 5; i++) {
                Alert a = create(prefix + "-" + i, event);
                ids.add(a.getId());
            }
            makeAvailable(ids.subList(1, ids.size()));
            assertNoOtherEligibleWork(ids);
            List<DetectionWorkClaim> batch = claims.claim(10);
            List<DetectionWorkClaim> group = batch.stream().filter(c -> ids.contains(c.alertPk()) && c.alertPk() != legacy.getId()).toList();
            assertEquals(5, group.size());
            DetectionWorker worker = new DetectionWorker(claims, processor, failures);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var pool = Executors.newFixedThreadPool(2);
            try {
                String password = System.getenv("AISOC_V2_TEST_PASSWORD");
                try (var blocker = DriverManager.getConnection(URL, USER, password)) {
                    blocker.setAutoCommit(false);
                    try (var lock = blocker.createStatement()) {
                        lock.execute("LOCK TABLE public.incidents IN SHARE MODE");
                    }
                    var first = pool.submit(() -> processAfterBarrier(worker, group.get(0), ready, start));
                    var second = pool.submit(() -> processAfterBarrier(worker, group.get(1), ready, start));
                    assertTrue(ready.await(5, TimeUnit.SECONDS));
                    start.countDown();
                    awaitTwoBlockedIncidentInserts(password);
                    blocker.commit();
                    assertTrue(first.get(15, TimeUnit.SECONDS));
                    assertTrue(second.get(15, TimeUnit.SECONDS));
                }
            } finally {
                pool.shutdownNow();
                pool.awaitTermination(10, TimeUnit.SECONDS);
            }
            String host = "worker-host";
            long bucket = DetectionWorkProcessor.bucketStartEpoch(event.getEpochSecond());
            String key = DetectionWorkProcessor.correlationKey(host, bucket);
            Integer incidentCount = jdbc.queryForObject("SELECT count(*) FROM public.incidents WHERE correlation_key=?", Integer.class, key);
            assertEquals(1, incidentCount);
            Integer investigationRows = jdbc.queryForObject("""
                    SELECT count(*) FROM public.incident_investigation_work w
                      JOIN public.incidents i ON i.id=w.incident_pk WHERE i.correlation_key=?
                    """, Integer.class, key);
            assertEquals(1, investigationRows);
            for (DetectionWorkClaim claim : group.subList(0, 2)) {
                AlertDetectionWork completed = work.findById(claim.alertPk()).orElseThrow();
                assertEquals("COMPLETED", completed.getStatus());
                assertTrue(List.of("INCIDENT_CREATED", "INCIDENT_REUSED_ACTIVE").contains(completed.getResultCode()));
            }
        } finally {
            cleanup(ids, DetectionWorkProcessor.correlationKey("worker-host",
                    DetectionWorkProcessor.bucketStartEpoch(Instant.parse("2026-10-28T12:03:00Z").getEpochSecond())));
        }
    }

    @Test
    void reusesClosedCorrelationIncidentWithoutReopeningOrEnqueueingInvestigation() {
        String prefix = "worker-closed-" + UUID.randomUUID();
        Instant event = Instant.parse("2026-10-28T12:03:00Z");
        long bucket = DetectionWorkProcessor.bucketStartEpoch(event.getEpochSecond());
        String key = DetectionWorkProcessor.correlationKey("worker-host", bucket);
        List<Long> ids = new ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) ids.add(create(prefix + "-" + i, event).getId());
            makeAvailable(ids);
            assertNoOtherEligibleWork(ids);
            jdbc.update("""
                    INSERT INTO public.incidents
                        (incident_id, title, severity, status, host, detection, timestamp, correlation_key)
                    VALUES (?, 'already closed', 'HIGH', 'CLOSED', 'worker-host',
                            'Multiple Failed Logins', ?, ?)
                    """, prefix + "-incident", LocalDateTime.ofInstant(event, java.time.ZoneOffset.UTC), key);
            Long incidentPk = jdbc.queryForObject("SELECT id FROM public.incidents WHERE correlation_key=?", Long.class, key);
            DetectionWorkClaim claim = claims.claim(1).stream()
                    .filter(c -> ids.contains(c.alertPk())).findFirst().orElseThrow();

            assertTrue(processor.process(claim));
            assertEquals("CLOSED", jdbc.queryForObject("SELECT status FROM public.incidents WHERE id=?", String.class, incidentPk));
            assertEquals("INCIDENT_REUSED_CLOSED", work.findById(claim.alertPk()).orElseThrow().getResultCode());
            assertEquals("NO_INVESTIGATION_REQUIRED", jdbc.queryForObject(
                    "SELECT result_code FROM public.incident_investigation_work WHERE incident_pk=?", String.class, incidentPk));
        } finally {
            cleanup(ids, key);
        }
    }

    @Test
    void failedInvestigationEnqueueRollsBackIncidentAndDetectionCompletionTogether() {
        String prefix = "worker-atomic-" + UUID.randomUUID();
        Instant event = Instant.parse("2026-10-28T12:03:00Z");
        String key = DetectionWorkProcessor.correlationKey("worker-host",
                DetectionWorkProcessor.bucketStartEpoch(event.getEpochSecond()));
        List<Long> ids = new ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) ids.add(create(prefix + "-" + i, event).getId());
            makeAvailable(ids);
            assertNoOtherEligibleWork(ids);
            DetectionWorkClaim claim = claims.claim(1).getFirst();
            doThrow(new org.springframework.dao.DataIntegrityViolationException("simulated enqueue failure"))
                    .when(jdbcSpy).update(startsWith("INSERT INTO public.incident_investigation_work"), any(Object[].class));

            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> processor.process(claim));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.incidents WHERE correlation_key=?", Integer.class, key));
            AlertDetectionWork pendingCompletion = work.findById(claim.alertPk()).orElseThrow();
            assertEquals("IN_PROGRESS", pendingCompletion.getStatus());
            assertEquals(claim.token(), pendingCompletion.getClaimToken());
            assertEquals(0, jdbc.queryForObject("""
                    SELECT count(*) FROM public.incident_investigation_work w
                      JOIN public.incidents i ON i.id=w.incident_pk WHERE i.correlation_key=?
                    """, Integer.class, key));
        } finally {
            reset(jdbcSpy);
            cleanup(ids, key);
        }
    }

    private DetectionWorkClaim claimAfterBarrier(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("barrier timed out");
        return claims.claim(1).getFirst();
    }

    private boolean processAfterBarrier(DetectionWorker worker, DetectionWorkClaim claim,
                                        CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("barrier timed out");
        worker.processClaim(claim);
        return "COMPLETED".equals(work.findById(claim.alertPk()).orElseThrow().getStatus());
    }

    private void awaitTwoBlockedIncidentInserts(String password) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        int blocked = 0;
        while (System.nanoTime() < deadline) {
            try (var monitor = DriverManager.getConnection(URL, USER, password);
                 var statement = monitor.createStatement();
                 var result = statement.executeQuery("""
                         SELECT count(*) FROM pg_stat_activity
                          WHERE datname=current_database() AND usename=current_user
                            AND pid<>pg_backend_pid() AND wait_event_type='Lock'
                            AND query ILIKE 'insert%into%public.incidents%'
                         """)) {
                result.next();
                blocked = result.getInt(1);
            }
            if (blocked >= 2) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Expected two concurrent incident inserts to wait on the table lock; observed " + blocked);
    }

    private Alert create(String id, Instant eventTime) {
        Alert alert = new Alert(id, "TEST", "FAILED_LOGON", "LOW", "worker-host", "test",
                LocalDateTime.parse("2026-10-28T12:00:00"));
        alert.setEventTimestampUtc(eventTime);
        return persistence.saveNewAlertWithDetectionWork(alert);
    }

    private void makeAvailable(List<Long> ids) {
        for (Long id : ids) {
            jdbc.update("UPDATE public.alert_detection_work SET available_at=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE alert_pk=?", id);
        }
    }

    private void assertNoOtherEligibleWork(List<Long> ourIds) {
        String placeholders = String.join(",", java.util.Collections.nCopies(ourIds.size(), "?"));
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM public.alert_detection_work
                 WHERE ((status='PENDING' AND available_at <= CURRENT_TIMESTAMP)
                    OR (status='IN_PROGRESS' AND lease_expires_at <= CURRENT_TIMESTAMP))
                   AND alert_pk NOT IN (%s)
                """.formatted(placeholders), Integer.class, ourIds.toArray());
        if (count != null && count != 0) {
            throw new IllegalStateException("Refusing worker integration test because unrelated eligible work exists");
        }
    }

    private void cleanup(List<Long> alertIds) {
        cleanup(alertIds, null);
    }

    private void cleanup(List<Long> alertIds, String correlationKey) {
        new TransactionTemplate(tx).executeWithoutResult(status -> {
            if (correlationKey != null) {
                jdbc.update("DELETE FROM public.incident_investigation_work WHERE incident_pk IN " +
                        "(SELECT id FROM public.incidents WHERE correlation_key=?)", correlationKey);
                jdbc.update("DELETE FROM public.incidents WHERE correlation_key=?", correlationKey);
            }
            for (Long id : alertIds) {
                jdbc.update("DELETE FROM public.alert_detection_work WHERE alert_pk=?", id);
                jdbc.update("DELETE FROM public.alerts WHERE id=?", id);
            }
        });
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("ai_soc_backend.incident.entity")
    @EnableJpaRepositories("ai_soc_backend.incident.repository")
    @Import({AlertPersistenceService.class, DetectionWorkClaimService.class,
            DetectionWorkFailureService.class, DetectionWorkProcessor.class})
    static class TestApplication { }
}
