package ai_soc_backend.config;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.util.TimeZone;

/** Fail closed before datasource, Flyway, or Hibernate beans can connect. */
@Configuration(proxyBeanMethods = false)
@Profile("pipeline-test")
public class PipelineTestSafetyConfiguration {
    static final String EXPECTED_URL = "jdbc:postgresql://127.0.0.1:55432/aisoc_test";
    static final String EXPECTED_USER = "aisoc_test";
    static final String EXPECTED_TIME_ZONE = "Asia/Kolkata";

    @Bean
    static BeanFactoryPostProcessor requireIsolatedPipelineTestTarget(Environment environment) {
        return beanFactory -> {
            validateTarget(environment);
            configurePipelineTestTimeZone();
        };
    }

    static void configurePipelineTestTimeZone() {
        // pgJDBC sends TimeZone.getDefault() in its startup packet for both the
        // application datasource and Flyway's datasource.
        System.setProperty("user.timezone", EXPECTED_TIME_ZONE);
        TimeZone.setDefault(TimeZone.getTimeZone(EXPECTED_TIME_ZONE));
    }

    static void validateTarget(Environment environment) {
        String url = environment.getProperty("spring.datasource.url");
        String user = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        String flywayUrl = environment.getProperty("spring.flyway.url");
        String flywayUser = environment.getProperty("spring.flyway.user");
        String flywayPassword = environment.getProperty("spring.flyway.password");
        if (!EXPECTED_URL.equals(url) || !EXPECTED_USER.equals(user)
                || password == null || password.isBlank()
                || !EXPECTED_URL.equals(flywayUrl) || !EXPECTED_USER.equals(flywayUser)
                || !password.equals(flywayPassword)
                || !"true".equalsIgnoreCase(environment.getProperty("spring.flyway.enabled"))
                || !"false".equalsIgnoreCase(environment.getProperty("spring.flyway.baseline-on-migrate"))
                || !"validate".equalsIgnoreCase(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                || !EXPECTED_TIME_ZONE.equals(environment.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone"))
                || !"true".equalsIgnoreCase(environment.getProperty("aisoc.detection-worker.enabled"))
                || !"false".equalsIgnoreCase(environment.getProperty("aisoc.investigation-worker.enabled"))
                || !"127.0.0.1".equals(environment.getProperty("server.address"))
                || !"8082".equals(environment.getProperty("server.port"))) {
            throw new BeanInitializationException(
                    "pipeline-test refuses startup unless its fixed datasource, Flyway, Hibernate, worker, and local server settings are intact");
        }
    }
}
