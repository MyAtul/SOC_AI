package ai_soc_backend.incident.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class AttackScenarioCatalogTest {
    @Test
    void marksWindows4625ThresholdAsImplementedWithoutCallingItSshOrPowerShell() {
        var scenario = AttackScenarioCatalog.forDetection("Multiple Failed Logins").orElseThrow();
        assertEquals("IMPLEMENTED_DETECTION", scenario.coverage());
        assertTrue(scenario.name().contains("Windows"));
        assertTrue(scenario.coverageDescription().contains("not protocol or intent"));
        assertFalse(scenario.name().contains("SSH"));
        assertFalse(scenario.name().contains("PowerShell"));
    }

    @Test
    void keepsRequestedPlatformScenariosKnowledgeBaseOnly() {
        var ssh = AttackScenarioCatalog.byId("ssh-brute-force").orElseThrow();
        assertEquals("KNOWLEDGE_BASE_ONLY", ssh.coverage());
        assertIterableEquals(java.util.List.of("T1110", "T1110.001", "T1110.002", "T1110.003", "T1110.004"),
                ssh.references().stream().map(AttackTechniqueReference::id).toList());
        assertEquals("KNOWLEDGE_BASE_ONLY", AttackScenarioCatalog.byId("suspicious-powershell-execution").orElseThrow().coverage());
        var linux = AttackScenarioCatalog.byId("linux-privilege-escalation").orElseThrow();
        assertEquals("KNOWLEDGE_BASE_ONLY", linux.coverage());
        assertTrue(linux.references().stream().anyMatch(ref -> ref.id().equals("CVE-2016-5195")));
        assertTrue(linux.references().stream().anyMatch(ref -> ref.id().equals("CVE-2022-0847")));
    }
}
