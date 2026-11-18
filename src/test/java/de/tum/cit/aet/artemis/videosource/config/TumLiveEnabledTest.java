package de.tum.cit.aet.artemis.videosource.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.mock.env.MockEnvironment;

class TumLiveEnabledTest {

    private final TumLiveEnabled condition = new TumLiveEnabled();

    @ParameterizedTest
    @ValueSource(strings = { "aiworker", "prod,aiworker" })
    void workerDoesNotLoadTumLiveWithInheritedUrl(String profiles) {
        MockEnvironment environment = new MockEnvironment().withProperty("artemis.tum-live.api-base-url", "https://tum.live/api");
        environment.setActiveProfiles(profiles.split(","));
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        assertThat(condition.matches(context, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "core", "buildagent", "prod,core", "aiworker,core" })
    void serverAndBuildAgentStillEnableTumLiveOnlyWithUrl(String profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles.split(","));
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        assertThat(condition.matches(context, null)).isFalse();
        environment.setProperty("artemis.tum-live.api-base-url", " ");
        assertThat(condition.matches(context, null)).isFalse();
        environment.setProperty("artemis.tum-live.api-base-url", "https://tum.live/api");
        assertThat(condition.matches(context, null)).isTrue();
    }
}
