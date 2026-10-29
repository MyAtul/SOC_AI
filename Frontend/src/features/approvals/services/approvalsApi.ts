import { apiClient } from "../../../services/apiClient";
import type { ApprovalDecisionRecord, ApprovalResponse } from "../types/approval";

export async function approveIncident(incidentId: string): Promise<ApprovalResponse> {
  const response = await apiClient.post<ApprovalResponse>(`/incidents/${incidentId}/approve`);
  return response.data;
}

export async function denyIncident(incidentId: string): Promise<ApprovalResponse> {
  const response = await apiClient.post<ApprovalResponse>(`/incidents/${incidentId}/deny`);
  return response.data;
}

export async function getApprovalDecisions(incidentId: string): Promise<ApprovalDecisionRecord[]> {
  const response = await apiClient.get<ApprovalDecisionRecord[]>(`/incidents/${incidentId}/approval-decisions`);
  return response.data;
}
