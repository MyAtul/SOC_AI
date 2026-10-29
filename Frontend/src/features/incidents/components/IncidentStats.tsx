import type { Incident } from "../types/incident";
import { StatCard } from "./IncidentDetails";

export function IncidentStats({ incidents }: { incidents: Incident[] }) {
  const activeStatuses = new Set(["OPEN", "INVESTIGATING", "AWAITING_APPROVAL"]);
  const active = incidents.filter((incident) => activeStatuses.has(incident.status)).length;
  const critical = incidents.filter((incident) => incident.severity === "CRITICAL").length;
  const high = incidents.filter((incident) => incident.severity === "HIGH").length;
  const awaitingApproval = incidents.filter((incident) => incident.status === "AWAITING_APPROVAL").length;

  return (
    <div className="grid grid-cols-2 gap-2.5 lg:grid-cols-4">
      <StatCard title="Active Incidents" value={active} />
      <StatCard title="Critical" value={critical} />
      <StatCard title="High Severity" value={high} />
      <StatCard title="Awaiting Approval" value={awaitingApproval} />
    </div>
  );
}

