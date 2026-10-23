import { useEffect, useState } from "react";

import {
  getIncidents,
  getInvestigation,
  investigateIncident,
  approveIncident,
  denyIncident,
  type Incident,
  type AIInvestigation,
  type ApprovalResponse,
} from "./services/incidentService";

function App() {
  const [incidents, setIncidents] = useState<Incident[]>([]);

  const [selectedIncident, setSelectedIncident] =
    useState<Incident | null>(null);

  const [investigation, setInvestigation] =
    useState<AIInvestigation | null>(null);

  const [approvalResult, setApprovalResult] =
    useState<ApprovalResponse | null>(null);

  const [loading, setLoading] = useState(true);

  const [investigating, setInvestigating] = useState(false);

  const [investigationLoading, setInvestigationLoading] = useState(false);

  const [investigationError, setInvestigationError] = useState<string | null>(null);

  const [approvalLoading, setApprovalLoading] = useState(false);

  // ─────────────────────────────────────────────
  // LOAD INCIDENTS
  // ─────────────────────────────────────────────

  useEffect(() => {
    loadIncidents();
  }, []);

  useEffect(() => {
    if (!selectedIncident) return;

    let ignoreResult = false;

    setInvestigation(null);
    setInvestigationError(null);
    setInvestigationLoading(true);

    getInvestigation(selectedIncident.incidentId)
      .then((result) => {
        if (!ignoreResult) setInvestigation(result);
      })
      .catch((error) => {
        console.error("Failed to load saved AI investigation:", error);
        if (!ignoreResult) {
          setInvestigationError(
            "The saved investigation could not be loaded. You can try investigating this incident again."
          );
        }
      })
      .finally(() => {
        if (!ignoreResult) setInvestigationLoading(false);
      });

    return () => {
      ignoreResult = true;
    };
  }, [selectedIncident]);

  const loadIncidents = async () => {
    try {
      const data = await getIncidents();

      setIncidents(data);
    } catch (error) {
      console.error("Failed to load incidents:", error);
    } finally {
      setLoading(false);
    }
  };

  // ─────────────────────────────────────────────
  // SELECT INCIDENT
  // ─────────────────────────────────────────────

  const handleSelectIncident = (incident: Incident) => {
    setSelectedIncident(incident);

    // Reset previous investigation
    setInvestigation(null);

    // Reset previous approval
    setApprovalResult(null);
  };

  // ─────────────────────────────────────────────
  // BACK
  // ─────────────────────────────────────────────

  const handleBack = () => {
    setSelectedIncident(null);

    setInvestigation(null);

    setApprovalResult(null);
  };

  // ─────────────────────────────────────────────
  // AI INVESTIGATION
  // ─────────────────────────────────────────────

  const handleInvestigate = async () => {
    if (!selectedIncident) return;

    setInvestigating(true);
    setInvestigationError(null);

    // Clear previous approval when starting
    // a new investigation.
    setApprovalResult(null);

    try {
      const result = await investigateIncident(
        selectedIncident.incidentId
      );

      setInvestigation(result);
    } catch (error) {
      console.error("AI investigation failed:", error);
      setInvestigationError(
        "The AI investigation failed. Please try again."
      );
    } finally {
      setInvestigating(false);
    }
  };

  // ─────────────────────────────────────────────
  // APPROVE
  // ─────────────────────────────────────────────

  const handleApprove = async () => {
    if (!selectedIncident) return;

    setApprovalLoading(true);

    try {
      const result = await approveIncident(
        selectedIncident.incidentId
      );

      setApprovalResult(result);
    } catch (error) {
      console.error("Approval failed:", error);
    } finally {
      setApprovalLoading(false);
    }
  };

  // ─────────────────────────────────────────────
  // DENY
  // ─────────────────────────────────────────────

  const handleDeny = async () => {
    if (!selectedIncident) return;

    setApprovalLoading(true);

    try {
      const result = await denyIncident(
        selectedIncident.incidentId
      );

      setApprovalResult(result);
    } catch (error) {
      console.error("Denial failed:", error);
    } finally {
      setApprovalLoading(false);
    }
  };

  // ─────────────────────────────────────────────
  // INCIDENT DETAILS
  // ─────────────────────────────────────────────

  if (selectedIncident) {
    return (
      <IncidentDetails
        incident={selectedIncident}
        investigation={investigation}
        investigating={investigating}
        investigationLoading={investigationLoading}
        investigationError={investigationError}
        approvalResult={approvalResult}
        approvalLoading={approvalLoading}
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

  const criticalCount = incidents.filter(
    (incident) => incident.severity === "CRITICAL"
  ).length;

  const highCount = incidents.filter(
    (incident) => incident.severity === "HIGH"
  ).length;

  const approvalCount = incidents.filter(
    (incident) => incident.status === "AWAITING_APPROVAL"
  ).length;

  // ─────────────────────────────────────────────
  // DASHBOARD
  // ─────────────────────────────────────────────

  return (
    <div className="min-h-screen bg-slate-950 text-white">

      {/* ───────────────── NAVBAR ───────────────── */}

      <header className="border-b border-slate-800 bg-slate-900">

        <div className="flex h-16 items-center justify-between px-8">

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

            {/* System status */}

            <div className="hidden items-center gap-2 sm:flex">
              <span className="h-2 w-2 rounded-full bg-emerald-400" />

              <span className="text-xs text-emerald-400">
                SYSTEM ONLINE
              </span>
            </div>

            <span className="text-sm text-slate-400">
              Analyst
            </span>

            <button className="text-sm text-slate-400 transition hover:text-white">
              Logout
            </button>

          </div>

        </div>

      </header>

      <div className="flex">

        {/* ───────────────── SIDEBAR ───────────────── */}

        <aside className="hidden min-h-[calc(100vh-4rem)] w-60 border-r border-slate-800 bg-slate-900 p-5 md:block">

          <nav className="space-y-2">

            <button className="w-full rounded-lg bg-cyan-500/10 px-4 py-3 text-left text-sm font-medium text-cyan-400">
              Dashboard
            </button>

            <button className="w-full rounded-lg px-4 py-3 text-left text-sm text-slate-400 transition hover:bg-slate-800 hover:text-white">
              Incidents
            </button>

            <button className="w-full rounded-lg px-4 py-3 text-left text-sm text-slate-400 transition hover:bg-slate-800 hover:text-white">
              Approvals
            </button>

            <button className="w-full rounded-lg px-4 py-3 text-left text-sm text-slate-400 transition hover:bg-slate-800 hover:text-white">
              Audit Logs
            </button>

          </nav>

          {/* Architecture indicator */}

          <div className="mt-10 rounded-lg border border-slate-800 bg-slate-950 p-4">

            <p className="text-xs font-medium text-slate-500">
              AI-SOC ENGINE
            </p>

            <div className="mt-3 space-y-2 text-xs">

              <div className="flex items-center justify-between">
                <span className="text-slate-400">
                  Detection
                </span>

                <span className="text-emerald-400">
                  ONLINE
                </span>
              </div>

              <div className="flex items-center justify-between">
                <span className="text-slate-400">
                  AI Engine
                </span>

                <span className="text-emerald-400">
                  READY
                </span>
              </div>

              <div className="flex items-center justify-between">
                <span className="text-slate-400">
                  Policy
                </span>

                <span className="text-emerald-400">
                  ACTIVE
                </span>
              </div>

            </div>

          </div>

        </aside>

        {/* ───────────────── MAIN ───────────────── */}

        <main className="flex-1 p-8">

          <div className="mb-8">

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
                Live telemetry
              </div>

            </div>

          </div>

          {/* ───────────────── STATISTICS ───────────────── */}

          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">

            <StatCard
              title="Active Incidents"
              value={incidents.length}
            />

            <StatCard
              title="Critical"
              value={criticalCount}
            />

            <StatCard
              title="High Severity"
              value={highCount}
            />

            <StatCard
              title="Awaiting Approval"
              value={approvalCount}
            />

          </div>

          {/* ───────────────── INCIDENTS ───────────────── */}

          <div className="mt-8 rounded-xl border border-slate-800 bg-slate-900">

            <div className="border-b border-slate-800 px-6 py-5">

              <h3 className="font-semibold">
                Recent Incidents
              </h3>

              <p className="mt-1 text-sm text-slate-400">
                Security alerts detected by the SOC.
              </p>

            </div>

            {loading ? (

              <div className="p-8 text-center text-slate-400">
                Loading incidents...
              </div>

            ) : incidents.length === 0 ? (

              <div className="p-8 text-center">

                <p className="text-slate-400">
                  No active incidents.
                </p>

                <p className="mt-1 text-xs text-slate-600">
                  The SOC has no incidents requiring attention.
                </p>

              </div>

            ) : (

              <div className="divide-y divide-slate-800">

                {incidents.map((incident) => (

                  <button
                    key={incident.incidentId}
                    onClick={() =>
                      handleSelectIncident(incident)
                    }
                    className="flex w-full flex-col gap-4 px-6 py-5 text-left transition hover:bg-slate-800/50 md:flex-row md:items-center md:justify-between"
                  >

                    <div>

                      <div className="flex items-center gap-3">

                        <span className="font-mono text-sm text-cyan-400">
                          {incident.incidentId}
                        </span>

                        <SeverityBadge
                          severity={incident.severity}
                        />

                      </div>

                      <h4 className="mt-2 font-medium">
                        {incident.title}
                      </h4>

                      <p className="mt-1 text-sm text-slate-400">
                        {incident.host} •{" "}
                        {incident.detection}
                      </p>

                    </div>

                    <div className="text-left md:text-right">

                      <StatusBadge
                        status={incident.status}
                      />

                      <p className="mt-2 text-xs text-slate-500">
                        {incident.timestamp}
                      </p>

                    </div>

                  </button>

                ))}

              </div>

            )}

          </div>

        </main>

      </div>

    </div>
  );
}

/* ───────────────────────────────────────────── */
/* INCIDENT DETAILS                              */
/* ───────────────────────────────────────────── */

function IncidentDetails({
  incident,
  investigation,
  investigating,
  investigationLoading,
  investigationError,
  approvalResult,
  approvalLoading,
  onBack,
  onInvestigate,
  onApprove,
  onDeny,
}: {
  incident: Incident;
  investigation: AIInvestigation | null;
  investigating: boolean;
  investigationLoading: boolean;
  investigationError: string | null;
  approvalResult: ApprovalResponse | null;
  approvalLoading: boolean;
  onBack: () => void;
  onInvestigate: () => void;
  onApprove: () => void;
  onDeny: () => void;
}) {
  return (
    <div className="min-h-screen bg-slate-950 text-white">

      {/* ───────────────── NAVBAR ───────────────── */}

      <header className="border-b border-slate-800 bg-slate-900">

        <div className="flex h-16 items-center px-8">

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

        </div>

      </header>

      <main className="mx-auto max-w-6xl p-8">

        {/* ───────────────── BACK ───────────────── */}

        <button
          onClick={onBack}
          className="mb-6 text-sm text-slate-400 transition hover:text-cyan-400"
        >
          ← Back to incidents
        </button>

        {/* ───────────────── INCIDENT HEADER ───────────────── */}

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6">

          <div className="flex flex-col justify-between gap-4 md:flex-row md:items-start">

            <div>

              <div className="flex items-center gap-3">

                <span className="font-mono text-cyan-400">
                  {incident.incidentId}
                </span>

                <SeverityBadge
                  severity={incident.severity}
                />

              </div>

              <h2 className="mt-3 text-2xl font-bold">
                {incident.title}
              </h2>

              <p className="mt-2 text-slate-400">
                {incident.detection}
              </p>

            </div>

            <StatusBadge
              status={incident.status}
            />

          </div>

          {/* Incident information */}

          <div className="mt-6 grid gap-4 border-t border-slate-800 pt-6 sm:grid-cols-3">

            <InfoItem
              label="Affected Host"
              value={incident.host}
            />

            <InfoItem
              label="Severity"
              value={incident.severity}
            />

            <InfoItem
              label="Detected At"
              value={incident.timestamp}
            />

          </div>

        </div>

        {/* ───────────────── AI INVESTIGATION ───────────────── */}

        <div className="mt-6 rounded-xl border border-slate-800 bg-slate-900">

          <div className="border-b border-slate-800 p-6">

            <div className="flex items-center gap-3">

              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-cyan-500/10">

                <span className="text-lg text-cyan-400">
                  ✦
                </span>

              </div>

              <div>

                <h3 className="font-semibold">
                  AI Investigation
                </h3>

                <p className="text-sm text-slate-400">
                  View or run an AI assessment for this incident.
                </p>

              </div>

            </div>

          </div>

          <div className="p-6">

            {/* No investigation */}

            {!investigation && !investigating && !investigationLoading && (

              <div className="py-8 text-center">

                <p className="mb-5 text-slate-400">
                  {investigationError ?? "No saved AI investigation is available yet."}
                </p>

                <button
                  onClick={onInvestigate}
                  className="rounded-lg bg-cyan-500 px-6 py-3 font-medium text-slate-950 transition hover:bg-cyan-400"
                >
                  Investigate with AI
                </button>

              </div>

            )}

            {/* Investigation loading */}

            {(investigating || investigationLoading) && (

              <div className="py-10 text-center">

                <div className="mx-auto mb-4 h-8 w-8 animate-spin rounded-full border-2 border-slate-700 border-t-cyan-400" />

                <p className="font-medium">
                  {investigationLoading
                    ? "Loading saved AI investigation..."
                    : "AI investigation in progress..."}
                </p>

                <p className="mt-1 text-sm text-slate-400">
                  {investigationLoading
                    ? "Checking for a previous result"
                    : "Analyzing incident evidence"}
                </p>

              </div>

            )}

            {/* Investigation result */}

            {investigation && !investigating && (

              <AIResult
                investigation={investigation}
                approvalResult={approvalResult}
                approvalLoading={approvalLoading}
                onApprove={onApprove}
                onDeny={onDeny}
              />

            )}

          </div>

        </div>

      </main>

    </div>
  );
}

/* ───────────────────────────────────────────── */
/* AI RESULT                                     */
/* ───────────────────────────────────────────── */

function AIResult({
  investigation,
  approvalResult,
  approvalLoading,
  onApprove,
  onDeny,
}: {
  investigation: AIInvestigation;
  approvalResult: ApprovalResponse | null;
  approvalLoading: boolean;
  onApprove: () => void;
  onDeny: () => void;
}) {
  return (
    <div className="space-y-6">

      {/* ───────────────── SUMMARY ───────────────── */}

      <div>

        <p className="text-sm text-slate-400">
          AI Assessment
        </p>

        <h3 className="mt-1 text-xl font-semibold">
          {investigation.classification}
        </h3>

        <p className="mt-3 leading-7 text-slate-300">
          {investigation.summary}
        </p>

      </div>

      {/* ───────────────── METRICS ───────────────── */}

      <div className="grid gap-4 sm:grid-cols-3">

        <ResultCard
          label="Severity"
          value={investigation.severity}
        />

        <ResultCard
          label="Confidence"
          value={`${Math.round(
            investigation.confidence * 100
          )}%`}
        />

        <ResultCard
          label="MITRE Technique"
          value={investigation.attackTechnique}
        />

      </div>

      {/* ───────────────── EVIDENCE ───────────────── */}

      <div>

        <h4 className="mb-3 font-semibold">
          Evidence
        </h4>

        <div className="space-y-2">

          {investigation.evidence.map(
            (item, index) => (

              <div
                key={index}
                className="flex items-start gap-3 rounded-lg border border-slate-800 bg-slate-950 p-4"
              >

                <span className="mt-0.5 text-emerald-400">
                  ✓
                </span>

                <span className="text-sm text-slate-300">
                  {item}
                </span>

              </div>

            )
          )}

        </div>

      </div>

      {/* ───────────────── RESPONSE RECOMMENDATION ───────────────── */}

      <div className="rounded-xl border border-orange-500/20 bg-orange-500/5 p-5">

        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">

          <div>

            <p className="text-sm text-slate-400">
              Response Recommendation
            </p>

            <h4 className="mt-1 text-xl font-semibold text-orange-400">
              {investigation.recommendedAction}
            </h4>

          </div>

          <div className="flex flex-wrap gap-2">

            <span className="rounded-full border border-orange-500/20 bg-orange-500/10 px-3 py-1 text-xs text-orange-400">
              Risk: {investigation.risk}
            </span>

            {investigation.approvalRequired && (

              <span className="rounded-full border border-yellow-500/20 bg-yellow-500/10 px-3 py-1 text-xs text-yellow-400">
                ⚠ Approval Required
              </span>

            )}

            {!investigation.approvalRequired && (

              <span className="rounded-full border border-emerald-500/20 bg-emerald-500/10 px-3 py-1 text-xs text-emerald-400">
                ✓ Approval Not Required
              </span>

            )}

          </div>

        </div>

        {/* ───────────────── APPROVAL CONTROLS ───────────────── */}

        {investigation.approvalRequired && !approvalResult && (

          <div className="mt-6 border-t border-orange-500/10 pt-5">

            <p className="mb-4 text-sm text-slate-400">
              This action requires analyst approval before it
              can proceed.
            </p>

            <div className="flex flex-wrap gap-3">

              <button
                onClick={onApprove}
                disabled={approvalLoading}
                className="rounded-lg bg-emerald-600 px-6 py-3 text-sm font-semibold text-white transition hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {approvalLoading
                  ? "Processing..."
                  : "✓ Approve Action"}
              </button>

              <button
                onClick={onDeny}
                disabled={approvalLoading}
                className="rounded-lg border border-red-500/30 bg-red-500/10 px-6 py-3 text-sm font-semibold text-red-300 transition hover:bg-red-500/20 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {approvalLoading
                  ? "Processing..."
                  : "✕ Deny Action"}
              </button>

            </div>

          </div>

        )}

        {/* ───────────────── APPROVAL RESULT ───────────────── */}

        {approvalResult && (

          <ApprovalResult
            result={approvalResult}
          />

        )}

      </div>

    </div>
  );
}

