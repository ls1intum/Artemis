package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import de.tum.cit.aet.artemis.aiworker.api.SandboxApi;
import de.tum.cit.aet.artemis.aiworker.config.telemetry.WorkerTelemetryConfiguration;
import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.service.worker.DefaultGenerationEngineService;
import de.tum.cit.aet.artemis.hyperion.service.worker.GenerationEngine;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

class WorkerModelConfigurationTest {

    @Test
    void createsTheActualProviderModelAndEngineWithoutAWebApplicationStack() {
        new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles("aiworker"))
                .withInitializer(context -> context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
                .withConfiguration(AutoConfigurations.of(ObservationAutoConfiguration.class, ToolCallingAutoConfiguration.class, OpenAiChatAutoConfiguration.class))
                .withUserConfiguration(JavaGradleGenerationAdapterService.class, WorkerTelemetryConfiguration.class, DefaultGenerationEngineService.class)
                .withBean(ObservationHandler.class, () -> new ObservationHandler<Observation.Context>() {

                    @Override
                    public boolean supportsContext(Observation.Context context) {
                        return true;
                    }
                }).withBean(SandboxApi.class, () -> mock(SandboxApi.class))
                .withPropertyValues("artemis.aiworker.workload=hyperion-generation", "artemis.aiworker.profile=java-gradle", "spring.ai.openai.api-key=test-only-not-used",
                        "spring.ai.openai.chat.options.model=test-model", "spring.ai.openai.max-retries=0")
                .run(context -> {
                    assertThat(context.getBean(GenerationEngine.class)).isInstanceOf(DefaultGenerationEngineService.class);
                    assertThat(context).hasNotFailed().hasSingleBean(ChatModel.class).hasSingleBean(JavaGradleGenerationAdapterService.class);
                    assertThat(context.getBean(ChatModel.class).getOptions().getModel()).isEqualTo("test-model");
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
