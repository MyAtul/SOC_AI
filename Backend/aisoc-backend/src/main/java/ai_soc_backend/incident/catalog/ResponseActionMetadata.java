package ai_soc_backend.incident.catalog;

public record ResponseActionMetadata(
        String id,
        String name,
        String description,
        boolean approvalRequired,
        String executionMode
) {
}
