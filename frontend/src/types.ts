export type AiAssistMode = 'OFF' | 'HINT' | 'FULL';
export type AiTrafficLight = 'GREEN' | 'AMBER' | 'RED';
export type AiRecommendedDecision = 'VERIFY' | 'NOT_LEGIBLE' | 'REJECT';

/** Response of GET /api/status. */
export interface AssistStatus {
  mode: AiAssistMode;
  /** False when the AI engine is not configured. */
  available: boolean;
}

/**
 * Response of POST /api/analyse. Detail fields are only present in FULL mode.
 * `stale` and `analysedAt` are optional client-side metadata, e.g. set `stale`
 * when the file has been replaced since the analysis ran.
 */
export interface AssistAnalysis {
  mode: AiAssistMode;
  status: 'SUCCESS' | 'ERROR';
  overallScore?: number;
  trafficLight?: AiTrafficLight;
  errorMessage?: string;
  summary?: string;
  documentTypeMatches?: boolean;
  legible?: boolean;
  expiryStatus?: 'VALID' | 'EXPIRING_SOON' | 'EXPIRED' | 'NOT_APPLICABLE' | 'UNKNOWN';
  extractedFields?: Record<string, string>;
  fieldComparisons?: Array<{ field: string; expected: string | null; found: string | null; match: boolean | null }>;
  issues?: Array<{ severity: 'INFO' | 'WARNING' | 'CRITICAL'; message: string }>;
  recommendedDecision?: AiRecommendedDecision;
  recommendedReason?: string;
  stale?: boolean;
  analysedAt?: string;
}
