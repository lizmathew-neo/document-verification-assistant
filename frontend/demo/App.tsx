import React, { useState } from 'react';
import AiAssistPanel, { AiTrafficDot } from '../src/AiAssistPanel';
import type { AiAssistMode, AiRecommendedDecision, AssistAnalysis } from '../src/types';
import { APPLICANT, REFERENCE_DATE, SAMPLES } from './samples';
import { toView } from './view';

type Decision = 'VERIFIED' | 'NOT_LEGIBLE' | 'REJECTED';

const DECISION_FROM_AI: Record<AiRecommendedDecision, Decision> = {
  VERIFY: 'VERIFIED',
  NOT_LEGIBLE: 'NOT_LEGIBLE',
  REJECT: 'REJECTED',
};

const DECISION_LABEL: Record<Decision, string> = {
  VERIFIED: 'Verified',
  NOT_LEGIBLE: 'Not legible',
  REJECTED: 'Rejected',
};

const MODES: Array<{ mode: AiAssistMode; label: string; help: string }> = [
  { mode: 'OFF', label: 'Off', help: 'No AI assistance. The reviewer checks documents unaided.' },
  { mode: 'HINT', label: 'Hint', help: 'Only a confidence score and a traffic light.' },
  { mode: 'FULL', label: 'Full assistance', help: 'Findings, a record-vs-document comparison and a suggested decision.' },
];

/** Simulated latency of a real vision-model call. */
export const ANALYSIS_DELAY_MS = 1200;

interface DialogState {
  docId: string;
  decision: Decision;
  reason: string;
}

