import type { AiAssistMode, AssistAnalysis } from '../src/types';

/**
 * Mirrors the backend's AssistView: in HINT mode only the score and traffic
 * light are kept. In the real service this filtering happens server-side.
 */
export function toView(full: AssistAnalysis, mode: AiAssistMode): AssistAnalysis | undefined {
  if (mode === 'OFF') return undefined;
  if (mode === 'HINT') {
    const { status, overallScore, trafficLight, errorMessage, stale, analysedAt } = full;
    return { mode: 'HINT', status, overallScore, trafficLight, errorMessage, stale, analysedAt };
  }
  return { ...full, mode: 'FULL' };
}
