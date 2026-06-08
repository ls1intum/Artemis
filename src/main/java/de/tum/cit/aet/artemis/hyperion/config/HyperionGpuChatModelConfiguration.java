package de.tum.cit.aet.artemis.hyperion.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.GpuEndpointChatModel;

/**
 * Registers an OpenAI-compatible OpenWebUI endpoint (e.g. the TUM gpt-oss-120b GPU deployment) as the Hyperion {@link ChatModel}, so a running Artemis instance can drive real
 * agentic exercise generation against that endpoint without the Azure OpenAI starter. Activated ONLY when an {@code artemis.hyperion.gpu.api-key} is configured, so a normal
 * deployment (no key) is unaffected and keeps using whichever Spring AI starter {@code spring.ai.model.chat} selects.
 * <p>
 * When this bean is active, set {@code spring.ai.model.chat=none} so the Azure/OpenAI starter does not also contribute a competing {@code ChatModel} — the {@code AgentLoopRunner}
 * injects the full {@code Collection<ChatModel>} and uses the first, so a single deterministic bean is required. The bean is marked {@link Primary} so any shared
 * {@code ChatClient}
 * also resolves to it.
 */
@Lazy
@Configuration
@Conditional(HyperionEnabled.class)
@ConditionalOnProperty(prefix = "artemis.hyperion.gpu", name = "api-key")
public class HyperionGpuChatModelConfiguration {

    /**
     * @param baseUrl the endpoint base URL (chat completions are POSTed to {@code <baseUrl>/api/chat/completions}); defaults to the TUM GPU deployment
     * @param apiKey  the bearer token for the endpoint (its presence activates this configuration)
     * @param model   the model identifier the endpoint expects; defaults to {@code openai/gpt-oss-120b}
     * @return the GPU-backed chat model used by the Hyperion agent loop
     */
    @Bean
    @Primary
    public ChatModel hyperionGpuChatModel(@Value("${artemis.hyperion.gpu.base-url:https://gpu.ase.cit.tum.de}") String baseUrl,
            @Value("${artemis.hyperion.gpu.api-key}") String apiKey, @Value("${artemis.hyperion.gpu.model:openai/gpt-oss-120b}") String model) {
        return new GpuEndpointChatModel(baseUrl, apiKey, model);
    }
}
