package io.github.lizmathewneo.docverify.assist;

import io.github.lizmathewneo.docverify.analysis.AnalysisResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistViewTest {

    private static final AnalysisResult RESULT = new AnalysisResult("SUCCESS", 64, "AMBER", true, true, "VALID",
            Map.of("passportNumber", "123456789"),
            List.of(new AnalysisResult.FieldComparison("fullName", "Jane Doe", "JANE DOE", true)),
            List.of(new AnalysisResult.Issue("WARNING", "Glare")),
            "NOT_LEGIBLE", "Glare over key fields", "Mostly readable",
            "amazon.nova-pro-v1:0", 1000, 200, 0.003, null);

    @Test
    void hint_exposesOnlyScoreAndTrafficLight() {
        AssistView v = AssistView.of(RESULT, AssistMode.HINT);
        assertThat(v.overallScore()).isEqualTo(64);
        assertThat(v.trafficLight()).isEqualTo("AMBER");
        assertThat(v.issues()).isNull();
        assertThat(v.fieldComparisons()).isNull();
        assertThat(v.extractedFields()).isNull();
        assertThat(v.recommendedDecision()).isNull();
        assertThat(v.recommendedReason()).isNull();
        assertThat(v.summary()).isNull();
    }

    @Test
    void full_exposesFindingsAndRecommendation() {
        AssistView v = AssistView.of(RESULT, AssistMode.FULL);
        assertThat(v.issues()).hasSize(1);
        assertThat(v.fieldComparisons()).hasSize(1);
        assertThat(v.recommendedDecision()).isEqualTo("NOT_LEGIBLE");
    }

    @Test
    void off_isRefused() {
        assertThatThrownBy(() -> AssistView.of(RESULT, AssistMode.OFF)).isInstanceOf(IllegalArgumentException.class);
    }
}
