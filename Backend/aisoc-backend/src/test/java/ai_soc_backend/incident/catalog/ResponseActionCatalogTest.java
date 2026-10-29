package ai_soc_backend.incident.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseActionCatalogTest {
    @Test
    void exposesRequestedActionsAsSimulationsAndRequiresApprovalForDisruptiveChanges() {
        assertEquals(13, ResponseActionCatalog.all().size());
        assertTrue(ResponseActionCatalog.all().stream().allMatch(action -> action.executionMode().equals("SIMULATED_ONLY")));
        assertTrue(ResponseActionCatalog.find("ISOLATE_HOST").orElseThrow().approvalRequired());
        assertTrue(ResponseActionCatalog.find("remove_persistence").orElseThrow().approvalRequired());
        assertTrue(ResponseActionCatalog.find("notify_analyst").isPresent());
        assertTrue(ResponseActionCatalog.all().stream().map(ResponseActionMetadata::id).toList().containsAll(java.util.List.of(
                "block_ip", "disable_account", "isolate_host", "kill_process", "collect_logs", "tag_ioc",
                "notify_analyst", "force_password_reset", "revoke_ssh_keys", "audit_suid_sudo",
                "remove_persistence", "patch_vulnerabilities")));
    }
}
