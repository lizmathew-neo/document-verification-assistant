// ═══════════════════════════════════════════════════════════════════
// AI Document Assistant panel.
//   HINT mode: confidence score + traffic light only.
//   FULL mode: + findings, field comparison and a recommended decision.
// The AI is advisory: applying a recommendation should still go through your
// app's normal decision action, which the reviewer confirms.
// ═══════════════════════════════════════════════════════════════════
import React from 'react';
import type {
  AiAssistMode,
  AiRecommendedDecision,
  AiTrafficLight,
  AssistAnalysis,
} from './types';

const LIGHT_COLOURS: Record<AiTrafficLight, { bg: string; fg: string; label: string }> = {
  GREEN: { bg: '#0FCFAB', fg: '#040D18', label: 'Looks good' },
  AMBER: { bg: '#F5A623', fg: '#040D18', label: 'Check carefully' },
  RED:   { bg: '#E74C3C', fg: '#FFFFFF', label: 'Likely problem' },
};

const DECISION_LABELS: Record<AiRecommendedDecision, string> = {
  VERIFY: 'Verify',
  NOT_LEGIBLE: 'Not Legible',
  REJECT: 'Reject',
};

const SEVERITY_COLOURS: Record<string, string> = {
  CRITICAL: '#E74C3C',
  WARNING: '#F5A623',
  INFO: 'var(--dva-muted, #6b7280)',
};

const EXPIRY_LABELS: Record<string, string> = {
  VALID: 'Valid',
  EXPIRING_SOON: 'Expiring soon',
  EXPIRED: 'Expired',
  NOT_APPLICABLE: 'Not applicable',
  UNKNOWN: 'Unknown',
};

export const AiTrafficDot: React.FC<{ light?: AiTrafficLight; stale?: boolean; title?: string }> = ({
  light, stale, title,
}) => {
  if (!light) return null;
  return (
    <span
      data-testid="ai-traffic-dot"
      title={title ?? `AI: ${LIGHT_COLOURS[light].label}${stale ? ' (out of date)' : ''}`}
      style={{
        display: 'inline-block', width: 9, height: 9, borderRadius: 5,
        background: LIGHT_COLOURS[light].bg, marginLeft: 6, verticalAlign: 'middle',
        opacity: stale ? 0.35 : 1,
      }}
    />
  );
};

interface Props {
  mode: AiAssistMode;
  available: boolean;
  analysis?: AssistAnalysis;
  /** True when a file is attached that can be analysed. */
  hasFile: boolean;
  running: boolean;
  onAnalyse: () => void;
  /** Present only when the reviewer may decide on this document right now. */
  onApplyRecommendation?: (decision: AiRecommendedDecision, reason: string) => void;
}

const box: React.CSSProperties = {
  padding: 10, borderRadius: 6, marginBottom: 6,
  background: 'var(--dva-bg, #f9fafb)', border: '1px solid var(--dva-border, #e5e7eb)', fontSize: '0.76rem',
};
const smallBtn: React.CSSProperties = {
  padding: '4px 12px', borderRadius: 6, border: '1px solid #7C5CFF',
  background: 'transparent', color: '#7C5CFF', fontWeight: 600, cursor: 'pointer',
  fontSize: '0.74rem', fontFamily: "'Plus Jakarta Sans', sans-serif",
};
const subHead: React.CSSProperties = {
  fontSize: '0.66rem', textTransform: 'uppercase', letterSpacing: '0.04em',
  color: 'var(--dva-muted, #6b7280)', fontWeight: 700, margin: '10px 0 4px',
};
const tick = (v: boolean | null | undefined) =>
  v === true ? <span style={{ color: '#0FCFAB' }}>✓</span>
    : v === false ? <span style={{ color: '#E74C3C' }}>✗</span>
      : <span style={{ color: 'var(--dva-muted, #6b7280)' }}>?</span>;

