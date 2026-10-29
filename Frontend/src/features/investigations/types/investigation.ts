export interface AIInvestigation {
  incidentId: string;
  classification: string;
  severity: string;
  confidence: number;
  attackTechnique: string | null;
  evidence: string[];
  summary: string;
  recommendedAction: string | null;
  risk: string | null;
  approvalRequired: boolean;
  scenarioMetadata?: AttackScenarioMetadata | null;
  responseActionMetadata?: ResponseActionMetadata | null;
  analysisStatus?: "SIMULATED" | "MODEL_GENERATED" | "UNKNOWN";
}

export interface AttackScenarioMetadata {
  id: string;
  name: string;
  coverage: "IMPLEMENTED_DETECTION" | "PARTIAL_DETECTION" | "KNOWLEDGE_BASE_ONLY";
  coverageDescription: string;
  references: { id: string; name: string; kind: string; url: string }[];
}

export interface ResponseActionMetadata {
  id: string;
  name: string;
  description: string;
  approvalRequired: boolean;
  executionMode: "SIMULATED_ONLY";
}
