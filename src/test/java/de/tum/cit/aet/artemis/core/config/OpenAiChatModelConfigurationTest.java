package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OpenAiChatModelConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(OpenAiChatModelConfiguration.class)
            .withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class)).withBean(ToolCallingManager.class, () -> ToolCallingManager.builder().build())
            .withPropertyValues("spring.ai.openai.base-url=http://localhost:1/v1", "spring.ai.openai.api-key=test-key", "spring.ai.openai.chat.model=test-model",
                    "spring.ai.openai.timeout=10m", "spring.ai.openai.max-retries=0");

    @Test
    void requestOptionsHonorConfiguredConnectionPolicy() {
        context.run(application -> {
            assertThat(application).hasNotFailed().hasSingleBean(OpenAiChatModel.class);
            var options = application.getBean(OpenAiChatModel.class).getOptions();
            assertThat(options.getTimeout()).isEqualTo(Duration.ofMinutes(10));
            assertThat(options.getMaxRetries()).isZero();
            assertThat(options.getModel()).isEqualTo("test-model");
            assertThat(options.getBaseUrl()).isEqualTo("http://localhost:1/v1");
        });
    }

    @Test
    void chatSpecificTimeoutTakesPrecedence() {
        context.withPropertyValues("spring.ai.openai.chat.timeout=3m").run(application -> {
            assertThat(application).hasNotFailed().hasSingleBean(OpenAiChatModel.class);
            assertThat(application.getBean(OpenAiChatModel.class).getOptions().getTimeout()).isEqualTo(Duration.ofMinutes(3));
        });
    }
}
