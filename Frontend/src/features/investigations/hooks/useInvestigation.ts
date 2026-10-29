import { useEffect, useRef, useState } from "react";
import { getInvestigation, investigateIncident } from "../services/investigationsApi";
import type { AIInvestigation } from "../types/investigation";

type InvestigationState = { incidentId: string; phase: "loading" | "submitting" | "ready" | "error"; value: AIInvestigation | null; error: string | null; retryOperation?: "load" | "submit" };

export function useInvestigation(incidentId: string | null) {
  const [state, setState] = useState<InvestigationState | null>(null);
  const requestSequence = useRef(0);

  useEffect(() => {
    if (!incidentId) return;
    let active = true;
    const sequence = ++requestSequence.current;
    getInvestigation(incidentId).then(value => {
      if (active && sequence === requestSequence.current) setState({ incidentId, phase: "ready", value, error: null });
    }).catch(() => {
      if (active && sequence === requestSequence.current) setState({ incidentId, phase: "error", value: null, error: "The saved investigation could not be loaded. Retry to check again.", retryOperation: "load" });
    });
    return () => { active = false; requestSequence.current += 1; };
  }, [incidentId]);

  const current = state?.incidentId === incidentId ? state : null;
  const investigate = async () => {
    if (!incidentId) return;
    const sequence = ++requestSequence.current;
    setState({ incidentId, phase: "submitting", value: current?.value ?? null, error: null });
    try {
      const value = await investigateIncident(incidentId);
      if (sequence === requestSequence.current) setState({ incidentId, phase: "ready", value, error: null });
    } catch {
      if (sequence === requestSequence.current) setState({ incidentId, phase: "error", value: null, error: "The AI investigation failed. Please try again.", retryOperation: "submit" });
    }
  };

  const retry = async () => {
    if (!incidentId) return;
    if (current?.phase !== "error" || current.retryOperation !== "load") {
      await investigate();
      return;
    }

    const sequence = ++requestSequence.current;
    setState({ incidentId, phase: "loading", value: null, error: null });
    try {
      const value = await getInvestigation(incidentId);
      if (sequence === requestSequence.current) setState({ incidentId, phase: "ready", value, error: null });
    } catch {
      if (sequence === requestSequence.current) setState({ incidentId, phase: "error", value: null, error: "The saved investigation could not be loaded. Retry to check again.", retryOperation: "load" });
    }
  };

  return {
    investigation: current?.value ?? null,
    loading: !current || current.phase === "loading",
    investigating: current?.phase === "submitting",
    error: current?.phase === "error" ? current.error : null,
    investigate,
    retry,
  };
}
