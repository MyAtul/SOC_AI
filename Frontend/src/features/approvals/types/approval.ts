export interface ApprovalResponse {
  incidentId: string;
  action: string;
  decision: string;
  status: string;
  message: string;
}

export interface ApprovalDecisionRecord {
  incidentId: string;
  decision: "APPROVED" | "DENIED";
  decidedAt: string;
  actorId: string | null;
}
