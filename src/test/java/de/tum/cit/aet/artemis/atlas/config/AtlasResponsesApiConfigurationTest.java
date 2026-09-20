package de.tum.cit.aet.artemis.atlas.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.atlas.service.AtlasAgentDelegationService;
import de.tum.cit.aet.artemis.atlas.service.AtlasPromptTemplateService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AtlasResponsesApiConfigurationTest {

    private final ChatClient sharedChatClient = mock(ChatClient.class);

    private final ChatModel sharedChatModel = mock(ChatModel.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class, AtlasResponsesApiConfiguration.class)
            .withPropertyValues("artemis.atlas.enabled=true", "spring.ai.openai.api-key=test-key", "spring.ai.openai.base-url=http://localhost:1/v1")
            .withBean(JsonMapper.class, JsonMapper::new).withBean(MeterRegistry.class, SimpleMeterRegistry::new).withBean(ChatClient.class, () -> sharedChatClient)
            .withBean(ChatModel.class, () -> sharedChatModel).withBean(AtlasPromptTemplateService.class, () -> mock(AtlasPromptTemplateService.class))
            .withBean(AtlasAgentDelegationService.class);

    @Test
    void defaultsEnableResponsesWithoutReplacingSharedBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ChatClient.class).hasSingleBean(ChatModel.class);
            assertThat(context.getBean(ChatClient.class)).isSameAs(sharedChatClient);
            assertThat(context.getBean(ChatModel.class)).isSameAs(sharedChatModel);
            assertThat(context.getBean(AtlasResponsesApiConfiguration.AtlasResponsesChatClient.class).chatClient()).isNotSameAs(sharedChatClient);
            assertThat(context.getBean(AtlasAgentDelegationService.class).isOrchestratorAvailable()).isTrue();
            var properties = context.getBean(AtlasOrchestratorProperties.class);
            assertThat(properties.responsesApiEnabled()).isTrue();
            assertThat(properties.model()).isEqualTo("gpt-5.6-luna");
            assertThat(properties.reasoningEffort()).isEqualTo("xhigh");
        });
    }

    @Test
    void explicitFallbackDoesNotCreateResponsesBeans() {
        contextRunner.withPropertyValues("artemis.atlas.orchestrator.responses-api-enabled=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(AtlasResponsesApiConfiguration.AtlasResponsesChatClient.class);
            assertThat(context).doesNotHaveBean(AtlasResponsesApiConfiguration.ATLAS_RESPONSES_OPENAI_CLIENT);
            assertThat(context.getBean(ChatClient.class)).isSameAs(sharedChatClient);
            assertThat(context.getBean(AtlasAgentDelegationService.class).isOrchestratorAvailable()).isTrue();
        });
    }

    @Test
    void atlasDisabledDoesNotCreateResponsesBeans() {
        contextRunner.withPropertyValues("artemis.atlas.enabled=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(AtlasResponsesApiConfiguration.AtlasResponsesChatClient.class);
            assertThat(context).doesNotHaveBean(AtlasResponsesApiConfiguration.ATLAS_RESPONSES_OPENAI_CLIENT);
            assertThat(context.getBean(ChatClient.class)).isSameAs(sharedChatClient);
        });
    }

    @org.springframework.context.annotation.Lazy
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ AtlasOrchestratorProperties.class, AtlasAgentProperties.class, OpenAiCommonProperties.class })
    static class PropertiesConfiguration {
    }
}
