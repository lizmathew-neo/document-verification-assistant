import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import AiAssistPanel, { AiTrafficDot } from '../AiAssistPanel';
import type { AssistAnalysis } from '../types';

const hint: AssistAnalysis = {
  mode: 'HINT',
  status: 'SUCCESS',
  overallScore: 64,
  trafficLight: 'AMBER',
};

const full: AssistAnalysis = {
  ...hint,
  mode: 'FULL',
  summary: 'Mostly readable passport',
  documentTypeMatches: true,
  legible: true,
  expiryStatus: 'VALID',
  fieldComparisons: [{ field: 'fullName', expected: 'Jane Doe', found: 'JANE DOE', match: true }],
  issues: [{ severity: 'WARNING', message: 'Glare over MRZ' }],
  recommendedDecision: 'NOT_LEGIBLE',
  recommendedReason: 'MRZ partially obscured',
};

describe('AiAssistPanel', () => {
  it('renders nothing in OFF mode', () => {
    const { container } = render(
      <AiAssistPanel mode="OFF" available hasFile running={false} onAnalyse={jest.fn()} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('HINT mode shows only the score and traffic light', () => {
    render(<AiAssistPanel mode="HINT" available hasFile running={false} onAnalyse={jest.fn()} analysis={hint} />);
    expect(screen.getByTestId('ai-score').textContent).toBe('64/100');
    expect(screen.getByTestId('ai-traffic-light').textContent).toBe('Check carefully');
    expect(screen.queryByTestId('ai-full-details')).toBeNull();
    expect(screen.queryByTestId('ai-recommendation')).toBeNull();
  });

  it('FULL mode shows findings and applies the recommendation with its reason', () => {
    const onApply = jest.fn();
    render(
      <AiAssistPanel
        mode="FULL" available hasFile running={false} onAnalyse={jest.fn()}
        analysis={full} onApplyRecommendation={onApply}
      />,
    );
    expect(screen.getByTestId('ai-full-details').textContent).toContain('Glare over MRZ');
    fireEvent.click(screen.getByTestId('ai-apply-recommendation'));
    expect(onApply).toHaveBeenCalledWith('NOT_LEGIBLE', 'MRZ partially obscured');
  });

  it('flags stale results and hides apply', () => {
    render(
      <AiAssistPanel
        mode="FULL" available hasFile running={false} onAnalyse={jest.fn()}
        analysis={{ ...full, stale: true }} onApplyRecommendation={jest.fn()}
      />,
    );
    expect(screen.getByTestId('ai-stale')).toBeTruthy();
    expect(screen.queryByTestId('ai-apply-recommendation')).toBeNull();
  });

  it('shows the error message for a failed analysis', () => {
    render(
      <AiAssistPanel
        mode="FULL" available hasFile running={false} onAnalyse={jest.fn()}
        analysis={{ mode: 'FULL', status: 'ERROR', errorMessage: 'AI service is busy' }}
      />,
    );
    expect(screen.getByTestId('ai-error').textContent).toContain('AI service is busy');
  });

  it('Analyse button triggers analysis and is disabled without a file', () => {
    const onAnalyse = jest.fn();
    const { rerender } = render(
      <AiAssistPanel mode="HINT" available hasFile running={false} onAnalyse={onAnalyse} />,
    );
    fireEvent.click(screen.getByTestId('ai-analyse-btn'));
    expect(onAnalyse).toHaveBeenCalledTimes(1);

    rerender(<AiAssistPanel mode="HINT" available hasFile={false} running={false} onAnalyse={onAnalyse} />);
    expect((screen.getByTestId('ai-analyse-btn') as HTMLButtonElement).disabled).toBe(true);
  });

  it('hides the Analyse button when the engine is unavailable', () => {
    render(<AiAssistPanel mode="FULL" available={false} hasFile running={false} onAnalyse={jest.fn()} />);
    expect(screen.queryByTestId('ai-analyse-btn')).toBeNull();
  });

  it('AiTrafficDot renders only when a light is given', () => {
    const { container, rerender } = render(<AiTrafficDot />);
    expect(container.firstChild).toBeNull();
    rerender(<AiTrafficDot light="RED" />);
    expect(screen.getByTestId('ai-traffic-dot')).toBeTruthy();
  });
});
