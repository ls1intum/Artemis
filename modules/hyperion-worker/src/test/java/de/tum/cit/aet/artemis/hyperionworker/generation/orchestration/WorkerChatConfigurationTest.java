package de.tum.cit.aet.artemis.hyperionworker.generation.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationParameters;
import de.tum.cit.aet.artemis.hyperionworker.sandbox.InteractiveSandbox;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

class WorkerChatConfigurationTest {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void configuredTimeoutReachesTheActualProviderRequest(boolean chatOverride) {
        var actualTimeout = new AtomicReference<Duration>();
        var contextRunner = new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(context -> context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
                .withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class)).withUserConfiguration(GradleGenerationEngine.class)
                .withBean(InteractiveSandbox.class, () -> mock(InteractiveSandbox.class)).withBean(ObservationRegistry.class, () -> ObservationRegistry.NOOP)
                .withPropertyValues("spring.ai.openai.api-key=test-key", "spring.ai.openai.chat.model=test-model", "spring.ai.openai.timeout=10m", "spring.ai.openai.max-retries=0")
                .withBean(OpenAiHttpClientBuilderCustomizer.class, () -> builder -> builder.interceptor(chain -> {
                    actualTimeout.set(Duration.ofNanos(chain.call().timeout().timeoutNanos()));
                    return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create("""
                            {"id":"chatcmpl-test","object":"chat.completion","created":1,"model":"test-model",
                             "choices":[{"index":0,"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}],
                             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                            """, MediaType.get("application/json"))).build();
                }));
        if (chatOverride) {
            contextRunner = contextRunner.withPropertyValues("spring.ai.openai.chat.timeout=2m");
        }

        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            var parameters = new GenerationParameters("standard", 10, 100_000, Duration.ofMinutes(5), 128_000, null, null, null, null, null, true, "CONTINUOUS");
            var settings = context.getBean(GradleGenerationEngine.class).settings(parameters);
            var response = context.getBean(OpenAiChatModel.class).call(new Prompt("Reply OK", settings.chatOptions()));
            assertThat(response.getResult().getOutput().getText()).isEqualTo("OK");
            assertThat(actualTimeout.get()).isEqualTo(Duration.ofMinutes(chatOverride ? 2 : 10));
        });
    }
}
