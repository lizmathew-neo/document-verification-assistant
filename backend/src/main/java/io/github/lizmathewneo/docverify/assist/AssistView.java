package io.github.lizmathewneo.docverify.assist;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.lizmathewneo.docverify.analysis.AnalysisResult;

import java.util.List;
import java.util.Map;

/**
 * The analysis as shown to a reviewer, filtered by their {@link AssistMode}:
 * in HINT mode only the score and traffic light are populated.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssistView(
        AssistMode mode,
        String status,
        Integer overallScore,
        String trafficLight,
        String errorMessage,
        // ── FULL mode only ──
        String summary,
        Boolean documentTypeMatches,
        Boolean legible,
        String expiryStatus,
        Map<String, String> extractedFields,
        List<AnalysisResult.FieldComparison> fieldComparisons,
        List<AnalysisResult.Issue> issues,
        String recommendedDecision,
        String recommendedReason
) {

    /**
     * @throws IllegalArgumentException for {@link AssistMode#OFF} — callers must
     *                                  refuse the request before analysing
     */
    public static AssistView of(AnalysisResult r, AssistMode mode) {
        if (mode == null || mode == AssistMode.OFF) {
            throw new IllegalArgumentException("AI assistance is off for this user");
        }
        boolean full = mode == AssistMode.FULL;
        return new AssistView(
                mode, r.status(), r.overallScore(), r.trafficLight(), r.errorMessage(),
                full ? r.summary() : null,
                full ? r.documentTypeMatches() : null,
                full ? r.legible() : null,
                full ? r.expiryStatus() : null,
                full ? r.extractedFields() : null,
                full ? r.fieldComparisons() : null,
                full ? r.issues() : null,
                full ? r.recommendedDecision() : null,
                full ? r.recommendedReason() : null);
    }
}
