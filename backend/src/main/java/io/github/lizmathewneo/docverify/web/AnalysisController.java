package io.github.lizmathewneo.docverify.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lizmathewneo.docverify.analysis.AnalysisRequest;
import io.github.lizmathewneo.docverify.analysis.AnalysisResult;
import io.github.lizmathewneo.docverify.analysis.DocumentAnalysisService;
import io.github.lizmathewneo.docverify.assist.AssistMode;
import io.github.lizmathewneo.docverify.assist.AssistModeResolver;
import io.github.lizmathewneo.docverify.assist.AssistView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final DocumentAnalysisService analysisService;
    private final AssistModeResolver modeResolver;
    private final ObjectMapper objectMapper;

    public AnalysisController(DocumentAnalysisService analysisService,
                              AssistModeResolver modeResolver,
                              ObjectMapper objectMapper) {
        this.analysisService = analysisService;
        this.modeResolver = modeResolver;
        this.objectMapper = objectMapper;
    }

    /** The caller's mode and whether the AI engine is configured. */
    @GetMapping("/status")
    public Map<String, Object> status(HttpServletRequest http) {
        AssistMode mode = modeResolver.resolve(http);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode);
        body.put("available", mode != AssistMode.OFF && analysisService.isEnabled());
        body.put("provider", analysisService.getProvider());
        body.put("modelId", analysisService.getModelId());
        return body;
    }

    /**
     * Analyse one uploaded document.
     *
     * @param expectedFields JSON object of values on record, e.g. {"fullName":"Jane Doe","dateOfBirth":"1990-01-31"}
     */
    @PostMapping(value = "/analyse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssistView analyse(@RequestParam("file") MultipartFile file,
                              @RequestParam("requirementCode") String requirementCode,
                              @RequestParam(value = "requirementLabel", required = false) String requirementLabel,
                              @RequestParam(value = "requirementDescription", required = false) String requirementDescription,
                              @RequestParam(value = "expectedFields", required = false) String expectedFieldsJson,
                              @RequestParam(value = "referenceDate", required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
                              HttpServletRequest http) {
        AssistMode mode = modeResolver.resolve(http);
        if (mode == AssistMode.OFF) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AI assistance is not enabled for this user");
        }
        if (!analysisService.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI document analysis is not enabled");
        }
        if (requirementCode == null || requirementCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requirementCode is required");
        }
        if (file.getSize() > analysisService.getMaxFileBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File too large for AI analysis");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded file");
        }

        AnalysisRequest request = new AnalysisRequest(requirementCode, requirementLabel,
                requirementDescription, parseExpectedFields(expectedFieldsJson), referenceDate);
        AnalysisResult result = analysisService.analyse(bytes, file.getContentType(), request);
        return AssistView.of(result, mode);
    }

    private Map<String, String> parseExpectedFields(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, String> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            return parsed == null ? Map.of() : parsed;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "expectedFields must be a JSON object of string values");
        }
    }
}