/* ───────────────────────────────────────────── */
/* APPROVAL RESULT                               */
/* ───────────────────────────────────────────── */

function ApprovalResult({
  result,
}: {
  result: ApprovalResponse;
}) {
  const approved =
    result.status === "APPROVED";

  const denied =
    result.status === "DENIED";

  const policyDenied =
    result.status === "POLICY_DENIED";

  const autoAllowed =
    result.status === "AUTO_ALLOWED";

  let containerStyle =
    "border-slate-700 bg-slate-800/50";

  let icon = "ℹ";

  let iconStyle = "text-cyan-400";

  let title = result.status;

  if (approved) {
    containerStyle =
      "border-emerald-500/30 bg-emerald-500/10";

    icon = "✓";
    iconStyle = "text-emerald-400";
    title = "Action Approved";
  }

  if (denied) {
    containerStyle =
      "border-red-500/30 bg-red-500/10";

    icon = "✕";
    iconStyle = "text-red-400";
    title = "Action Denied";
  }

  if (policyDenied) {
    containerStyle =
      "border-red-500/30 bg-red-500/10";

    icon = "⛔";
    iconStyle = "text-red-400";
    title = "Action Blocked by Policy";
  }

  if (autoAllowed) {
    containerStyle =
      "border-cyan-500/30 bg-cyan-500/10";

    icon = "✓";
    iconStyle = "text-cyan-400";
    title = "Action Automatically Allowed";
  }

  return (
    <div
      className={`mt-6 rounded-xl border p-5 ${containerStyle}`}
    >

      <div className="flex items-start gap-4">

        <span
          className={`text-2xl font-bold ${iconStyle}`}
        >
          {icon}
        </span>

        <div className="flex-1">

          <div className="flex flex-wrap items-center gap-3">

            <p className="font-semibold text-white">
              {title}
            </p>

            <span className="rounded-full border border-slate-700 bg-slate-900 px-2.5 py-1 text-xs text-slate-400">
              {result.action}
            </span>

          </div>

          <p className="mt-2 text-sm leading-6 text-slate-400">
            {result.message}
          </p>

        </div>

      </div>

    </div>
  );
}

