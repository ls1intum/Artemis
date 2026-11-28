package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.aiworker.api.SandboxApi;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationParameters;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SpecFidelityCritic;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

class WorkerChatConfigurationTest {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void presetTimeoutReachesTheActualProviderRequest(boolean presetOverride) {
        var actualTimeout = new AtomicReference<Duration>();
        var contextRunner = WorkerLlmTestContext.runner().withBean(SandboxApi.class, () -> mock(SandboxApi.class)).withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                .withBean(ObservationRegistry.class, () -> ObservationRegistry.NOOP)
                .withPropertyValues("artemis.llm.endpoints.test.timeout=10m", "artemis.llm.endpoints.test.max-retries=0")
                .withBean(OpenAiHttpClientBuilderCustomizer.class, () -> builder -> builder.interceptor(chain -> {
                    actualTimeout.set(Duration.ofNanos(chain.call().timeout().timeoutNanos()));
                    return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create("""
                            {"id":"chatcmpl-test","object":"chat.completion","created":1,"model":"test-model",
                             "choices":[{"index":0,"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}],
                             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                            """, MediaType.get("application/json"))).build();
                }));
        if (presetOverride) {
            contextRunner = contextRunner.withPropertyValues("artemis.llm.presets.testpreset.timeout=2m");
        }

        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            var parameters = new GenerationParameters("standard", 10, 100_000, Duration.ofMinutes(5), 128_000, "testpreset", true, "CONTINUOUS", 0.5);
            var adapter = context.getBean(JavaGradleGenerationAdapterService.class);
            var model = adapter.workerModel("testpreset");
            var settings = adapter.settings(parameters, model);
            var response = model.call(new Prompt("Reply OK", settings.chatOptions()));
            assertThat(response.getResult().getOutput().getText()).isEqualTo("OK");
            assertThat(response.getMetadata().<String>get(LlmClientService.PRESET_METADATA_KEY)).isEqualTo("testpreset");
            assertThat(actualTimeout.get()).isEqualTo(Duration.ofMinutes(presetOverride ? 2 : 10));

            actualTimeout.set(null);
            var critic = new SpecFidelityCritic(ChatClient.create(model), JsonMapper.builder().build()).forSettings(settings);
            critic.reviewSpecification("A bounded stack exercise", "A bounded stack with push and pop", null, () -> false);
            assertThat(actualTimeout.get()).isEqualTo(Duration.ofMinutes(presetOverride ? 2 : 10));
        });
    }
}
