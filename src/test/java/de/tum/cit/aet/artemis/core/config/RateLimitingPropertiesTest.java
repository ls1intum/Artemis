package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RateLimitingPropertiesTest {

    /** Binds the properties the way the application does, without the rest of it. */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles(PROFILE_CORE))
            .withUserConfiguration(BindingOnly.class, RateLimitingProperties.class);

    @EnableConfigurationProperties
    static class BindingOnly {
    }

    @Test
    void rateLimitingIsEnabledUnlessAnOperatorSwitchesItOff() {
        assertThat(new RateLimitingProperties().isEnabled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "-1" })
    void aLimitThatIsNotPositiveIsRejectedWhenTheSettingsAreBound(String value) {
        contextRunner.withPropertyValues("artemis.rate-limiting.re-evaluation-requests-per-minute=" + value)
                .run(context -> assertThatThrownBy(() -> context.getBean(RateLimitingProperties.class)).hasStackTraceContaining("reEvaluationRequestsPerMinute"));
    }

    @Test
    void aLimitThatIsNotPositiveIsRejectedForEveryRateLimitType() {
        contextRunner.withPropertyValues("artemis.rate-limiting.authentication-requests-per-minute=0")
                .run(context -> assertThatThrownBy(() -> context.getBean(RateLimitingProperties.class)).hasStackTraceContaining("authenticationRequestsPerMinute"));
    }

    @Test
    void aPositiveLimitIsKeptAndAnUnsetLimitSelectsTheDefault() {
        contextRunner.withPropertyValues("artemis.rate-limiting.re-evaluation-requests-per-minute=3").run(context -> {
            RateLimitingProperties properties = context.getBean(RateLimitingProperties.class);
            assertThat(properties.getReEvaluationRequestsPerMinute()).isEqualTo(3);
            assertThat(properties.getAuthenticationRequestsPerMinute()).isNull();
        });
    }
}
