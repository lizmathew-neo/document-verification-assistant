package io.github.lizmathewneo.docverify.assist;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssistModeConfig {

    /** Fallback: one mode for everyone, from {@code docverify.assist.mode} (default FULL). */
    @Bean
    @ConditionalOnMissingBean(AssistModeResolver.class)
    public AssistModeResolver configuredAssistModeResolver(
            @Value("${docverify.assist.mode:FULL}") AssistMode mode) {
        return request -> mode;
    }
}
