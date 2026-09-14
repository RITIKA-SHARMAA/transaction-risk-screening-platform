package com.ritikasharma.risk.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class)
            .withPropertyValues("risk.security.jwt.issuer=test", "risk.security.jwt.access-token-ttl=PT15M");

    @Test
    void missingSecretFailsStartup() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shortSecretFailsStartup() {
        runner.withPropertyValues("risk.security.jwt.secret=too-short")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("must be set and at least 32 bytes"));
    }

    @Test
    void validSecretBindsAndIsMaskedInToString() {
        String secret = "0123456789abcdef0123456789abcdef";
        runner.withPropertyValues("risk.security.jwt.secret=" + secret)
                .run(context -> {
                    JwtProperties properties = context.getBean(JwtProperties.class);
                    assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
                    assertThat(properties.toString()).doesNotContain(secret);
                });
    }

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class Config {
    }
}
