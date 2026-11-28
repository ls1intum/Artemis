package de.tum.cit.aet.artemis.core.service.llm;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.config.llm.LlmEnabled;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import io.micrometer.observation.ObservationRegistry;

/**
 * Routes the chat models of all LLM presets to the mocked {@link ChatModel} of an integration test base, so no test reaches a provider.
 * The presets themselves stay real: an unknown preset still fails, and every response carries its preset name for cost accounting.
 * Without a mocked chat model in the context, the presets get their real models.
 */
@TestConfiguration
public class MockChatModelLlmConfiguration {

    /**
     * @return the factory that hands out the mocked chat model under each preset
     */
    @Bean
    @Primary
    @Lazy
    @Conditional(LlmEnabled.class)
    public LlmClientService mockChatModelLlmClientService(LlmProperties properties, JsonMapper jsonMapper, ObservationRegistry observationRegistry,
            ObjectProvider<ChatModel> mockedChatModel) {
        return new LlmClientService(properties, jsonMapper, observationRegistry) {

            @Override
            public ChatModel chatModel(String presetName) {
                ChatModel mock = mockedChatModel.getIfUnique();
                if (mock == null) {
                    return super.chatModel(presetName);
                }
                preset(presetName);
                return new PresetTaggingChatModel(mock, presetName);
            }
        };
    }
}
