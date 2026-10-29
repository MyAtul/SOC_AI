import type { Incident } from "../types/incident";
import { SeverityBadge, StatusBadge } from "./IncidentDetails";

export function IncidentList({ incidents, loading, error, onRetry, onRefresh, onSelect, title = "Recent Incidents" }: {
  incidents: Incident[];
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  onRefresh: () => void;
  onSelect: (incident: Incident) => void;
  title?: string;
}) {
  return <section className="mt-5 overflow-hidden rounded-lg border border-slate-800 bg-slate-900">
    <div className="flex items-center justify-between border-b border-slate-800 px-4 py-3 md:px-5">
      <div><h3 className="text-sm font-semibold tracking-wide">{title}</h3><p className="mt-0.5 text-xs text-slate-400">Security incidents requiring SOC review.</p></div>
      <div className="flex items-center gap-3">
        <span className="text-xs text-slate-400">{loading ? "Loading" : `${incidents.length} records`}</span>
        {!error && <button type="button" onClick={onRefresh} disabled={loading} className="rounded-md border border-slate-700 px-3 py-1.5 text-xs text-slate-300 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-400">{loading ? "Refreshing…" : "Refresh"}</button>}
      </div>
    </div>
    {loading ? <div className="p-6 text-center text-sm text-slate-400">Loading incidents...</div> : error ? (
      <div className="p-6 text-center" role="alert"><p className="text-sm text-red-300">{error}</p><button type="button" onClick={onRetry} className="mt-3 rounded-md bg-cyan-500 px-4 py-2 text-sm font-medium text-slate-950 focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-400">Retry</button></div>
    ) : incidents.length === 0 ? (
      <div className="p-6 text-center"><p className="text-sm text-slate-300">No incidents to display.</p><p className="mt-1 text-xs text-slate-400">No records match this view.</p></div>
    ) : <div className="overflow-x-auto">
      <table className="w-full min-w-[1050px] table-fixed text-left text-sm">
        <thead className="bg-slate-950/60 text-[11px] uppercase tracking-wider text-slate-400"><tr><th className="w-[14%] px-4 py-2.5 font-medium">Incident ID</th><th className="w-[25%] px-4 py-2.5 font-medium">Title</th><th className="w-[11%] px-4 py-2.5 font-medium">Severity</th><th className="w-[15%] px-4 py-2.5 font-medium">Status</th><th className="w-[13%] px-4 py-2.5 font-medium">Host</th><th className="w-[11%] px-4 py-2.5 font-medium">Detection</th><th className="w-[11%] px-4 py-2.5 font-medium">Timestamp</th></tr></thead>
        <tbody className="divide-y divide-slate-800">{incidents.map(incident => <IncidentListItem key={incident.incidentId} incident={incident} onSelect={onSelect} />)}</tbody>
      </table>
    </div>}
  </section>;
}

function IncidentListItem({ incident, onSelect }: { incident: Incident; onSelect: (incident: Incident) => void }) {
  const open = () => onSelect(incident);
  return <tr onClick={open} tabIndex={0} onKeyDown={event => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); open(); } }} aria-label={`Open incident ${incident.incidentId}`} className="cursor-pointer transition hover:bg-slate-800/50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-inset focus-visible:outline-cyan-400">
    <td className="px-4 py-3 font-mono text-xs text-cyan-300">{incident.incidentId}</td><td className="px-4 py-3"><div className="truncate font-medium text-slate-100">{incident.title}</div></td><td className="px-4 py-3"><SeverityBadge severity={incident.severity} /></td><td className="px-4 py-3"><StatusBadge status={incident.status} /></td><td className="truncate px-4 py-3 text-xs text-slate-300">{incident.host || "—"}</td><td className="truncate px-4 py-3 text-xs text-slate-300">{incident.detection || "—"}</td><td className="px-4 py-3 text-xs text-slate-300">{formatIncidentTimestamp(incident.timestamp)}</td>
  </tr>;
}

function formatIncidentTimestamp(value: string | null): string {
  if (!value) return "—";
  const match = value.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})(?::\d{2}(?:\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?$/);
  if (!match) return value;
  return `${match[1]} ${match[2]}${match[3] ? ` ${match[3]}` : ""}`;
}
