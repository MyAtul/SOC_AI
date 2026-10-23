package ai_soc_backend.incident.service;

import ai_soc_backend.incident.entity.Alert;
import ai_soc_backend.incident.entity.Incident;

public interface DetectionService {

    Incident evaluate(Alert alert);
}