export default function App() {
  const [mode, setMode] = useState<AiAssistMode>('FULL');
  const [selectedId, setSelectedId] = useState(SAMPLES[0].id);
  const [results, setResults] = useState<Record<string, AssistAnalysis>>({});
  const [runningId, setRunningId] = useState<string | null>(null);
  const [decisions, setDecisions] = useState<Record<string, Decision>>({});
  const [dialog, setDialog] = useState<DialogState | null>(null);

  const selected = SAMPLES.find((s) => s.id === selectedId)!;
  const stored = results[selected.id];

  const analyse = () => {
    const id = selected.id;
    setRunningId(id);
    window.setTimeout(() => {
      const sample = SAMPLES.find((s) => s.id === id)!;
      setResults((prev) => ({
        ...prev,
        [id]: { ...sample.result, stale: false, analysedAt: new Date().toISOString() },
      }));
      setRunningId((cur) => (cur === id ? null : cur));
    }, ANALYSIS_DELAY_MS);
  };

  const simulateReupload = () => {
    setResults((prev) => (prev[selected.id] ? { ...prev, [selected.id]: { ...prev[selected.id], stale: true } } : prev));
    setDecisions(({ [selected.id]: _removed, ...rest }) => rest);
  };

  const openDecision = (decision: Decision, reason = '') =>
    setDialog({ docId: selected.id, decision, reason });

  const confirmDecision = () => {
    if (!dialog) return;
    setDecisions((prev) => ({ ...prev, [dialog.docId]: dialog.decision }));
    setDialog(null);
  };

  const reset = () => {
    setResults({});
    setDecisions({});
    setRunningId(null);
    setDialog(null);
  };

  return (
    <div className="page">
      <header className="header">
        <div>
          <h1>Document Verification Assistant</h1>
          <p className="tagline">
            An AI assistant that helps a human reviewer check documents. Pick a reviewer mode, choose a
            document and click <strong>Analyse with AI</strong>.
          </p>
        </div>
        <a className="repo-link" href="https://github.com/lizmathew-neo/document-verification-assistant">View on GitHub</a>
      </header>

      <div className="banner" role="note">
        <strong>Demo.</strong> All documents are fictional specimens for a fictional country. Results are
        pre-computed examples of the real service's output: nothing is uploaded and no AI is called.
      </div>

      <section className="modes" aria-label="Reviewer AI mode">
        <span className="modes-label">Reviewer's AI mode (set per user by an administrator):</span>
        <div className="segmented" role="radiogroup">
          {MODES.map((m) => (
            <button
              key={m.mode}
              type="button"
              role="radio"
              aria-checked={mode === m.mode}
              className={mode === m.mode ? 'seg active' : 'seg'}
              data-testid={`mode-${m.mode}`}
              onClick={() => setMode(m.mode)}
            >
              {m.label}
            </button>
          ))}
        </div>
        <span className="modes-help">{MODES.find((m) => m.mode === mode)!.help}</span>
        <button type="button" className="link-btn" onClick={reset}>Reset demo</button>
      </section>

      <main className="workspace">
        <nav className="doc-list" aria-label="Documents">
          <div className="pane-title">Documents ({SAMPLES.length})</div>
          {SAMPLES.map((s) => {
            const view = results[s.id] && toView(results[s.id], mode);
            const decision = decisions[s.id];
            return (
              <button
                key={s.id}
                type="button"
                data-testid={`doc-${s.id}`}
                className={s.id === selectedId ? 'doc-item active' : 'doc-item'}
                onClick={() => setSelectedId(s.id)}
              >
                <span className="doc-name">
                  {s.label}
                  <AiTrafficDot light={view?.trafficLight} stale={view?.stale} />
                </span>
                <span className={`badge ${decision ? decision.toLowerCase() : 'uploaded'}`}>
                  {decision ? DECISION_LABEL[decision] : 'Uploaded'}
                </span>
              </button>
            );
          })}
        </nav>

        <section className="preview" aria-label="Document preview">
          <div className="pane-title">{selected.label}</div>
          <div className="preview-frame">
            <img src={selected.image} alt={`Specimen ${selected.label.toLowerCase()}`} />
          </div>
        </section>

        <aside className="side">
          <div className="pane-title">Applicant record</div>
          <dl className="record">
            <dt>Full name</dt><dd>{APPLICANT.fullName}</dd>
            <dt>Date of birth</dt><dd>{APPLICANT.dateOfBirth}</dd>
            <dt>Nationality</dt><dd>{APPLICANT.nationality}</dd>
            <dt>Passport no.</dt><dd>{APPLICANT.passportNumber}</dd>
            <dt>Passport expiry</dt><dd>{APPLICANT.passportExpiry}</dd>
            <dt>Checked on</dt><dd>{REFERENCE_DATE}</dd>
          </dl>

          <div className="pane-title">Document decision</div>
          <div className="decision-box">
            <div className="decision-buttons">
              <button type="button" className="btn green" onClick={() => openDecision('VERIFIED')}>✓ Verify</button>
              <button type="button" className="btn amber" onClick={() => openDecision('NOT_LEGIBLE')}>⚠ Not legible</button>
              <button type="button" className="btn red" onClick={() => openDecision('REJECTED')}>✕ Reject</button>
            </div>
            <button type="button" className="link-btn" onClick={simulateReupload} disabled={!stored}
              title={stored ? 'Pretend the applicant uploaded a new file' : 'Analyse the document first'}>
              Simulate re-upload
            </button>
          </div>

          {mode === 'OFF' && (
            <p className="off-note" data-testid="off-note">
              AI assistance is <strong>off</strong> for this reviewer, so no AI panel is shown.
            </p>
          )}
          <AiAssistPanel
            mode={mode}
            available
            hasFile
            analysis={stored && toView(stored, mode)}
            running={runningId === selected.id}
            onAnalyse={analyse}
            onApplyRecommendation={(decision, reason) => openDecision(DECISION_FROM_AI[decision], reason)}
          />
        </aside>
      </main>

      {dialog && (
        <div className="dialog-backdrop" onClick={() => setDialog(null)}>
          <div className="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"
            onClick={(e) => e.stopPropagation()}>
            <h2 id="dialog-title">Mark as {DECISION_LABEL[dialog.decision].toLowerCase()}?</h2>
            {dialog.decision !== 'VERIFIED' && (
              <label className="dialog-label">
                Reason (sent to the applicant)
                <textarea
                  data-testid="decision-reason"
                  rows={4}
                  value={dialog.reason}
                  onChange={(e) => setDialog({ ...dialog, reason: e.target.value })}
                />
              </label>
            )}
            <p className="dialog-note">The AI only suggests. The reviewer makes the decision.</p>
            <div className="dialog-actions">
              <button type="button" className="btn" onClick={() => setDialog(null)}>Cancel</button>
              <button
                type="button"
                className="btn primary"
                data-testid="confirm-decision"
                disabled={dialog.decision !== 'VERIFIED' && !dialog.reason.trim()}
                onClick={confirmDecision}
              >
                Confirm
              </button>
            </div>
          </div>
        </div>
      )}

      <footer className="footer">
        © 2026 lizmathew-neo. All rights reserved. Sample documents are fictional and contain no real personal data.
      </footer>
    </div>
  );
}
