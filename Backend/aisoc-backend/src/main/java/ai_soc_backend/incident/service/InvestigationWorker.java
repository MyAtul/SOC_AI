package ai_soc_backend.incident.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Enable only after reconciling legacy investigation work against existing persisted investigations. */
@Component
@ConditionalOnProperty(prefix = "aisoc.investigation-worker", name = "enabled", havingValue = "true")
public class InvestigationWorker {
    private static final Logger log = LoggerFactory.getLogger(InvestigationWorker.class);
    private final InvestigationWorkClaimService claims;
    private final InvestigationWorkProcessor processor;
    private final InvestigationWorkFailureService failures;

    public InvestigationWorker(InvestigationWorkClaimService claims, InvestigationWorkProcessor processor,
                               InvestigationWorkFailureService failures) {
        this.claims = claims;
        this.processor = processor;
        this.failures = failures;
    }

    @Scheduled(fixedDelayString = "${aisoc.investigation-worker.poll-delay-ms:1000}")
    public void poll() {
        for (InvestigationWorkClaim claim : claims.claim(5)) {
            try {
                processor.process(claim);
            } catch (RuntimeException failure) {
                try {
                    failures.fail(claim, failure);
                } catch (RuntimeException updateFailure) {
                    log.error("Could not record investigation-work failure for incidentPk={}, errorType={}",
                            claim.incidentPk(), updateFailure.getClass().getSimpleName());
                }
            }
        }
    }
}