/* ───────────────────────────────────────────── */
/* STAT CARD                                     */
/* ───────────────────────────────────────────── */

function StatCard({
  title,
  value,
}: {
  title: string;
  value: number;
}) {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900 p-5 transition hover:border-slate-700">

      <p className="text-sm text-slate-400">
        {title}
      </p>

      <p className="mt-2 text-3xl font-bold">
        {value}
      </p>

    </div>
  );
}

/* ───────────────────────────────────────────── */
/* INFO ITEM                                     */
/* ───────────────────────────────────────────── */

function InfoItem({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div>

      <p className="text-xs uppercase tracking-wide text-slate-500">
        {label}
      </p>

      <p className="mt-1 text-sm font-medium text-slate-200">
        {value}
      </p>

    </div>
  );
}

/* ───────────────────────────────────────────── */
/* RESULT CARD                                   */
/* ───────────────────────────────────────────── */

function ResultCard({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-950 p-4">

      <p className="text-xs text-slate-500">
        {label}
      </p>

      <p className="mt-2 text-sm font-semibold text-slate-200">
        {value}
      </p>

    </div>
  );
}

/* ───────────────────────────────────────────── */
/* SEVERITY BADGE                                */
/* ───────────────────────────────────────────── */

function SeverityBadge({
  severity,
}: {
  severity: string;
}) {
  const styles: Record<string, string> = {
    CRITICAL:
      "bg-red-500/10 text-red-400 border-red-500/20",

    HIGH:
      "bg-orange-500/10 text-orange-400 border-orange-500/20",

    MEDIUM:
      "bg-yellow-500/10 text-yellow-400 border-yellow-500/20",

    LOW:
      "bg-green-500/10 text-green-400 border-green-500/20",
  };

  return (
    <span
      className={`rounded-full border px-2.5 py-1 text-xs font-medium ${
        styles[severity] ??
        "bg-slate-500/10 text-slate-400 border-slate-500/20"
      }`}
    >
      {severity}
    </span>
  );
}

/* ───────────────────────────────────────────── */
/* STATUS BADGE                                  */
/* ───────────────────────────────────────────── */

function StatusBadge({
  status,
}: {
  status: string;
}) {
  const styles: Record<string, string> = {
    INVESTIGATING:
      "border-cyan-500/20 bg-cyan-500/10 text-cyan-400",

    OPEN:
      "border-yellow-500/20 bg-yellow-500/10 text-yellow-400",

    AWAITING_APPROVAL:
      "border-orange-500/20 bg-orange-500/10 text-orange-400",

    RESOLVED:
      "border-emerald-500/20 bg-emerald-500/10 text-emerald-400",

    CLOSED:
      "border-slate-700 bg-slate-800 text-slate-400",
  };

  return (
    <span
      className={`rounded-full border px-3 py-1 text-xs ${
        styles[status] ??
        "border-slate-700 bg-slate-800 text-slate-300"
      }`}
    >
      {status.replaceAll("_", " ")}
    </span>
  );
}

export default App;
