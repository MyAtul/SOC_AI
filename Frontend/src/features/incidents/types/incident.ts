export interface Incident {
  incidentId: string;
  title: string;
  severity: string;
  status: string;
  host: string | null;
  detection: string | null;
  timestamp: string | null;
}
