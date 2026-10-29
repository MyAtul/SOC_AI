import { useCallback, useEffect, useRef, useState, type Dispatch, type SetStateAction } from "react";
import { approveIncident, denyIncident, getApprovalDecisions } from "../services/approvalsApi";
import type { ApprovalDecisionRecord, ApprovalResponse } from "../types/approval";
import { applyApprovalStatus } from "../../../services/incidentState";
import type { Incident } from "../../incidents/types/incident";

export function useApproval(incidentId: string | null, refreshIncidents: () => Promise<void>, updateIncidents: Dispatch<SetStateAction<Incident[]>>, updateSelected: Dispatch<SetStateAction<Incident | null>>) {
  const [result, setResult] = useState<ApprovalResponse | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [historyState, setHistoryState] = useState<{ incidentId: string; records: ApprovalDecisionRecord[]; loading: boolean; error: string | null } | null>(null);
  const pendingRef = useRef(false);
  const historySequence = useRef(0);

  const loadHistory = useCallback(async (targetIncidentId: string | null) => {
    if (!targetIncidentId) return;
    const sequence = ++historySequence.current;
    try {
      const records = await getApprovalDecisions(targetIncidentId);
      if (sequence === historySequence.current) setHistoryState({ incidentId: targetIncidentId, records, loading: false, error: null });
    } catch {
      if (sequence === historySequence.current) setHistoryState({ incidentId: targetIncidentId, records: [], loading: false, error: "Decision history could not be loaded." });
    }
  }, []);

  useEffect(() => {
    if (!incidentId) return;
    const sequence = ++historySequence.current;
    getApprovalDecisions(incidentId).then(records => {
      if (sequence === historySequence.current) setHistoryState({ incidentId, records, loading: false, error: null });
    }).catch(() => {
      if (sequence === historySequence.current) setHistoryState({ incidentId, records: [], loading: false, error: "Decision history could not be loaded." });
    });
    return () => { historySequence.current += 1; };
  }, [incidentId]);

  const decide = async (incidentId: string, decision: "approve" | "deny") => {
    if (pendingRef.current) return;
    pendingRef.current = true;
    setPending(true);
    setError(null);
    try {
      const response = decision === "approve" ? await approveIncident(incidentId) : await denyIncident(incidentId);
      setResult(response);
      if (response.status === "APPROVED" || response.status === "DENIED") {
        updateIncidents(items => applyApprovalStatus(items, response.incidentId, response.status));
        updateSelected(incident => incident?.incidentId === response.incidentId ? { ...incident, status: response.status } : incident);
        try {
          await refreshIncidents();
        } catch {
          setError("The decision was saved, but the incident list could not be refreshed.");
        }
        await loadHistory(incidentId);
      }
    } catch (cause) {
      const status = typeof cause === "object" && cause !== null && "response" in cause
        ? (cause as { response?: { status?: number } }).response?.status : undefined;
      setError(status === 409 ? "A decision has already been recorded. Refresh the incident and review its current status." : "The decision could not be saved. Please retry.");
    } finally {
      pendingRef.current = false;
      setPending(false);
    }
  };
  const currentHistory = historyState?.incidentId === incidentId ? historyState : null;
  return {
    result, pending, error,
    history: currentHistory?.records ?? [],
    historyLoading: currentHistory?.loading ?? Boolean(incidentId),
    historyError: currentHistory?.error ?? null,
    approve: (id: string) => decide(id, "approve"),
    deny: (id: string) => decide(id, "deny"),
    clear: () => { setResult(null); setError(null); }
  };
}
