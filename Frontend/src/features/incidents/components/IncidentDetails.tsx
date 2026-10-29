import type { ReactNode } from "react";
import type { Incident } from "../types/incident";
import type { AIInvestigation } from "../../investigations/types/investigation";
import type { ApprovalResponse } from "../../approvals/types/approval";
import type { ApprovalDecisionRecord } from "../../approvals/types/approval";

export function IncidentDetails({
  incident,
  investigation,
  investigating,
  investigationLoading,
  investigationError,
  onRetryInvestigation,
  approvalResult,
  approvalLoading,
  approvalError,
  approvalHistory,
  approvalHistoryLoading,
  approvalHistoryError,
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
  onRetryInvestigation: () => void;
  approvalResult: ApprovalResponse | null;
  approvalLoading: boolean;
  approvalError: string | null;
  approvalHistory: ApprovalDecisionRecord[];
  approvalHistoryLoading: boolean;
  approvalHistoryError: string | null;
  onBack: () => void;
  onInvestigate: () => void;
  onApprove: () => void;
  onDeny: () => void;
}) {
  return (
    <div className="soc-shell min-h-screen bg-slate-950 text-white">

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

      <main className="mx-auto w-full max-w-6xl p-5 md:p-8">

        {/* ───────────────── BACK ───────────────── */}

        <button
          type="button"
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
              {incident.detection ?? "Detection type not provided"}
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
              value={incident.host ?? "—"}
            />

            <InfoItem
              label="Severity"
              value={incident.severity}
            />

            <InfoItem
              label="Detected At"
              value={incident.timestamp ?? "—"}
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
                  Investigation
                </h3>

                <p className="text-sm text-slate-400">
                  View or run the backend investigation for this incident.
                </p>

              </div>

            </div>

          </div>

          <p className="border-b border-amber-500/20 bg-amber-500/5 px-6 py-3 text-xs text-amber-200" role="note">
            The current backend investigation provider is a mock implementation. Results shown here are demonstrations, not genuine AI findings.
          </p>

          <div className="p-6">

            {/* No investigation */}

            {!investigation && !investigating && !investigationLoading && (

              <div className="py-8 text-center">

                <p className="mb-5 text-slate-400">
                  {investigationError ?? "No saved AI investigation is available yet."}
                </p>

                {investigationError && <button type="button" onClick={onRetryInvestigation} className="mb-4 rounded-lg border border-slate-700 px-4 py-2 text-sm text-slate-200 transition hover:bg-slate-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-400">Retry</button>}

                <button
                  type="button"
                  onClick={onInvestigate}
                  className="rounded-lg bg-cyan-500 px-6 py-3 font-medium text-slate-950 transition hover:bg-cyan-400"
                >
                  Generate simulated assessment
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
                    : "Preparing simulated assessment..."}
                </p>

                <p className="mt-1 text-sm text-slate-400">
                  {investigationLoading
                    ? "Checking for a previous result"
                    : "The configured mock provider is generating its demonstration response"}
                </p>

              </div>

            )}

            {/* Investigation result */}

            {investigation && !investigating && (

              <AIResult
                investigation={investigation}
                incidentStatus={incident.status}
                approvalResult={approvalResult}
                approvalLoading={approvalLoading}
                approvalError={approvalError}
                approvalHistory={approvalHistory}
                approvalHistoryLoading={approvalHistoryLoading}
                approvalHistoryError={approvalHistoryError}
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
  incidentStatus,
  approvalResult,
  approvalLoading,
  approvalError,
  approvalHistory,
  approvalHistoryLoading,
  approvalHistoryError,
  onApprove,
  onDeny,
}: {
  investigation: AIInvestigation;
  incidentStatus: string;
  approvalResult: ApprovalResponse | null;
  approvalLoading: boolean;
  approvalError: string | null;
  approvalHistory: ApprovalDecisionRecord[];
  approvalHistoryLoading: boolean;
  approvalHistoryError: string | null;
  onApprove: () => void;
  onDeny: () => void;
}) {
  return (
    <div className="space-y-6">

      {/* ───────────────── SUMMARY ───────────────── */}

      <div>

        <p className="text-sm text-slate-400">
          Investigation Assessment
        </p>

        <p className="mt-2 inline-flex rounded-full border border-amber-500/30 bg-amber-500/10 px-2.5 py-1 text-xs font-medium text-amber-200">
          {investigation.analysisStatus === "MODEL_GENERATED" ? "Model generated" :
            investigation.analysisStatus === "UNKNOWN" ? "Result provenance unknown" : "SIMULATED RESULT"}
        </p>

        <h3 className="mt-1 text-xl font-semibold">
          {investigation.classification}
        </h3>

        <p className="mt-3 leading-7 text-slate-300">
          {investigation.summary}
        </p>

      </div>

      {investigation.scenarioMetadata && (
        <section className="rounded-xl border border-slate-800 bg-slate-950 p-5" aria-label="Detection coverage">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h4 className="font-semibold">{investigation.scenarioMetadata.name}</h4>
            <span className="rounded-full border border-cyan-500/20 bg-cyan-500/10 px-2.5 py-1 text-xs text-cyan-200">
              {investigation.scenarioMetadata.coverage.replaceAll("_", " ")}
            </span>
          </div>
          <p className="mt-2 text-sm text-slate-400">{investigation.scenarioMetadata.coverageDescription}</p>
          {investigation.scenarioMetadata.references.length > 0 && (
            <ul className="mt-3 flex flex-wrap gap-3 text-sm">
              {investigation.scenarioMetadata.references.map((reference) => (
                <li key={reference.id}>
                  <a href={reference.url} target="_blank" rel="noreferrer" className="text-cyan-300 underline decoration-cyan-700 underline-offset-2 hover:text-cyan-200 focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-400">
                    {reference.id}: {reference.name}
                  </a>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

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

        <ResultCard label="MITRE ATT&CK Technique" value={<AttackTechnique technique={investigation.attackTechnique} />} />

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
              {investigation.responseActionMetadata?.name ?? investigation.recommendedAction ?? "No recommendation provided"}
            </h4>
            <p className="mt-2 text-xs text-amber-200">SIMULATED ONLY — {investigation.responseActionMetadata?.description ?? "No response action is executed by this application."}</p>
            <p className="mt-1 text-xs text-slate-400">An approval decision records review only; it does not execute remediation.</p>

          </div>

          <div className="flex flex-wrap gap-2">

            <span className="rounded-full border border-orange-500/20 bg-orange-500/10 px-3 py-1 text-xs text-orange-400">
              Risk: {investigation.risk ?? "Not provided"}
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

        {investigation.approvalRequired && !approvalResult && incidentStatus !== "APPROVED" && incidentStatus !== "DENIED" && (

          <div className="mt-6 border-t border-orange-500/10 pt-5">

            <p className="mb-4 text-sm text-slate-400">
              This simulated recommendation requires a recorded decision. No response action runs after approval.
            </p>

            <div className="flex flex-wrap gap-3">

              <button
                type="button"
                onClick={onApprove}
                disabled={approvalLoading}
                className="rounded-lg bg-emerald-600 px-6 py-3 text-sm font-semibold text-white transition hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {approvalLoading
                  ? "Processing..."
                  : "✓ Approve Action"}
              </button>

              <button
                type="button"
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

        {!approvalResult && (incidentStatus === "APPROVED" || incidentStatus === "DENIED") && (
          <p className="mt-5 border-t border-slate-800 pt-4 text-sm text-slate-300">
            Persisted incident decision: <strong>{incidentStatus}</strong>. This records a decision only; no remediation was executed.
          </p>
        )}

        {/* ───────────────── APPROVAL RESULT ───────────────── */}

        {approvalResult && (

          <ApprovalResult
            result={approvalResult}
          />

        )}
        {approvalError && <p className="mt-4 text-sm text-red-300" role="alert">{approvalError}</p>}

        <section className="mt-6 border-t border-slate-800 pt-5" aria-label="Approval decision history">
          <h4 className="font-semibold text-slate-200">Decision history</h4>
          {approvalHistoryLoading && <p className="mt-2 text-sm text-slate-400">Loading saved decisions…</p>}
          {approvalHistoryError && <p className="mt-2 text-sm text-amber-200" role="status">{approvalHistoryError}</p>}
          {!approvalHistoryLoading && !approvalHistoryError && approvalHistory.length === 0 && (
            <p className="mt-2 text-sm text-slate-400">No approval or denial decisions have been recorded.</p>
          )}
          <ul className="mt-3 space-y-2">
            {approvalHistory.map((record, index) => (
              <li key={`${record.decidedAt}-${record.decision}-${index}`} className="flex flex-wrap justify-between gap-2 rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm">
                <span className={record.decision === "APPROVED" ? "text-emerald-300" : "text-red-300"}>{record.decision}</span>
                <span className="text-slate-400">{record.actorId ?? "Actor not authenticated"}</span>
                <time className="text-slate-500" dateTime={record.decidedAt}>{new Date(record.decidedAt).toLocaleString()}</time>
              </li>
            ))}
          </ul>
        </section>

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

export function StatCard({
  title,
  value,
}: {
  title: string;
  value: number;
}) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900 p-3.5 transition hover:border-slate-700">

      <p className="text-sm text-slate-400">
        {title}
      </p>

      <p className="mt-1 text-2xl font-semibold">
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
  value: ReactNode;
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

function AttackTechnique({ technique }: { technique: string | null }) {
  if (!technique) return <span>Not provided</span>;
  const match = technique.match(/^(T\d{4})(?:\.(\d{3}))?(?:\s+(?:[-—:]\s*)?(.+))?$/i);
  if (!match) return <span>{technique}</span>;

  const id = `${match[1].toUpperCase()}${match[2] ? `.${match[2]}` : ""}`;
  const path = match[2] ? `${match[1].toUpperCase()}/${match[2]}` : match[1].toUpperCase();
  return <span className="flex flex-col items-start gap-1">
    <a href={`https://attack.mitre.org/techniques/${path}/`} target="_blank" rel="noreferrer" className="text-cyan-300 underline decoration-cyan-700 underline-offset-2 hover:text-cyan-200 focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-400">{id}</a>
    {match[3] && <span>{match[3]}</span>}
  </span>;
}

/* ───────────────────────────────────────────── */
/* SEVERITY BADGE                                */
/* ───────────────────────────────────────────── */

export function SeverityBadge({
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

export function StatusBadge({
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



