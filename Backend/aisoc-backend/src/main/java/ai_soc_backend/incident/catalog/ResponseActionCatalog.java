package ai_soc_backend.incident.catalog;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Action descriptions only. No action in this catalogue executes remediation. */
public final class ResponseActionCatalog {
    private static final List<ResponseActionMetadata> ACTIONS = List.of(
            action("block_ip", "Block IP address", "Simulated network deny-list recommendation; current mock policy may allow a medium-risk request without a decision.", false),
            action("disable_account", "Disable account", "Simulated account-access restriction recommendation.", true),
            action("isolate_host", "Isolate host", "Simulated host-isolation recommendation.", true),
            action("kill_process", "Terminate process", "Simulated process-termination recommendation.", true),
            action("collect_logs", "Collect logs", "Simulated evidence-collection recommendation; review data scope first.", true),
            action("tag_ioc", "Tag indicator of compromise", "Simulated IOC tagging recommendation.", false),
            action("notify_analyst", "Notify analyst", "Simulated analyst-notification recommendation.", false),
            action("force_password_reset", "Force password reset", "Simulated credential-reset recommendation.", true),
            action("revoke_ssh_keys", "Revoke SSH keys", "Simulated SSH-key revocation recommendation.", true),
            action("audit_suid_sudo", "Audit SUID and sudo configuration", "Simulated read-only audit recommendation.", false),
            action("remove_persistence", "Remove persistence", "Simulated persistence-removal recommendation.", true),
            action("patch_vulnerabilities", "Patch vulnerabilities", "Simulated patch recommendation; no changes are applied.", true),
            action("review_manually", "Review manually", "Manual review recommendation; no automated action.", false)
    );

    private ResponseActionCatalog() { }

    public static List<ResponseActionMetadata> all() { return ACTIONS; }

    public static Optional<ResponseActionMetadata> find(String action) {
        if (action == null) return Optional.empty();
        String normalized = action.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (normalized.equals("blocksourceip")) return ACTIONS.stream().filter(candidate -> candidate.id().equals("block_ip")).findFirst();
        return ACTIONS.stream().filter(candidate -> candidate.id().replaceAll("[^A-Za-z0-9]", "")
                .equalsIgnoreCase(normalized) || candidate.name().replaceAll("[^A-Za-z0-9]", "")
                .equalsIgnoreCase(normalized)).findFirst();
    }

    private static ResponseActionMetadata action(String id, String name, String description, boolean approval) {
        return new ResponseActionMetadata(id, name, description, approval, "SIMULATED_ONLY");
    }
}
