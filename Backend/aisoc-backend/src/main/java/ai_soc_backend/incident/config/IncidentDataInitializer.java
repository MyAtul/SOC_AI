package ai_soc_backend.incident.config;

import ai_soc_backend.incident.entity.Incident;
import ai_soc_backend.incident.repository.IncidentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class IncidentDataInitializer {

    @Bean
    CommandLineRunner initializeIncidents(IncidentRepository incidentRepository) {

        return args -> {

            if (incidentRepository.count() > 0) {
                return;
            }

            Incident incident1 = new Incident(
                    "INC-001",
                    "Suspicious PowerShell Activity",
                    "HIGH",
                    "INVESTIGATING",
                    "WIN-CLIENT-01",
                    "Suspicious PowerShell",
                    LocalDateTime.parse("2026-09-20T17:30:00")
            );

            Incident incident2 = new Incident(
                    "INC-002",
                    "Multiple Failed Logins",
                    "MEDIUM",
                    "OPEN",
                    "SERVER-01",
                    "Brute Force Detection",
                    LocalDateTime.parse("2026-09-20T16:45:00")
            );

            Incident incident3 = new Incident(
                    "INC-003",
                    "Privilege Escalation",
                    "CRITICAL",
                    "AWAITING_APPROVAL",
                    "SERVER-02",
                    "Privilege Escalation Detection",
                    LocalDateTime.parse("2026-09-20T15:20:00")
            );

            incidentRepository.save(incident1);
            incidentRepository.save(incident2);
            incidentRepository.save(incident3);
        };
    }
}