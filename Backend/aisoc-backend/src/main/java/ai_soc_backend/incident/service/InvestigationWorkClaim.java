package ai_soc_backend.incident.service;

import java.util.UUID;

public record InvestigationWorkClaim(long incidentPk, UUID token, short attempt) { }
