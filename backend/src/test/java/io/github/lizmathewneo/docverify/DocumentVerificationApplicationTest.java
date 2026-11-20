package io.github.lizmathewneo.docverify;

import io.github.lizmathewneo.docverify.analysis.DocumentAnalysisService;
import io.github.lizmathewneo.docverify.assist.AssistMode;
import io.github.lizmathewneo.docverify.assist.AssistModeResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "docverify.assist.mode=HINT")
class DocumentVerificationApplicationTest {

    @Autowired private DocumentAnalysisService analysisService;
    @Autowired private AssistModeResolver resolver;

    @Test
    void contextLoads_withBedrockDisabledByDefault() {
        assertThat(analysisService.isEnabled()).isFalse();
        assertThat(resolver.resolve(new MockHttpServletRequest())).isEqualTo(AssistMode.HINT);
    }
}
