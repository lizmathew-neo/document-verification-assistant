package io.github.lizmathewneo.docverify.analysis;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentAnalysisServiceTest {

    private static final String CLAUDE = "eu.anthropic.claude-sonnet-5-5";
    private static final String NOVA = "amazon.nova-pro-v1:0";

    @Mock private ObjectProvider<BedrockRuntimeClient> provider;
    @Mock private BedrockRuntimeClient bedrock;

    @BeforeEach
    void setUp() {
        lenient().when(provider.getIfAvailable()).thenReturn(bedrock);
        lenient().when(provider.getObject()).thenReturn(bedrock);
    }

    private DocumentAnalysisService service(String model) {
        return new DocumentAnalysisService(provider, true, model, 15 * 1024 * 1024, 3, 1500, 0.0016, 0.0064);
    }

    // ── Fixtures ────────────────────────────────────────────────

    private static byte[] png() throws IOException {
        BufferedImage img = new BufferedImage(200, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(20, 20, 100, 60);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static byte[] pdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private void stubClaude(String modelText) {
        stubEnvelope("{\"content\":[{\"type\":\"text\",\"text\":\"" + escape(modelText) + "\"}],"
                + "\"usage\":{\"input_tokens\":2000,\"output_tokens\":500}}");
    }

    private void stubNova(String modelText) {
        stubEnvelope("{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\""
                + escape(modelText) + "\"}]}},\"usage\":{\"inputTokens\":1000,\"outputTokens\":250}}");
    }

    private void stubEnvelope(String envelope) {
        when(bedrock.invokeModel(any(InvokeModelRequest.class))).thenReturn(InvokeModelResponse.builder()
                .contentType("application/json").body(SdkBytes.fromUtf8String(envelope)).build());
    }

    private static AnalysisRequest passport() {
        return new AnalysisRequest("PASSPORT", "Passport", "Passport photo page",
                Map.of("fullName", "JANE DOE", "passportNumber", "123456789"), LocalDate.of(2026, 1, 1));
    }

    private String sentBody() {
        ArgumentCaptor<InvokeModelRequest> captor = ArgumentCaptor.forClass(InvokeModelRequest.class);
        verify(bedrock).invokeModel(captor.capture());
        return captor.getValue().body().asUtf8String();
    }

    private static final String GOOD_JSON = """
            {"overallScore": 92, "documentTypeMatches": true, "legible": true, "expiryStatus": "valid",
             "extractedFields": {"passportNumber": "123456789", "surname": "DOE"},
             "fieldComparisons": [{"field":"passportNumber","expected":"123456789","found":"123456789","match":true}],
             "issues": [{"severity":"info","message":"Minor glare"}],
             "recommendedDecision": "VERIFY", "recommendedReason": "All fields match.",
             "summary": "Valid passport."}
            """;

    // ── Tests ───────────────────────────────────────────────────

    @Test
    void claude_validJson_isGreenAndUsesAnthropicSchema() throws IOException {
        stubClaude(GOOD_JSON);
        AnalysisResult r = service(CLAUDE).analyse(png(), "image/png", passport());

        assertThat(r.status()).isEqualTo("SUCCESS");
        assertThat(r.trafficLight()).isEqualTo("GREEN");
        assertThat(r.overallScore()).isEqualTo(92);
        assertThat(r.expiryStatus()).isEqualTo("VALID");
        assertThat(r.recommendedDecision()).isEqualTo("VERIFY");
        assertThat(r.inputTokens()).isEqualTo(2000);
        assertThat(r.estimatedCostUsd()).isCloseTo(2 * 0.0016 + 0.5 * 0.0064, Offset.offset(1e-9));

        String body = sentBody();
        assertThat(body).contains("\"anthropic_version\"").contains("\"media_type\":\"image/jpeg\"")
                .contains("JANE DOE");
    }

    @Test
    void nova_validJson_usesMessagesV1SchemaAndParsesNovaEnvelope() throws IOException {
        stubNova(GOOD_JSON);
        AnalysisResult r = service(NOVA).analyse(png(), "image/png", passport());

        assertThat(r.status()).isEqualTo("SUCCESS");
        assertThat(r.trafficLight()).isEqualTo("GREEN");
        assertThat(r.inputTokens()).isEqualTo(1000);
        assertThat(r.outputTokens()).isEqualTo(250);

        assertThat(sentBody()).contains("\"schemaVersion\":\"messages-v1\"").contains("\"format\":\"jpeg\"")
                .contains("\"inferenceConfig\"").doesNotContain("anthropic_version");
    }

    @Test
    void jsonWrappedInProseAndFences_isStillParsed() throws IOException {
        stubNova("Here is the result:\n```json\n" + GOOD_JSON + "\n```\nThanks");
        assertThat(service(NOVA).analyse(png(), "image/png", passport()).status()).isEqualTo("SUCCESS");
    }

    @Test
    void malformedOutput_isError() throws IOException {
        stubNova("I cannot help with that.");
        AnalysisResult r = service(NOVA).analyse(png(), "image/png", passport());
        assertThat(r.status()).isEqualTo("ERROR");
        assertThat(r.trafficLight()).isNull();
        assertThat(r.errorMessage()).isEqualTo(DocumentAnalysisService.ERR_UNREADABLE);
    }

    @Test
    void criticalIssue_forcesRedClampsScoreAndOverridesVerify() throws IOException {
        stubNova("""
                {"overallScore": 95, "documentTypeMatches": true, "legible": true, "expiryStatus": "VALID",
                 "issues": [{"severity":"CRITICAL","message":"Photo appears pasted over original"}],
                 "recommendedDecision": "VERIFY", "recommendedReason": "", "summary": "x"}
                """);
        AnalysisResult r = service(NOVA).analyse(png(), "image/png", passport());
        assertThat(r.trafficLight()).isEqualTo("RED");
        assertThat(r.overallScore()).isLessThanOrEqualTo(49);
        assertThat(r.recommendedDecision()).isEqualTo("REJECT");
        assertThat(r.recommendedReason()).isNotBlank();
    }

    @Test
    void pdf_isRenderedToJpegPages() throws IOException {
        stubNova(GOOD_JSON);
        service(NOVA).analyse(pdf(), "application/pdf", passport());
        assertThat(sentBody()).contains("\"format\":\"jpeg\"");
    }

    @Test
    void tooLarge_isErrorWithoutCallingModel() throws IOException {
        DocumentAnalysisService small = new DocumentAnalysisService(provider, true, NOVA, 10, 3, 1500, 0.0016, 0.0064);
        AnalysisResult r = small.analyse(png(), "image/png", passport());
        assertThat(r.errorMessage()).isEqualTo(DocumentAnalysisService.ERR_TOO_LARGE);
        verifyNoInteractions(bedrock);
    }

    @Test
    void unsupportedFile_isError() {
        AnalysisResult r = service(NOVA).analyse("not an image".getBytes(), "text/plain", passport());
        assertThat(r.errorMessage()).isEqualTo(DocumentAnalysisService.ERR_UNSUPPORTED);
    }

    @Test
    void throttling_isFriendlyError() throws IOException {
        when(bedrock.invokeModel(any(InvokeModelRequest.class)))
                .thenThrow(ThrottlingException.builder().statusCode(429).message("slow down").build());
        AnalysisResult r = service(NOVA).analyse(png(), "image/png", passport());
        assertThat(r.errorMessage()).isEqualTo(DocumentAnalysisService.ERR_MODEL_BUSY);
    }

    @Test
    void disabled_throws() {
        DocumentAnalysisService off = new DocumentAnalysisService(provider, false, NOVA, 100, 3, 1500, 0, 0);
        assertThat(off.isEnabled()).isFalse();
        assertThatThrownBy(() -> off.analyse(new byte[]{1}, "image/png", passport()))
                .isInstanceOf(IllegalStateException.class);
    }
}
