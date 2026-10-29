package ai_soc_backend.incident.service;

import java.util.UUID;

public record DetectionWorkClaim(long alertPk, UUID token, short attempt) { }
