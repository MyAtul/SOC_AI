package ai_soc_backend.incident.controller;

import ai_soc_backend.incident.catalog.AttackScenarioCatalog;
import ai_soc_backend.incident.catalog.ResponseActionCatalog;
import ai_soc_backend.incident.catalog.AttackScenarioMetadata;
import ai_soc_backend.incident.catalog.ResponseActionMetadata;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
@CrossOrigin(origins = "http://localhost:5173")
public class CatalogController {
    @GetMapping("/attack-scenarios")
    public List<AttackScenarioMetadata> attackScenarios() { return AttackScenarioCatalog.all(); }

    @GetMapping("/response-actions")
    public List<ResponseActionMetadata> responseActions() { return ResponseActionCatalog.all(); }
}
