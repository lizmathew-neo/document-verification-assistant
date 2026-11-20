package io.github.lizmathewneo.docverify.analysis;

import java.util.List;
import java.util.Map;

/**
 * Full result of an AI document analysis.
 *
 * <p>{@code status} is "SUCCESS" or "ERROR". On ERROR, {@code trafficLight}
 * is null, collections are empty and {@code errorMessage} carries a safe message.
 */
public record AnalysisResult(
        String status,
        Integer overallScore,
        String trafficLight,
        Boolean documentTypeMatches,
        Boolean legible,
        String expiryStatus,
        Map<String, String> extractedFields,
        List<FieldComparison> fieldComparisons,
        List<Issue> issues,
        String recommendedDecision,
        String recommendedReason,
        String summary,
        String modelId,
        Integer inputTokens,
        Integer outputTokens,
        Double estimatedCostUsd,
        String errorMessage
) {
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_ERROR = "ERROR";

    public record FieldComparison(String field, String expected, String found, Boolean match) {}

    public record Issue(String severity, String message) {}

    public static AnalysisResult error(String modelId, String errorMessage,
                                       Integer inputTokens, Integer outputTokens,
                                       Double estimatedCostUsd) {
        return new AnalysisResult(
                STATUS_ERROR, null, null, null, null, null,
                Map.of(), List.of(), List.of(),
                null, null, null,
                modelId, inputTokens, outputTokens, estimatedCostUsd, errorMessage);
    }
}
