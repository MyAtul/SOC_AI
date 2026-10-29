package ai_soc_backend.incident.catalog;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Curated references; coverage labels describe this repository's code, not ATT&CK itself. */
public final class AttackScenarioCatalog {
    public static final String IMPLEMENTED = "IMPLEMENTED_DETECTION";
    public static final String PARTIAL = "PARTIAL_DETECTION";
    public static final String KNOWLEDGE_ONLY = "KNOWLEDGE_BASE_ONLY";

    private static final AttackScenarioMetadata WINDOWS_FAILED_LOGON = new AttackScenarioMetadata(
            "windows-failed-logon-pattern", "Repeated Windows failed-logon pattern", PARTIAL,
            "The monitor ingests Windows Security Event ID 4625 and the backend correlates repeated failed logons. " +
                    "This does not establish SSH, password spraying, credential stuffing, or a specific source protocol.",
            List.of(attack("T1110", "Brute Force")));
    private static final AttackScenarioMetadata WINDOWS_4625_THRESHOLD = new AttackScenarioMetadata(
            "windows-4625-threshold", "Windows failed-logon threshold", IMPLEMENTED,
            "The monitor reads Security Event ID 4625 and the backend detects a threshold of repeated failures " +
                    "for one host within its configured time window. This detects an event pattern, not protocol or intent.",
            List.of());
    private static final List<AttackScenarioMetadata> SCENARIOS = List.of(
            WINDOWS_4625_THRESHOLD,
            WINDOWS_FAILED_LOGON,
            new AttackScenarioMetadata("ssh-brute-force", "SSH Brute Force", KNOWLEDGE_ONLY,
                    "Knowledge-base references only. No SSH authentication telemetry or SSH-specific detector is implemented.",
                    List.of(attack("T1110", "Brute Force"),
                            attack("T1110.001", "Password Guessing"),
                            attack("T1110.002", "Password Cracking"),
                            attack("T1110.003", "Password Spraying"),
                            attack("T1110.004", "Credential Stuffing"))),
            new AttackScenarioMetadata("suspicious-powershell-execution", "Suspicious PowerShell Execution", KNOWLEDGE_ONLY,
                    "Knowledge-base reference only. The current monitor collects Event ID 4625, not process or PowerShell telemetry.",
                    List.of(attack("T1059.001", "PowerShell"))),
            new AttackScenarioMetadata("linux-privilege-escalation", "Linux Privilege Escalation", KNOWLEDGE_ONLY,
                    "Knowledge-base references only. No Linux host telemetry or Linux privilege-escalation detector is implemented.",
                    List.of(attack("T1068", "Exploitation for Privilege Escalation"),
                            attack("T1548.001", "Setuid and Setgid"),
                            attack("T1548.003", "Sudo and Sudo Caching"),
                            attack("T1611", "Escape to Host"),
                            new AttackTechniqueReference("CVE-2016-5195", "Dirty COW", "VULNERABILITY",
                                    "https://nvd.nist.gov/vuln/detail/CVE-2016-5195"),
                            new AttackTechniqueReference("CVE-2022-0847", "Dirty Pipe", "VULNERABILITY",
                                    "https://nvd.nist.gov/vuln/detail/CVE-2022-0847")))
    );

    private AttackScenarioCatalog() { }

    public static List<AttackScenarioMetadata> all() {
        return SCENARIOS;
    }

    public static Optional<AttackScenarioMetadata> byId(String id) {
        return SCENARIOS.stream().filter(scenario -> scenario.id().equals(id)).findFirst();
    }

    public static Optional<AttackScenarioMetadata> forDetection(String detection) {
        if (detection == null) return Optional.empty();
        String normalized = detection.toLowerCase(Locale.ROOT);
        if (normalized.contains("failed logon") || normalized.contains("failed login")) {
            return Optional.of(WINDOWS_4625_THRESHOLD);
        }
        if (normalized.contains("suspicious powershell")) {
            return byId("suspicious-powershell-execution");
        }
        return Optional.empty();
    }

    private static AttackTechniqueReference attack(String id, String name) {
        String[] parts = id.split("\\.");
        String path = parts[0] + (parts.length == 2 ? "/" + parts[1] : "");
        return new AttackTechniqueReference(id, name, "ATT&CK",
                "https://attack.mitre.org/techniques/" + path + "/");
    }
}
