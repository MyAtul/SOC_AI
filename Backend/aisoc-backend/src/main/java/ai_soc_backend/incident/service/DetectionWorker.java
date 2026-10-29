package ai_soc_backend.incident.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Enable only after manually reconciling legacy PENDING work against existing incidents. */
@Component
@ConditionalOnProperty(prefix = "aisoc.detection-worker", name = "enabled", havingValue = "true")
public class DetectionWorker {
    private static final Logger log = LoggerFactory.getLogger(DetectionWorker.class);
    private final DetectionWorkClaimService claims;
    private final DetectionWorkProcessor processor;
    private final DetectionWorkFailureService failures;

    public DetectionWorker(DetectionWorkClaimService claims, DetectionWorkProcessor processor,
                           DetectionWorkFailureService failures) {
        this.claims = claims;
        this.processor = processor;
        this.failures = failures;
    }

    @Scheduled(fixedDelayString = "${aisoc.detection-worker.poll-delay-ms:1000}")
    public void poll() {
        for (DetectionWorkClaim claim : claims.claim(10)) processClaim(claim);
    }

    /** Retry only a correlation unique race, after the failed processor transaction has rolled back. */
    public void processClaim(DetectionWorkClaim claim) {
        RuntimeException failure;
        try {
            processor.process(claim);
            return;
        } catch (CorrelationKeyConflictException conflict) {
            try {
                processor.process(claim); // New proxied call/transaction; rechecks the current token.
                return;
            } catch (RuntimeException retryFailure) {
                failure = retryFailure;
            }
        } catch (RuntimeException processingFailure) {
            failure = processingFailure;
        }
        try {
            failures.fail(claim, failure);
        } catch (RuntimeException updateFailure) {
            log.error("Could not record detection-work failure for alertPk={}, errorType={}",
                    claim.alertPk(), updateFailure.getClass().getSimpleName());
        }
    }
}
