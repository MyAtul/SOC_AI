package ai_soc_backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.mock.env.MockEnvironment;

import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PipelineTestSafetyConfigurationTest {
    @Test
    void acceptsOnlyTheExplicitDisposableTarget() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", PipelineTestSafetyConfiguration.EXPECTED_URL)
                .withProperty("spring.datasource.username", PipelineTestSafetyConfiguration.EXPECTED_USER)
                .withProperty("spring.datasource.password", "test-password")
                .withProperty("spring.flyway.url", PipelineTestSafetyConfiguration.EXPECTED_URL)
                .withProperty("spring.flyway.user", PipelineTestSafetyConfiguration.EXPECTED_USER)
                .withProperty("spring.flyway.password", "test-password")
                .withProperty("spring.flyway.enabled", "true")
                .withProperty("spring.flyway.baseline-on-migrate", "false")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.jpa.properties.hibernate.jdbc.time_zone", PipelineTestSafetyConfiguration.EXPECTED_TIME_ZONE)
                .withProperty("aisoc.detection-worker.enabled", "true")
                .withProperty("aisoc.investigation-worker.enabled", "false")
                .withProperty("server.address", "127.0.0.1")
                .withProperty("server.port", "8082");

        assertDoesNotThrow(() -> PipelineTestSafetyConfiguration.validateTarget(environment));
    }

    @Test
    void rejectsLiveAndAnyOtherDatasourceBeforeConnection() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/aisoc")
                .withProperty("spring.datasource.username", "postgres")
                .withProperty("spring.datasource.password", "test-password")
                .withProperty("spring.flyway.url", PipelineTestSafetyConfiguration.EXPECTED_URL)
                .withProperty("spring.flyway.user", PipelineTestSafetyConfiguration.EXPECTED_USER)
                .withProperty("spring.flyway.password", "test-password")
                .withProperty("spring.flyway.enabled", "true")
                .withProperty("spring.flyway.baseline-on-migrate", "false")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.jpa.properties.hibernate.jdbc.time_zone", PipelineTestSafetyConfiguration.EXPECTED_TIME_ZONE)
                .withProperty("aisoc.detection-worker.enabled", "true")
                .withProperty("aisoc.investigation-worker.enabled", "false")
                .withProperty("server.address", "127.0.0.1")
                .withProperty("server.port", "8082");

        assertThrows(BeanInitializationException.class,
                () -> PipelineTestSafetyConfiguration.validateTarget(environment));
    }

    @Test
    void rejectsMissingTestPassword() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", PipelineTestSafetyConfiguration.EXPECTED_URL)
                .withProperty("spring.datasource.username", PipelineTestSafetyConfiguration.EXPECTED_USER)
                .withProperty("spring.flyway.url", PipelineTestSafetyConfiguration.EXPECTED_URL)
                .withProperty("spring.flyway.user", PipelineTestSafetyConfiguration.EXPECTED_USER)
                .withProperty("spring.flyway.password", "test-password")
                .withProperty("spring.flyway.enabled", "true")
                .withProperty("spring.flyway.baseline-on-migrate", "false")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.jpa.properties.hibernate.jdbc.time_zone", PipelineTestSafetyConfiguration.EXPECTED_TIME_ZONE)
                .withProperty("aisoc.detection-worker.enabled", "true")
                .withProperty("aisoc.investigation-worker.enabled", "false")
                .withProperty("server.address", "127.0.0.1")
                .withProperty("server.port", "8082");

        assertThrows(BeanInitializationException.class,
                () -> PipelineTestSafetyConfiguration.validateTarget(environment));
    }

    @Test
    void rejectsFlywayOverridesAndUnsafeSchemaManagement() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", PipelineTestSafetyConfiguration.EXPECTED_URL)
                .withProperty("spring.datasource.username", PipelineTestSafetyConfiguration.EXPECTED_USER)
                .withProperty("spring.datasource.password", "test-password")
                .withProperty("spring.flyway.url", "jdbc:postgresql://localhost:5432/aisoc")
                .withProperty("spring.flyway.user", PipelineTestSafetyConfiguration.EXPECTED_USER)
                .withProperty("spring.flyway.password", "test-password")
                .withProperty("spring.flyway.enabled", "true")
                .withProperty("spring.flyway.baseline-on-migrate", "true")
                .withProperty("spring.jpa.hibernate.ddl-auto", "update")
                .withProperty("spring.jpa.properties.hibernate.jdbc.time_zone", PipelineTestSafetyConfiguration.EXPECTED_TIME_ZONE)
                .withProperty("aisoc.detection-worker.enabled", "true")
                .withProperty("aisoc.investigation-worker.enabled", "false")
                .withProperty("server.address", "127.0.0.1")
                .withProperty("server.port", "8082");

        assertThrows(BeanInitializationException.class,
                () -> PipelineTestSafetyConfiguration.validateTarget(environment));
    }

    @Test
    void rejectsWrongTestPortBindAddressOrEnabledInvestigationWorker() {
        MockEnvironment wrongPort = validEnvironment().withProperty("server.port", "8081");
        assertThrows(BeanInitializationException.class,
                () -> PipelineTestSafetyConfiguration.validateTarget(wrongPort));

        MockEnvironment wrongBindAddress = validEnvironment().withProperty("server.address", "0.0.0.0");
        assertThrows(BeanInitializationException.class,
                () -> PipelineTestSafetyConfiguration.validateTarget(wrongBindAddress));

        MockEnvironment investigationEnabled = validEnvironment()
                .withProperty("aisoc.investigation-worker.enabled", "true");
        assertThrows(BeanInitializationException.class,
                () -> PipelineTestSafetyConfiguration.validateTarget(investigationEnabled));
    }

    @Test
    void rejectsNonCanonicalHibernateJdbcTimezone() {
        MockEnvironment wrongTimeZone = validEnvironment()
                .withProperty("spring.jpa.properties.hibernate.jdbc.time_zone", "Asia/Calcutta");
        assertThrows(BeanInitializationException.class,
                () -> PipelineTestSafetyConfiguration.validateTarget(wrongTimeZone));
    }

    private static MockEnvironment validEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", PipelineTestSafetyConfiguration.EXPECTED_URL)
                .withProperty("spring.datasource.username", PipelineTestSafetyConfiguration.EXPECTED_USER)
                .withProperty("spring.datasource.password", "test-password")
                .withProperty("spring.flyway.url", PipelineTestSafetyConfiguration.EXPECTED_URL)
                .withProperty("spring.flyway.user", PipelineTestSafetyConfiguration.EXPECTED_USER)
                .withProperty("spring.flyway.password", "test-password")
                .withProperty("spring.flyway.enabled", "true")
                .withProperty("spring.flyway.baseline-on-migrate", "false")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.jpa.properties.hibernate.jdbc.time_zone", PipelineTestSafetyConfiguration.EXPECTED_TIME_ZONE)
                .withProperty("aisoc.detection-worker.enabled", "true")
                .withProperty("aisoc.investigation-worker.enabled", "false")
                .withProperty("server.address", "127.0.0.1")
                .withProperty("server.port", "8082");
    }

    @Test
    void canonicalizesJvmTimezoneForPipelineTestConnections() {
        String previousProperty = System.getProperty("user.timezone");
        TimeZone previousTimeZone = TimeZone.getDefault();
        try {
            System.setProperty("user.timezone", "Asia/Calcutta");
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Calcutta"));

            PipelineTestSafetyConfiguration.configurePipelineTestTimeZone();

            assertEquals("Asia/Kolkata", System.getProperty("user.timezone"));
            assertEquals("Asia/Kolkata", TimeZone.getDefault().getID());
            assertEquals("Asia/Kolkata", ZoneId.systemDefault().getId());
        } finally {
            if (previousProperty == null) {
                System.clearProperty("user.timezone");
            } else {
                System.setProperty("user.timezone", previousProperty);
            }
            TimeZone.setDefault(previousTimeZone);
        }
    }
}
