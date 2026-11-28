package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;

import de.tum.cit.aet.artemis.aiworker.api.SandboxApi;
import de.tum.cit.aet.artemis.aiworker.config.telemetry.WorkerTelemetryConfiguration;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.service.worker.DefaultGenerationEngineService;
import de.tum.cit.aet.artemis.hyperion.service.worker.GenerationEngine;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

class WorkerModelConfigurationTest {

    @Test
    void createsTheActualProviderModelAndEngineWithoutAWebApplicationStack() {
        WorkerLlmTestContext.runner().withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, ObservationAutoConfiguration.class))
                .withUserConfiguration(WorkerTelemetryConfiguration.class, DefaultGenerationEngineService.class)
                .withBean(ObservationHandler.class, () -> new ObservationHandler<Observation.Context>() {

                    @Override
                    public boolean supportsContext(Observation.Context context) {
                        return true;
                    }
                }).withBean(SandboxApi.class, () -> mock(SandboxApi.class)).withPropertyValues("artemis.aiworker.profile=java-gradle", "artemis.llm.endpoints.test.max-retries=0")
                .run(context -> {
                    assertThat(context.getBean(GenerationEngine.class)).isInstanceOf(DefaultGenerationEngineService.class);
                    assertThat(context).hasNotFailed().hasSingleBean(JavaGradleGenerationAdapterService.class);
                    assertThat(context.getBean(LlmClientService.class).chatModel("testpreset").getOptions().getModel()).isEqualTo("test-model");
                    assertThat(context.getBean(JavaGradleGenerationAdapterService.class)
                            .requestCancel(new ExecutionIdentity("unused", 1, java.util.UUID.randomUUID(), "worker", java.util.UUID.randomUUID()))).isFalse();
                    var registry = context.getBean(ObservationRegistry.class);
                    var chat = ChatModelObservationContext.builder().prompt(new Prompt("not for export")).provider("openai").build();
                    Observation.createNotStarted("gen_ai.client.operation", () -> chat, registry).observe(() -> {
                    });
                    assertThat(chat.getLowCardinalityKeyValue("ai.span").getValue()).isEqualTo("true");
                    assertThat(chat.getHighCardinalityKeyValue("gen_ai.input.messages")).isNull();
                });
    }
}
