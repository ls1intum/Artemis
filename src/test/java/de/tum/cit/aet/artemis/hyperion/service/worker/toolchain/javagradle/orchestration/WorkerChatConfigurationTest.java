package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.client.ResourceAccessException;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.aiworker.api.SandboxApi;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationParameters;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SpecFidelityCritic;
import io.micrometer.observation.ObservationRegistry;

class WorkerChatConfigurationTest {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void presetTimeoutReachesTheActualProviderRequest(boolean presetOverride) throws Exception {
        var provider = WorkerLlmTestContext.startProvider(exchange -> {
            try {
                Thread.sleep(600);
                WorkerLlmTestContext.reply(exchange, 200, WorkerLlmTestContext.SUCCESS);
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        try {
            var contextRunner = WorkerLlmTestContext.runner().withBean(SandboxApi.class, () -> mock(SandboxApi.class)).withBean(JsonMapper.class, JsonMapper::new)
                    .withBean(ObservationRegistry.class, () -> ObservationRegistry.NOOP)
                    .withPropertyValues(WorkerLlmTestContext.baseUrlProperty(provider), "artemis.llm.endpoints.test.timeout=150ms", "artemis.llm.endpoints.test.max-retries=0");
            if (presetOverride) {
                contextRunner = contextRunner.withPropertyValues("artemis.llm.presets.testpreset.timeout=5s");
            }
            contextRunner.run(context -> {
                assertThat(context).hasNotFailed();
                var parameters = new GenerationParameters("standard", 10, 100_000, Duration.ofMinutes(5), 128_000, "testpreset", true, "CONTINUOUS", 0.5);
                var adapter = context.getBean(JavaGradleGenerationAdapterService.class);
                var model = adapter.workerModel("testpreset");
                var settings = adapter.settings(parameters, model);
                var prompt = new Prompt("Reply OK", settings.chatOptions());
                if (presetOverride) {
                    var response = model.call(prompt);
                    assertThat(response.getResult().getOutput().getText()).isEqualTo("OK");
                    assertThat(response.getMetadata().<String>get(LlmClientService.PRESET_METADATA_KEY)).isEqualTo("testpreset");
                }
                else {
                    assertThatThrownBy(() -> model.call(prompt)).isInstanceOf(ResourceAccessException.class);
                }

                var received = new AtomicReference<ChatResponse>();
                var critic = new SpecFidelityCritic(ChatClient.create(model), JsonMapper.builder().build()).forSettings(settings);
                critic.reviewSpecification("A bounded stack exercise", "A bounded stack with push and pop", received::set, () -> false);
                assertThat(received.get() != null).isEqualTo(presetOverride);
            });
        }
        finally {
            provider.stop(0);
        }
    }
}
