package ai_soc_backend.incident.controller;

import ai_soc_backend.incident.entity.Alert;
import ai_soc_backend.incident.service.AlertService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = "http://localhost:5173")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping
    public Alert receiveAlert(@RequestBody Alert alert) {
        return alertService.processAlert(alert);
    }
}