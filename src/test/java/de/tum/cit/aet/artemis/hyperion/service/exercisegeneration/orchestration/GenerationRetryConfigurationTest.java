package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.Executor;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionAgentProperties;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationAccountingState;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class GenerationRetryConfigurationTest {

    @ParameterizedTest
    @CsvSource({ "0, 2", "1, 0", "0, ", "0, 3", "1, ", "3, 3" })
    void coreRetrySettingsDoNotChangeWorkerAccountingLifecycle(int common, Integer chat) {
        var runner = new ApplicationContextRunner().withInitializer(context -> {
            context.getEnvironment().setActiveProfiles("core", "localci", "localvc");
            context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
        }).withUserConfiguration(GenerationJobService.class, Dependencies.class)
                .withPropertyValues("artemis.hyperion.enabled=true", "artemis.hyperion.exercise-generation.enabled=true", "spring.ai.model.chat=none",
                        "spring.ai.openai.max-retries=" + common)
                .withBean(DistributedDataProvider.class, LocalDataProviderService::new).withBean("taskExecutor", Executor.class, () -> Runnable::run);
        if (chat != null) {
            runner = runner.withPropertyValues("spring.ai.openai.chat.max-retries=" + chat);
        }
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ChatModel.class).doesNotHaveBean(OpenAiChatProperties.class).doesNotHaveBean(OpenAiCommonProperties.class);
            var jobs = context.getBean(GenerationJobService.class);
            var owner = new User();
            owner.setLogin("owner");
            var exercise = new ProgrammingExercise();
            exercise.setId(1L);

            String jobId = jobs.startJob(owner, exercise, "test", GenerationMode.GENERATE);

            assertThat(jobs.getStatus(owner, exercise)).hasValueSatisfying(status -> assertThat(status.accountingState()).isEqualTo(ExerciseGenerationAccountingState.PENDING));

            jobs.recordEvent(exercise.getId(), jobId, ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "No model calls were made."), true);
            jobs.sealTokenAccountingOnWorkerExit(exercise.getId(), jobId);

            assertThat(jobs.getStatus(owner, exercise)).hasValueSatisfying(status -> assertThat(status.accountingState()).isEqualTo(ExerciseGenerationAccountingState.COMPLETE));
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Dependencies {

        @Bean
        GenerationTokenUsageService tokenUsage() {
            return mock(GenerationTokenUsageService.class);
        }

        @Bean
        HyperionGenerationBudgetService budget() {
            return mock(HyperionGenerationBudgetService.class);
        }

        @Bean
        HyperionAgentProperties agentProperties() {
            return new HyperionAgentProperties();
        }

        @Bean
        HyperionEffortProfileService effortProfiles() {
            return mock(HyperionEffortProfileService.class);
        }

    }
}
