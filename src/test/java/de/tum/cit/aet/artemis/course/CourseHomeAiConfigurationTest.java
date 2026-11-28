package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessResourceFailureException;

import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.core.config.llm.LlmPresetAssignments;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.config.llm.LlmTestCatalog;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.course.config.CourseHomeAiConfiguration;
import de.tum.cit.aet.artemis.course.config.CourseHomeAiProperties;
import de.tum.cit.aet.artemis.course.config.CourseHomeInfoContributor;
import de.tum.cit.aet.artemis.course.service.home.CourseHomeAiClient;
import de.tum.cit.aet.artemis.course.service.home.CourseHomeAiPolicyService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class CourseHomeAiConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(CourseHomeAiConfiguration.class)
            .withPropertyValues("spring.profiles.active=core").withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean("interactiveModel", ChatModel.class, () -> mock(ChatModel.class)).withBean("interactiveClient", ChatClient.class, () -> mock(ChatClient.class))
            .withBean("interactiveExecutor", Executor.class, () -> Runnable::run).withBean(LlmClientService.class, CourseHomeAiConfigurationTest::llmClientService);

    private static LlmClientService llmClientService() {
        LlmClientService llmClientService = mock(LlmClientService.class);
        when(llmClientService.preset("welcome")).thenReturn(LlmTestCatalog.preset("local", "test", LlmProperties.Api.RESPONSES));
        when(llmClientService.isolatedChatModel(eq("welcome"), any(Duration.class), anyInt())).thenReturn(mock(ChatModel.class));
        return llmClientService;
    }

    @Test
    void defaultsAndIncompleteConfigurationHaveNoAdapter() {
        runner.run(c -> assertThat(c).doesNotHaveBean(CourseHomeAiClient.class));
        runner.withPropertyValues("artemis.course-home.ai.enabled=true", "artemis.course-home.ai.location=LOCAL").run(c -> assertThat(c).doesNotHaveBean(CourseHomeAiClient.class));
    }

    @Test
    void optionalPropertiesStayLazyEvenWhenTheInfoContributorIsInstantiated() {
        runner.withUserConfiguration(CourseHomeInfoContributor.class).run(context -> {
            var names = context.getBeanFactory().getBeanNamesForType(CourseHomeAiProperties.class, false, false);
            assertThat(names).hasSize(1);
            assertThat(context.getBeanFactory().containsSingleton(names[0])).isFalse();
            var contributor = context.getBean(CourseHomeInfoContributor.class);
            assertThat(context.getBeanFactory().containsSingleton(names[0])).isFalse();
            var builder = new org.springframework.boot.actuate.info.Info.Builder();
            contributor.contribute(builder);
            assertThat(builder.build().getDetails()).containsEntry("courseHomeAiAvailable", false);
        });
    }

    @Test
    void enabledAdapterDoesNotChangeSharedModelClientOrExecutorBeans() {
        runner.withPropertyValues("artemis.course-home.ai.enabled=true", "artemis.course-home.ai.location=LOCAL", "artemis.course-home.ai.llm-preset=welcome").run(c -> {
            assertThat(c).hasSingleBean(CourseHomeAiClient.class);
            assertThat(c.getBean(CourseHomeAiClient.class)).isInstanceOf(de.tum.cit.aet.artemis.course.service.home.SpringAiCourseHomeClient.class);
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
        var props = new CourseHomeAiProperties(true, location, "welcome");
        var policy = new CourseHomeAiPolicyService(props, preferences, Optional.of(mock(CourseHomeAiClient.class)));
        assertThat(policy.canGenerate(7)).isEqualTo(expected);
    }

    @Test
    void undecidedAndMissingProvidersNeverGenerate() {
        var preferences = mock(UserAiPreferenceService.class);
        var props = new CourseHomeAiProperties(true, "LOCAL", "welcome");
        assertThat(new CourseHomeAiPolicyService(props, preferences, Optional.empty()).canGenerate(7)).isFalse();
        verifyNoInteractions(preferences);
        assertThat(new CourseHomeAiPolicyService(props, preferences, Optional.of(mock(CourseHomeAiClient.class))).canGenerate(7)).isFalse();
    }

    @Test
    void anEnabledHomeRequiresItsPresetAndADisabledOneNone() {
        runner.withPropertyValues("artemis.course-home.ai.enabled=true", "artemis.course-home.ai.llm-preset=welcome").run(c -> {
            assertThat(c.getBean(LlmPresetAssignments.class).llmPresetAssignments()).singleElement().satisfies(assignment -> {
                assertThat(assignment.propertyKey()).isEqualTo("artemis.course-home.ai.llm-preset");
                assertThat(assignment.preset()).isEqualTo("welcome");
            });
        });
        runner.withPropertyValues("artemis.course-home.ai.enabled=true").run(
                c -> assertThat(c.getBean(LlmPresetAssignments.class).llmPresetAssignments()).singleElement().satisfies(assignment -> assertThat(assignment.preset()).isNull()));
        runner.run(c -> assertThat(c).doesNotHaveBean(LlmPresetAssignments.class));
        // every spelling that Spring binds as true also requires the preset, and the client uses the name the validation checked
        runner.withPropertyValues("artemis.course-home.ai.enabled=on", "artemis.course-home.ai.location=LOCAL", "artemis.course-home.ai.llm-preset= welcome ").run(c -> {
            assertThat(c.getBean(LlmPresetAssignments.class).llmPresetAssignments()).singleElement().satisfies(assignment -> assertThat(assignment.preset()).isEqualTo("welcome"));
            assertThat(c).hasSingleBean(CourseHomeAiClient.class);
        });
        assertThat(new CourseHomeAiProperties(true, "LOCAL", " welcome ").llmPreset()).isEqualTo("welcome");
        assertThat(new CourseHomeAiProperties(true, "LOCAL", "  ").isConfigured()).isFalse();
    }

    @Test
    void preferenceDatabaseFailureFallsBackWithoutTreatingItAsConsent() {
        var preferences = mock(UserAiPreferenceService.class);
        when(preferences.findDecision(7)).thenThrow(new DataAccessResourceFailureException("unavailable"));
        var props = new CourseHomeAiProperties(true, "LOCAL", "welcome");
        var policy = new CourseHomeAiPolicyService(props, preferences, Optional.of(mock(CourseHomeAiClient.class)));
        assertThat(policy.canGenerate(7)).isFalse();
    }
}
