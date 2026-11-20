package io.github.lizmathewneo.docverify.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.time.Duration;

/**
 * AWS Bedrock Runtime client — only created when {@code docverify.bedrock.enabled=true}.
 *
 * <p>Enabling Bedrock incurs AWS charges per analysed document and sends document
 * images to Amazon Bedrock in the configured region.
 */
@Configuration
@ConditionalOnProperty(prefix = "docverify.bedrock", name = "enabled", havingValue = "true")
public class BedrockConfig {

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient(
            @Value("${docverify.bedrock.region:eu-west-2}") String region,
            @Value("${docverify.bedrock.access-key:}") String accessKey,
            @Value("${docverify.bedrock.secret-key:}") String secretKey,
            @Value("${docverify.bedrock.credentials-source:default}") String credentialsSource) {

        var builder = BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(60))
                        .apiCallAttemptTimeout(Duration.ofSeconds(55))
                        .build());

        if (accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        } else if ("instance-profile".equalsIgnoreCase(credentialsSource)) {
            // Use the EC2 instance role even if other AWS keys are present in the environment.
            builder.credentialsProvider(InstanceProfileCredentialsProvider.create());
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }
}
