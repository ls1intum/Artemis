package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.course.config.CourseHomeAiConfiguration;
import de.tum.cit.aet.artemis.course.config.CourseHomeAiProperties;
import de.tum.cit.aet.artemis.course.service.home.CourseHomeAiClient;
import de.tum.cit.aet.artemis.course.service.home.CourseHomeAiPolicyService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class CourseHomeAiConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(CourseHomeAiConfiguration.class)
            .withPropertyValues("spring.profiles.active=core").withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean("interactiveModel", ChatModel.class, () -> mock(ChatModel.class)).withBean("interactiveClient", ChatClient.class, () -> mock(ChatClient.class))
            .withBean("interactiveExecutor", Executor.class, () -> Runnable::run);

    @Test
    void defaultsAndIncompleteConfigurationHaveNoAdapter() {
        runner.run(c -> assertThat(c).doesNotHaveBean(CourseHomeAiClient.class));
        runner.withPropertyValues("artemis.course-home.ai.enabled=true", "artemis.course-home.ai.location=LOCAL").run(c -> assertThat(c).doesNotHaveBean(CourseHomeAiClient.class));
    }

    @Test
    void enabledAdapterDoesNotChangeSharedModelClientOrExecutorBeans() {
        runner.withPropertyValues("artemis.course-home.ai.enabled=true", "artemis.course-home.ai.location=LOCAL", "artemis.course-home.ai.base-url=http://127.0.0.1:1/v1",
                "artemis.course-home.ai.api-key=not-a-real-key", "artemis.course-home.ai.model=test").run(c -> {
                    assertThat(c).hasSingleBean(CourseHomeAiClient.class);
                    assertThat(c.getBeansOfType(ChatModel.class)).containsOnlyKeys("interactiveModel");
                    assertThat(c.getBeansOfType(ChatClient.class)).containsOnlyKeys("interactiveClient");
                    assertThat(c.getBeansOfType(Executor.class)).containsOnlyKeys("interactiveExecutor");
                });
    }

    @ParameterizedTest
    @CsvSource({ "LOCAL,LOCAL_AI,true", "LOCAL,CLOUD_AI,true", "CLOUD,LOCAL_AI,false", "CLOUD,CLOUD_AI,true", "LOCAL,NO_AI,false", "CLOUD,NO_AI,false" })
    void consentAndProviderLocationAreAuthoritative(String location, AiSelectionDecision decision, boolean expected) {
        var preferences = mock(UserAiPreferenceService.class);
        when(preferences.findDecision(7)).thenReturn(decision);
        var props = new CourseHomeAiProperties(true, location, "http://127.0.0.1:1/v1", "test-key", "test");
        var policy = new CourseHomeAiPolicyService(props, preferences, Optional.of(mock(CourseHomeAiClient.class)));
        assertThat(policy.canGenerate(7)).isEqualTo(expected);
    }

    @Test
    void undecidedAndMissingProvidersNeverGenerate() {
        var preferences = mock(UserAiPreferenceService.class);
        var props = new CourseHomeAiProperties(true, "LOCAL", "http://127.0.0.1:1/v1", "test-key", "test");
        assertThat(new CourseHomeAiPolicyService(props, preferences, Optional.empty()).canGenerate(7)).isFalse();
        verifyNoInteractions(preferences);
        assertThat(new CourseHomeAiPolicyService(props, preferences, Optional.of(mock(CourseHomeAiClient.class))).canGenerate(7)).isFalse();
    }

    @Test
    void providerDiagnosticsNeverContainCredentials() {
        var props = new CourseHomeAiProperties(true, "LOCAL", "http://localhost/v1", "secret-value", "test");
        assertThat(props.toString()).doesNotContain("secret-value");
    }
}
