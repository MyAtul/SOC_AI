import axios from "axios";

const API_BASE_URL = "http://localhost:8080/api";

const api = axios.create({
  baseURL: API_BASE_URL,
});

// ─────────────────────────────────────────────
// INCIDENT
// ─────────────────────────────────────────────

export interface Incident {
  incidentId: string;
  title: string;
  severity: string;
  status: string;
  host: string;
  detection: string;
  timestamp: string;
}

// ─────────────────────────────────────────────
// AI INVESTIGATION
// ─────────────────────────────────────────────

export interface AIInvestigation {
  incidentId: string;
  classification: string;
  severity: string;
  confidence: number;
  attackTechnique: string;
  evidence: string[];
  summary: string;
  recommendedAction: string;
  risk: string;
  approvalRequired: boolean;
}

// ─────────────────────────────────────────────
// APPROVAL
// ─────────────────────────────────────────────

export interface ApprovalResponse {
  incidentId: string;
  action: string;
  decision: string;
  status: string;
  message: string;
}

// ─────────────────────────────────────────────
// API FUNCTIONS
// ─────────────────────────────────────────────

export const getIncidents = async (): Promise<Incident[]> => {
  const response = await api.get<Incident[]>("/incidents");

  return response.data;
};

export const investigateIncident = async (
  incidentId: string
): Promise<AIInvestigation> => {
  const response = await api.post<AIInvestigation>(
    `/incidents/${incidentId}/investigate`
  );

  return response.data;
};

export const approveIncident = async (
  incidentId: string
): Promise<ApprovalResponse> => {
  const response = await api.post<ApprovalResponse>(
    `/incidents/${incidentId}/approve`
  );

  return response.data;
};

export const denyIncident = async (
  incidentId: string
): Promise<ApprovalResponse> => {
  const response = await api.post<ApprovalResponse>(
    `/incidents/${incidentId}/deny`
  );

  return response.data;
};