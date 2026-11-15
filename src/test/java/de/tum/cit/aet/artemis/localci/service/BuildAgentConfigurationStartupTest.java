package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.util.concurrent.ThreadPoolExecutor;

import jakarta.annotation.PostConstruct;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

import com.github.dockerjava.api.DockerClient;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;

class BuildAgentConfigurationStartupTest {

    @Test
    void installsOverridesBeforeAnEagerConsumerRegistersItsTasks() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("buildagent");
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "artemis.continuous-integration.docker-connection-uri=unix:///var/run/docker.sock",
                    "artemis.continuous-integration.build-runner=kubernetes", "artemis.continuous-integration.specify-concurrent-builds=true",
                    "artemis.continuous-integration.concurrent-build-size=1");
            context.registerBean(BuildAgentConfiguration.class, () -> spy(new BuildAgentConfiguration(mock(ProgrammingLanguageConfiguration.class))),
                    definition -> definition.setDestroyMethodName("closeBuildAgentServices"));
            context.register(TestBuildAgentConfiguration.class, StartupConsumer.class);
            context.refresh();

            StartupConsumer consumer = context.getBean(StartupConsumer.class);
            assertThat(consumer.clientAtStartup).isSameAs(consumer.client);
            assertThat(consumer.dockerAvailableAtStartup).isTrue();
            assertThat(consumer.executorAtStartup).isSameAs(consumer.executor);
        }
    }

    static class StartupConsumer {

        private final BuildAgentConfiguration configuration;

        private final BuildAgentConfigurationOverrides overrides;

        private final DockerClient client = mock(DockerClient.class);

        private final ThreadPoolExecutor executor = mock(ThreadPoolExecutor.class);

        private DockerClient clientAtStartup;

        private boolean dockerAvailableAtStartup;

        private ThreadPoolExecutor executorAtStartup;

        StartupConsumer(BuildAgentConfiguration configuration, BuildAgentConfigurationOverrides overrides) {
            this.configuration = configuration;
            this.overrides = overrides;
        }

        @PostConstruct
        void registerTasks() {
            overrides.useDockerClient(client);
            overrides.useBuildExecutor(executor);
            // A programmatically scheduled consumer can read these as soon as its PostConstruct runs.
            clientAtStartup = configuration.getDockerClient();
            dockerAvailableAtStartup = configuration.isDockerAvailable();
            executorAtStartup = configuration.getBuildExecutor();
        }
    }
}
