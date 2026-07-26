package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.HYPERION_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.HYPERION_EXERCISE_GENERATION_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.HyperionProviderFailureCooldownService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationOrchestrationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationPersistenceService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.web.HyperionExerciseGenerationResource;

class HyperionExerciseGenerationContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(GenerationConfiguration.class)
            .withPropertyValues("spring.profiles.active=" + String.join(",", PROFILE_CORE, PROFILE_LOCALCI, PROFILE_LOCALVC), HYPERION_ENABLED_PROPERTY_NAME + "=true");

    @Test
    void doesNotRegisterGenerationBeansWhenExerciseGenerationUsesDefaultOff() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(HyperionExerciseGenerationResource.class).doesNotHaveBean(GenerationOrchestrationService.class)
                    .doesNotHaveBean(GenerationPersistenceService.class).doesNotHaveBean(DifferentialVerificationService.class).doesNotHaveBean(AgentLoopRunner.class)
                    .doesNotHaveBean(HyperionProviderFailureCooldownService.class);
            assertThat(context.containsBean("hyperionGenerationExecutor")).isFalse();
        });
    }

    @Test
    void registersGenerationBeansWhenExerciseGenerationIsExplicitlyEnabled() {
        // Every generation bean here is @Lazy, so this only needs the HyperionExerciseGenerationEnabled condition to flip and the bean definitions to resolve their declared
        // type — it does not require constructing the full production dependency graph (sandbox, git, workspace services, etc.), mirroring the disabled-path test's mechanism
        // with the opposite property value.
        contextRunner.withPropertyValues(HYPERION_EXERCISE_GENERATION_ENABLED_PROPERTY_NAME + "=true").run(context -> {
            assertThat(context).hasSingleBean(HyperionExerciseGenerationResource.class).hasSingleBean(GenerationOrchestrationService.class)
                    .hasSingleBean(GenerationPersistenceService.class).hasSingleBean(DifferentialVerificationService.class).hasSingleBean(AgentLoopRunner.class)
                    .hasSingleBean(HyperionProviderFailureCooldownService.class);
            assertThat(context.containsBean("hyperionGenerationExecutor")).isTrue();
        });
    }

    @Lazy
    @Configuration(proxyBeanMethods = false)
    @Import({ HyperionExerciseGenerationResource.class, GenerationOrchestrationService.class, GenerationPersistenceService.class, DifferentialVerificationService.class,
            HyperionAsyncConfiguration.class, HyperionProviderFailureCooldownService.class })
    static class GenerationConfiguration {
    }
}
