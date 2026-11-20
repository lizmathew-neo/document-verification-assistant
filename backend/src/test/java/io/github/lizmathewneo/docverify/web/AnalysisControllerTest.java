package io.github.lizmathewneo.docverify.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lizmathewneo.docverify.analysis.AnalysisRequest;
import io.github.lizmathewneo.docverify.analysis.AnalysisResult;
import io.github.lizmathewneo.docverify.analysis.DocumentAnalysisService;
import io.github.lizmathewneo.docverify.assist.AssistMode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalysisControllerTest {

    private final DocumentAnalysisService service = mock(DocumentAnalysisService.class);
    private final MockMultipartFile file =
            new MockMultipartFile("file", "passport.png", "image/png", new byte[]{1, 2, 3});

    private static final AnalysisResult RESULT = new AnalysisResult("SUCCESS", 91, "GREEN", true, true, "VALID",
            Map.of(), List.of(), List.of(new AnalysisResult.Issue("INFO", "ok")),
            "VERIFY", "All good", "Fine", "amazon.nova-pro-v1:0", 1, 1, 0.0, null);

    private MockMvc mvc(AssistMode mode) {
        when(service.isEnabled()).thenReturn(true);
        when(service.getMaxFileBytes()).thenReturn(1_000_000L);
        return MockMvcBuilders.standaloneSetup(
                new AnalysisController(service, request -> mode, new ObjectMapper().findAndRegisterModules())).build();
    }

    @Test
    void off_returns403AndNeverAnalyses() throws Exception {
        mvc(AssistMode.OFF).perform(multipart("/api/analyse").file(file).param("requirementCode", "PASSPORT"))
                .andExpect(status().isForbidden());
        verify(service, never()).analyse(any(), any(), any());
    }

    @Test
    void hint_returnsOnlyScoreAndLight() throws Exception {
        MockMvc mvc = mvc(AssistMode.HINT);
        when(service.analyse(any(), eq("image/png"), any())).thenReturn(RESULT);
        mvc.perform(multipart("/api/analyse").file(file).param("requirementCode", "PASSPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("HINT"))
                .andExpect(jsonPath("$.trafficLight").value("GREEN"))
                .andExpect(jsonPath("$.issues").doesNotExist())
                .andExpect(jsonPath("$.recommendedDecision").doesNotExist());
    }

    @Test
    void full_passesExpectedFieldsAndReturnsFindings() throws Exception {
        MockMvc mvc = mvc(AssistMode.FULL);
        when(service.analyse(any(), any(), any())).thenReturn(RESULT);
        mvc.perform(multipart("/api/analyse").file(file)
                        .param("requirementCode", "PASSPORT")
                        .param("expectedFields", "{\"fullName\":\"Jane Doe\"}")
                        .param("referenceDate", "2026-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedDecision").value("VERIFY"))
                .andExpect(jsonPath("$.issues[0].message").value("ok"));

        ArgumentCaptor<AnalysisRequest> req = ArgumentCaptor.forClass(AnalysisRequest.class);
        verify(service).analyse(any(), any(), req.capture());
        assertThat(req.getValue().expectedFields()).containsEntry("fullName", "Jane Doe");
        assertThat(req.getValue().referenceDate()).hasToString("2026-01-01");
    }

    @Test
    void invalidExpectedFields_returns400() throws Exception {
        mvc(AssistMode.FULL).perform(multipart("/api/analyse").file(file)
                        .param("requirementCode", "PASSPORT").param("expectedFields", "not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void engineDisabled_returns503() throws Exception {
        MockMvc mvc = mvc(AssistMode.FULL);
        when(service.isEnabled()).thenReturn(false);
        mvc.perform(multipart("/api/analyse").file(file).param("requirementCode", "PASSPORT"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void status_reportsModeAndAvailability() throws Exception {
        MockMvc mvc = mvc(AssistMode.HINT);
        when(service.getProvider()).thenReturn("AWS_BEDROCK");
        when(service.getModelId()).thenReturn("amazon.nova-pro-v1:0");
        mvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("HINT"))
                .andExpect(jsonPath("$.available").value(true));
    }
}
