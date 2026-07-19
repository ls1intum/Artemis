package de.tum.cit.aet.artemis.buildagent.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.service.BuildAgentInformationService;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandboxReaperService;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandboxRelayHandler;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandboxService;
import de.tum.cit.aet.artemis.buildagent.service.SharedQueueProcessingService;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

class GenerationSandboxHostingContextTest {

    private static final String MAX_GENERATION_SANDBOX_SLOTS_PROPERTY = "artemis.continuous-integration.build-agent.max-generation-sandbox-slots";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(SandboxHostingConfiguration.class)
            .withPropertyValues("spring.profiles.active=" + PROFILE_BUILDAGENT, "artemis.continuous-integration.build-agent.short-name=test-agent")
            .withBean(BuildAgentConfiguration.class, () -> mock(BuildAgentConfiguration.class))
            .withBean(DistributedDataAccessService.class, () -> mock(DistributedDataAccessService.class))
            .withBean(SharedQueueProcessingService.class, () -> mock(SharedQueueProcessingService.class))
            .withBean(BuildAgentInformationService.class, () -> mock(BuildAgentInformationService.class))
            .withBean("taskScheduler", TaskScheduler.class, () -> mock(TaskScheduler.class));

    @Test
    void doesNotInstantiateHostingBeansWhenCapacityIsMissing() {
        contextRunner.run(context -> assertHostingDisabled(context));
    }

    @Test
    void doesNotInstantiateHostingBeansWhenCapacityIsZero() {
        contextRunner.withPropertyValues(MAX_GENERATION_SANDBOX_SLOTS_PROPERTY + "=0").run(context -> assertHostingDisabled(context));
    }

    @Test
    void instantiatesHostingBeansWhenCapacityIsPositiveAndKeepsSandboxServiceLazy() {
        contextRunner.withPropertyValues(MAX_GENERATION_SANDBOX_SLOTS_PROPERTY + "=1").run(context -> {
            assertThat(context).hasSingleBean(InteractiveSandboxRelayHandler.class).hasSingleBean(InteractiveSandboxReaperService.class);
            assertSandboxServiceIsLazy(context);
            context.getBean(InteractiveSandboxService.class);
            context.getBean(InteractiveSandboxRelayHandler.class).shutdown();
        });
    }

    private static void assertHostingDisabled(AssertableApplicationContext context) {
        assertThat(context).doesNotHaveBean(InteractiveSandboxRelayHandler.class).doesNotHaveBean(InteractiveSandboxReaperService.class);
        assertSandboxServiceIsLazy(context);
    }

    private static void assertSandboxServiceIsLazy(AssertableApplicationContext context) {
        String sandboxServiceBeanName = context.getBeanNamesForType(InteractiveSandboxService.class)[0];
        assertThat(context.getBeanFactory().containsSingleton(sandboxServiceBeanName)).isFalse();
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ InteractiveSandboxRelayHandler.class, InteractiveSandboxReaperService.class })
    static class SandboxHostingConfiguration {

        @Bean
        @Lazy
        InteractiveSandboxService interactiveSandboxService() {
            return mock(InteractiveSandboxService.class);
        }
    }
}
