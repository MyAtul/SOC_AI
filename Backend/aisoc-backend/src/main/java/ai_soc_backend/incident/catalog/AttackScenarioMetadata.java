package ai_soc_backend.incident.catalog;

import java.util.List;

public record AttackScenarioMetadata(
        String id,
        String name,
        String coverage,
        String coverageDescription,
        List<AttackTechniqueReference> references
) {
    public AttackScenarioMetadata {
        references = List.copyOf(references);
    }
}
