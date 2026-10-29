package ai_soc_backend.incident.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "aisoc.detection-worker", name = "enabled", havingValue = "true")
public class DetectionWorkerSchedulingConfiguration { }
