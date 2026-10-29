export interface IncidentStatusEntry {
  incidentId: string;
  status: string;
}

export function applyApprovalStatus<T extends IncidentStatusEntry>(
  incidents: readonly T[],
  incidentId: string,
  status: string,
): T[] {
  if (status !== "APPROVED" && status !== "DENIED") return [...incidents];

  return incidents.map((incident) =>
    incident.incidentId === incidentId ? { ...incident, status } : incident,
  );
}
