import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import App, { ANALYSIS_DELAY_MS } from '../App';
import { SAMPLES } from '../samples';
import { toView } from '../view';

describe('sample results follow the engine scoring rules', () => {
  it.each(SAMPLES.map((s) => [s.id, s.result] as const))('%s', (_id, r) => {
    const score = r.overallScore!;
    const critical = r.issues?.some((i) => i.severity === 'CRITICAL');
    const warningOrMismatch = r.issues?.some((i) => i.severity === 'WARNING')
      || r.fieldComparisons?.some((c) => c.match === false);
    const forcedRed = critical || r.documentTypeMatches === false || r.legible === false || r.expiryStatus === 'EXPIRED';

    if (r.trafficLight === 'RED') {
      expect(score).toBeLessThanOrEqual(49);
      expect(r.recommendedDecision).not.toBe('VERIFY');
    } else if (r.trafficLight === 'AMBER') {
      expect(score).toBeGreaterThanOrEqual(50);
      expect(score).toBeLessThanOrEqual(79);
      expect(forcedRed).toBeFalsy();
    } else {
      expect(score).toBeGreaterThanOrEqual(80);
      expect(forcedRed).toBeFalsy();
      expect(warningOrMismatch).toBeFalsy();
    }
    if (forcedRed) expect(r.trafficLight).toBe('RED');
    expect(r.recommendedReason).toBeTruthy();
  });
});

describe('toView', () => {
  const full = SAMPLES[0].result;

  it('HINT keeps only score and traffic light', () => {
    const v = toView(full, 'HINT')!;
    expect(v.overallScore).toBe(full.overallScore);
    expect(v.trafficLight).toBe(full.trafficLight);
    expect(v.issues).toBeUndefined();
    expect(v.recommendedDecision).toBeUndefined();
    expect(v.fieldComparisons).toBeUndefined();
  });

  it('OFF hides everything and FULL keeps everything', () => {
    expect(toView(full, 'OFF')).toBeUndefined();
    expect(toView(full, 'FULL')!.issues).toEqual(full.issues);
  });
});

describe('demo app', () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  const analyse = () => {
    fireEvent.click(screen.getByTestId('ai-analyse-btn'));
    act(() => { jest.advanceTimersByTime(ANALYSIS_DELAY_MS); });
  };

  it('analyses in FULL mode and applies the suggestion only after the reviewer confirms', () => {
    render(<App />);
    fireEvent.click(screen.getByTestId('doc-medical'));
    analyse();

    expect(screen.getByTestId('ai-traffic-light').textContent).toBe('Likely problem');
    expect(screen.getByTestId('ai-full-details').textContent).toContain('expired');

    fireEvent.click(screen.getByTestId('ai-apply-recommendation'));
    expect((screen.getByTestId('decision-reason') as HTMLTextAreaElement).value)
      .toContain('expired on 1 Oct 2025');
    expect(screen.getByTestId('doc-medical').textContent).toContain('Uploaded');

    fireEvent.click(screen.getByTestId('confirm-decision'));
    expect(screen.getByTestId('doc-medical').textContent).toContain('Rejected');
  });

  it('switching to HINT hides the findings of an existing analysis', () => {
    render(<App />);
    analyse();
    expect(screen.getByTestId('ai-full-details')).toBeTruthy();

    fireEvent.click(screen.getByTestId('mode-HINT'));
    expect(screen.getByTestId('ai-score').textContent).toBe('93/100');
    expect(screen.queryByTestId('ai-full-details')).toBeNull();
  });

  it('OFF mode shows no AI panel', () => {
    render(<App />);
    fireEvent.click(screen.getByTestId('mode-OFF'));
    expect(screen.queryByTestId('ai-assist-panel')).toBeNull();
    expect(screen.getByTestId('off-note')).toBeTruthy();
  });

  it('a simulated re-upload marks the result as stale', () => {
    render(<App />);
    analyse();
    fireEvent.click(screen.getByText('Simulate re-upload'));
    expect(screen.getByTestId('ai-stale')).toBeTruthy();
    expect(screen.queryByTestId('ai-apply-recommendation')).toBeNull();
  });
});