const AiAssistPanel: React.FC<Props> = ({
  mode, available, analysis, hasFile, running, onAnalyse, onApplyRecommendation,
}) => {
  if (mode === 'OFF') return null;

  const stale = !!analysis?.stale;
  const light = analysis?.trafficLight ? LIGHT_COLOURS[analysis.trafficLight] : null;
  const isFull = mode === 'FULL';

  return (
    <div data-testid="ai-assist-panel" style={box}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
        <div style={{ fontWeight: 700, color: 'var(--dva-text, #111827)' }}>
          ✨ AI Assistant
          <span style={{
            marginLeft: 6, padding: '1px 6px', borderRadius: 8, fontSize: '0.62rem',
            background: '#7C5CFF22', color: '#7C5CFF', fontWeight: 700,
          }}>
            {isFull ? 'FULL' : 'HINT'}
          </span>
        </div>
        {available && (
          <button
            type="button"
            data-testid="ai-analyse-btn"
            style={{ ...smallBtn, opacity: !hasFile || running ? 0.5 : 1 }}
            disabled={!hasFile || running}
            title={!hasFile ? 'No file uploaded' : 'Run an AI check of this document (billable)'}
            onClick={onAnalyse}
          >
            {running ? 'Analysing…' : analysis ? '↻ Re-analyse' : 'Analyse with AI'}
          </button>
        )}
      </div>

      {!available && (
        <div style={{ marginTop: 6, color: 'var(--dva-muted, #6b7280)', fontStyle: 'italic' }}>
          AI Document Assistant is not enabled on this environment.
        </div>
      )}

      {analysis && (
        <div style={{ marginTop: 8 }}>
          {stale && (
            <div data-testid="ai-stale" style={{ color: '#F5A623', marginBottom: 6 }}>
              ⚠ This result is for a previous file — re-analyse the current upload.
            </div>
          )}

          {analysis.status === 'ERROR' ? (
            <div data-testid="ai-error" style={{ color: '#E74C3C' }}>
              AI analysis failed: {analysis.errorMessage ?? 'unknown error'}
            </div>
          ) : (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, opacity: stale ? 0.55 : 1 }}>
              {light && (
                <span data-testid="ai-traffic-light" style={{
                  padding: '2px 10px', borderRadius: 10, fontWeight: 700, fontSize: '0.72rem',
                  background: light.bg, color: light.fg,
                }}>
                  {light.label}
                </span>
              )}
              <span data-testid="ai-score" style={{ fontFamily: "'DM Mono', monospace", fontWeight: 700 }}>
                {analysis.overallScore ?? '—'}/100
              </span>
              <span style={{ color: 'var(--dva-muted, #6b7280)' }}>confidence</span>
            </div>
          )}

          {isFull && analysis.status === 'SUCCESS' && (
            <div data-testid="ai-full-details" style={{ opacity: stale ? 0.55 : 1 }}>
              {analysis.summary && (
                <div style={{ marginTop: 6, color: 'var(--dva-text, #111827)' }}>{analysis.summary}</div>
              )}

              <div style={subHead}>Checks</div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', rowGap: 2 }}>
                <span>Correct document type</span>{tick(analysis.documentTypeMatches)}
                <span>Legible</span>{tick(analysis.legible)}
                <span>Validity</span>
                <span>{EXPIRY_LABELS[analysis.expiryStatus ?? 'UNKNOWN'] ?? analysis.expiryStatus}</span>
              </div>

              {(analysis.fieldComparisons?.length ?? 0) > 0 && (
                <>
                  <div style={subHead}>Profile vs document</div>
                  <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.72rem' }}>
                    <thead>
                      <tr style={{ color: 'var(--dva-muted, #6b7280)', textAlign: 'left' }}>
                        <th style={{ fontWeight: 600 }}>Field</th>
                        <th style={{ fontWeight: 600 }}>On file</th>
                        <th style={{ fontWeight: 600 }}>On document</th>
                        <th />
                      </tr>
                    </thead>
                    <tbody>
                      {analysis.fieldComparisons!.map((c) => (
                        <tr key={c.field} style={{ borderTop: '1px solid var(--dva-border, #e5e7eb)' }}>
                          <td>{c.field}</td>
                          <td style={{ fontFamily: "'DM Mono', monospace" }}>{c.expected ?? '—'}</td>
                          <td style={{ fontFamily: "'DM Mono', monospace" }}>{c.found ?? '—'}</td>
                          <td>{tick(c.match)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </>
              )}

              {(analysis.issues?.length ?? 0) > 0 && (
                <>
                  <div style={subHead}>Findings</div>
                  <ul style={{ margin: 0, paddingLeft: 16 }}>
                    {analysis.issues!.map((i, idx) => (
                      <li key={idx} style={{ color: SEVERITY_COLOURS[i.severity] ?? 'var(--dva-text, #111827)' }}>
                        <strong>{i.severity}</strong> — {i.message}
                      </li>
                    ))}
                  </ul>
                </>
              )}

              {analysis.recommendedDecision && (
                <div data-testid="ai-recommendation" style={{
                  marginTop: 10, padding: 8, borderRadius: 4,
                  background: 'var(--dva-card, #ffffff)', border: '1px dashed #7C5CFF',
                }}>
                  <div>
                    <strong>Suggested decision: {DECISION_LABELS[analysis.recommendedDecision]}</strong>
                  </div>
                  {analysis.recommendedReason && (
                    <div style={{ color: 'var(--dva-muted, #6b7280)', marginTop: 2 }}>“{analysis.recommendedReason}”</div>
                  )}
                  {onApplyRecommendation && !stale && (
                    <button
                      type="button"
                      data-testid="ai-apply-recommendation"
                      style={{ ...smallBtn, marginTop: 6 }}
                      onClick={() => onApplyRecommendation(
                        analysis.recommendedDecision!, analysis.recommendedReason ?? '')}
                    >
                      {analysis.recommendedDecision === 'VERIFY'
                        ? '✓ Verify as suggested'
                        : `Use this reason → ${DECISION_LABELS[analysis.recommendedDecision]}`}
                    </button>
                  )}
                </div>
              )}
            </div>
          )}

          <div style={{ marginTop: 8, fontSize: '0.66rem', color: 'var(--dva-muted, #6b7280)' }}>
            AI guidance only — always verify against the original document.
            {analysis.analysedAt && <> Analysed {new Date(analysis.analysedAt).toLocaleString()}.</>}
          </div>
        </div>
      )}
    </div>
  );
};

export default AiAssistPanel;
