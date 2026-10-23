package ai_soc_backend.incident.service;

import ai_soc_backend.incident.entity.Alert;

public interface AlertService {

    Alert processAlert(Alert alert);
}