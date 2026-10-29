import { useCallback, useEffect, useRef, useState } from "react";

import { getIncidents } from "./features/incidents/services/incidentsApi";
import type { Incident } from "./features/incidents/types/incident";
import { useInvestigation } from "./features/investigations/hooks/useInvestigation";
import { useApproval } from "./features/approvals/hooks/useApproval";
import { IncidentDetails } from './features/incidents/components/IncidentDetails';
import { IncidentStats } from './features/incidents/components/IncidentStats';
import { IncidentList } from './features/incidents/components/IncidentList';

function App() {
  const [section, setSection] = useState<"dashboard" | "incidents" | "approvals" | "audit">("dashboard");
  const [incidents, setIncidents] = useState<Incident[]>([]);

  const [selectedIncident, setSelectedIncident] =
    useState<Incident | null>(null);

  const [loading, setLoading] = useState(true);
  const [incidentLoadError, setIncidentLoadError] = useState<string | null>(null);
  const incidentRequestSequence = useRef(0);
  const investigation = useInvestigation(selectedIncident?.incidentId ?? null);

  // ─────────────────────────────────────────────
  // LOAD INCIDENTS
  // ─────────────────────────────────────────────

  const fetchIncidents = useCallback(() => getIncidents(), []);

  const loadIncidents = useCallback(async () => {
    const sequence = ++incidentRequestSequence.current;
    setLoading(true);
    try {
      const data = await fetchIncidents();

      if (sequence !== incidentRequestSequence.current) return;
      setIncidents(data);
      setIncidentLoadError(null);
    } catch (error) {
      if (sequence !== incidentRequestSequence.current) return;
      console.error("Failed to load incidents:", error);
      setIncidentLoadError("Incidents could not be loaded. Check the backend connection and retry.");
    } finally {
      if (sequence === incidentRequestSequence.current) setLoading(false);
    }
  }, [fetchIncidents]);

  useEffect(() => {
    let active = true;
    const sequence = ++incidentRequestSequence.current;
    fetchIncidents().then((data) => {
      if (active && sequence === incidentRequestSequence.current) {
        setIncidents(data);
        setIncidentLoadError(null);
      }
    }).catch((error: unknown) => {
      if (active && sequence === incidentRequestSequence.current) {
        console.error("Failed to load incidents:", error);
        setIncidentLoadError("Incidents could not be loaded. Check the backend connection and retry.");
      }
    }).finally(() => {
      if (active && sequence === incidentRequestSequence.current) setLoading(false);
    });
    return () => {
      active = false;
      if (sequence === incidentRequestSequence.current) incidentRequestSequence.current += 1;
    };
  }, [fetchIncidents]);

  const approval = useApproval(selectedIncident?.incidentId ?? null, loadIncidents, setIncidents, setSelectedIncident);

  // ─────────────────────────────────────────────
  // SELECT INCIDENT
  // ─────────────────────────────────────────────

  const handleSelectIncident = (incident: Incident) => {
    setSelectedIncident(incident);

    // Reset previous investigation
    approval.clear();
  };

  // ─────────────────────────────────────────────
  // BACK
  // ─────────────────────────────────────────────

  const handleBack = () => {
    setSelectedIncident(null);

    approval.clear();
  };

  // ─────────────────────────────────────────────
  // AI INVESTIGATION
  // ─────────────────────────────────────────────

  const handleInvestigate = async () => {
    approval.clear();
    await investigation.investigate();
  };

  // ─────────────────────────────────────────────
  // APPROVE
  // ─────────────────────────────────────────────

  const handleApprove = async () => {
    if (selectedIncident) await approval.approve(selectedIncident.incidentId);
  };

  // ─────────────────────────────────────────────
  // DENY
  // ─────────────────────────────────────────────

  const handleDeny = async () => {
    if (selectedIncident) await approval.deny(selectedIncident.incidentId);
  };

  // ─────────────────────────────────────────────
  // INCIDENT DETAILS
  // ─────────────────────────────────────────────

  if (selectedIncident) {
    return (
      <IncidentDetails
        incident={selectedIncident}
        investigation={investigation.investigation}
        investigating={investigation.investigating}
        investigationLoading={investigation.loading}
        investigationError={investigation.error}
        onRetryInvestigation={investigation.retry}
        approvalResult={approval.result}
        approvalLoading={approval.pending}
        approvalError={approval.error}
        approvalHistory={approval.history}
        approvalHistoryLoading={approval.historyLoading}
        approvalHistoryError={approval.historyError}
        onBack={handleBack}
        onInvestigate={handleInvestigate}
        onApprove={handleApprove}
        onDeny={handleDeny}
      />
    );
  }

  // ─────────────────────────────────────────────
  // DASHBOARD STATISTICS
  // ─────────────────────────────────────────────


  // ─────────────────────────────────────────────
  // DASHBOARD
  // ─────────────────────────────────────────────

  return (
    <div className="soc-shell min-h-screen bg-slate-950 text-white">

      {/* ───────────────── NAVBAR ───────────────── */}

      <header className="border-b border-slate-800 bg-slate-900">

        <div className="flex h-14 items-center justify-between px-4 md:px-6">

          <div className="flex items-center gap-3">

            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-cyan-500/10">
              <span className="font-bold text-cyan-400">
                AI
              </span>
            </div>

            <div>
              <h1 className="text-lg font-semibold">
                AI-SOC
              </h1>

              <p className="text-xs text-slate-400">
                Security Operations Center
              </p>
            </div>

          </div>

          <div className="flex items-center gap-5">

            <span className="text-sm text-slate-400">
              Local console
            </span>

            <button type="button" disabled title="Authentication is not configured; sign out is unavailable." aria-label="Logout unavailable because authentication is not configured" className="text-sm text-slate-500 opacity-60 cursor-not-allowed">
              Logout (unavailable)
            </button>

          </div>

        </div>

      </header>

      <div className="flex flex-col md:flex-row">

        {/* ───────────────── SIDEBAR ───────────────── */}

        <aside className="w-full border-b border-slate-800 bg-slate-900 p-2 md:min-h-[calc(100vh-3.5rem)] md:w-48 md:border-b-0 md:border-r md:p-3">

          <nav aria-label="Primary navigation" className="flex flex-wrap gap-1.5 md:block md:space-y-1">

            <button type="button" aria-current={section === "dashboard" ? "page" : undefined} onClick={() => setSection("dashboard")} className={`w-full rounded-lg px-4 py-3 text-left text-sm font-medium focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-400 ${section === "dashboard" ? "bg-cyan-500/10 text-cyan-400" : "text-slate-400 transition hover:bg-slate-800 hover:text-white"}`}>
              Dashboard
            </button>

            <button type="button" aria-current={section === "incidents" ? "page" : undefined} onClick={() => setSection("incidents")} className={`w-full rounded-lg px-4 py-3 text-left text-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-400 ${section === "incidents" ? "bg-cyan-500/10 text-cyan-400" : "text-slate-400 transition hover:bg-slate-800 hover:text-white"}`}>
              Incidents
            </button>

            <button type="button" aria-current={section === "approvals" ? "page" : undefined} onClick={() => setSection("approvals")} className={`w-full rounded-lg px-4 py-3 text-left text-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-400 ${section === "approvals" ? "bg-cyan-500/10 text-cyan-400" : "text-slate-400 transition hover:bg-slate-800 hover:text-white"}`}>
              Approvals
            </button>

            <button type="button" aria-current={section === "audit" ? "page" : undefined} onClick={() => setSection("audit")} className={`w-full rounded-lg px-4 py-3 text-left text-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-400 ${section === "audit" ? "bg-cyan-500/10 text-cyan-400" : "text-slate-400 transition hover:bg-slate-800 hover:text-white"}`}>
              Audit Logs
            </button>

          </nav>

        </aside>

        {/* ───────────────── MAIN ───────────────── */}

        <main className="mx-auto w-full max-w-[1600px] flex-1 p-4 md:p-6">

          <div className="mb-5">

            <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-end">

              <div>

                <p className="text-sm font-medium text-cyan-400">
                  SECURITY MONITORING
                </p>

                <h2 className="mt-1 text-2xl font-bold">
                  Security Operations Center
                </h2>

                <p className="mt-1 text-sm text-slate-400">
                  Monitor and investigate security incidents.
                </p>

              </div>

              <div className="text-xs text-slate-500">
                Backend incident records · telemetry provenance not provided
              </div>

            </div>

          </div>

          {/* ───────────────── STATISTICS ───────────────── */}

          {section === "audit" ? (
            <section className="mt-8 rounded-xl border border-slate-800 bg-slate-900 p-8" aria-labelledby="audit-unavailable">
              <h3 id="audit-unavailable" className="font-semibold">Audit logs unavailable</h3>
              <p className="mt-2 text-sm text-slate-400">This application does not currently provide an audit-log API or persisted audit records.</p>
            </section>
          ) : <>
          {section === "dashboard" && <IncidentStats incidents={incidents} />}

          {/* ───────────────── INCIDENTS ───────────────── */}

          <IncidentList title={section === "approvals" ? "Awaiting Approval" : "Recent Incidents"} incidents={section === "approvals" ? incidents.filter(item => item.status === "AWAITING_APPROVAL") : incidents} loading={loading} error={incidentLoadError}
            onRetry={() => void loadIncidents()} onRefresh={() => void loadIncidents()} onSelect={handleSelectIncident} />
          </>}

        </main>

      </div>

    </div>
  );
}

export default App;
