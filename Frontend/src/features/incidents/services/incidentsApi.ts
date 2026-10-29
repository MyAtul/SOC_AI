import { apiClient } from "../../../services/apiClient";
import type { Incident } from "../types/incident";

export async function getIncidents(): Promise<Incident[]> {
  const response = await apiClient.get<Incident[] | { value: Incident[] }>("/incidents");
  const payload = response.data;
  if (Array.isArray(payload)) return payload;
  if (payload && typeof payload === "object" && Array.isArray(payload.value)) return payload.value;
  throw new Error("The incidents API returned an unsupported response shape.");
}
