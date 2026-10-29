import { apiClient, axios } from "../../../services/apiClient";
import type { AIInvestigation } from "../types/investigation";

export async function investigateIncident(incidentId: string): Promise<AIInvestigation> {
  const response = await apiClient.post<AIInvestigation>(`/incidents/${incidentId}/investigate`);
  return response.data;
}

export async function getInvestigation(incidentId: string): Promise<AIInvestigation | null> {
  try {
    const response = await apiClient.get<AIInvestigation>(`/incidents/${incidentId}/investigation`);
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) return null;
    throw error;
  }
}
