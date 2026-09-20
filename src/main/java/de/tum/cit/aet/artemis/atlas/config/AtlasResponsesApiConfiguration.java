package de.tum.cit.aet.artemis.atlas.config;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.openai.client.OpenAIClient;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.atlas.service.AtlasResponsesChatModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;

/**
 * Isolates the optional Atlas Responses client from Artemis's shared Spring AI beans.
 *
 * <p>
 * The raw provider-aware OpenAI client is named, while the configured ChatClient is wrapped in
 * {@link AtlasResponsesChatClient}. This keeps the Responses adapter out of unqualified
 * {@code ChatModel}/{@code ChatClient} injection used by interactive Atlas and Hyperion flows.
 */
@Lazy
@Configuration(proxyBeanMethods = false)
@Conditional(AtlasEnabled.class)
@ConditionalOnProperty(prefix = "artemis.atlas.orchestrator", name = "responses-api-enabled", havingValue = "true", matchIfMissing = true)
public class AtlasResponsesApiConfiguration {

    /** Name of the provider-aware raw client bean used by the Atlas adapter. */
    public static final String ATLAS_RESPONSES_OPENAI_CLIENT = "atlasResponsesOpenAIClient";

    /** Name of the holder bean containing the Responses-specific ChatClient. */
    public static final String ATLAS_RESPONSES_CHAT_CLIENT = "atlasResponsesChatClient";

    /**
     * Creates a holder for the Responses-specific ChatClient.
     *
     * @param openAIClient provider-aware synchronous client
     * @param properties   shared OpenAI model configuration
     * @param objectMapper JSON parser used for tool schemas
     * @return isolated Responses ChatClient holder
     */
    @Bean(name = ATLAS_RESPONSES_CHAT_CLIENT)
    @Lazy
    AtlasResponsesChatClient atlasResponsesChatClient(@Qualifier(ATLAS_RESPONSES_OPENAI_CLIENT) OpenAIClient openAIClient, OpenAiCommonProperties properties,
            JsonMapper objectMapper) {
        AtlasResponsesChatModel chatModel = new AtlasResponsesChatModel(openAIClient, objectMapper, properties.getModel());
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        return new AtlasResponsesChatClient(chatClient);
    }

    /**
     * Reuses Spring AI's provider-aware client setup so OpenAI and Microsoft Foundry credentials,
     * base URLs, timeouts, proxies, headers, metrics, and HTTP customizers match the regular
     * {@code OpenAiChatModel}.
     *
     * @param properties          common OpenAI properties
     * @param observationRegistry optional observation registry
     * @param meterRegistry       metrics registry
     * @param customizerProvider  HTTP client customizers
     * @return configured synchronous OpenAI client
     */
    @Bean(name = ATLAS_RESPONSES_OPENAI_CLIENT, destroyMethod = "close")
    @Lazy
    OpenAIClient atlasResponsesOpenAIClient(OpenAiCommonProperties properties, ObjectProvider<ObservationRegistry> observationRegistry, MeterRegistry meterRegistry,
            ObjectProvider<OpenAiHttpClientBuilderCustomizer> customizerProvider) {
        ObservationRegistry registry = observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP);
        List<OpenAiHttpClientBuilderCustomizer> customizers = customizerProvider.orderedStream().toList();
        return OpenAiSetup.setupSyncClient(properties.getBaseUrl(), properties.getApiKey(), properties.getCredential(), properties.getMicrosoftDeploymentName(),
                properties.getMicrosoftFoundryServiceVersion(), properties.getOrganizationId(), properties.isMicrosoftFoundry(), properties.isGitHubModels(), properties.getModel(),
                properties.getTimeout(), properties.getMaxRetries(), properties.getProxy(), properties.getCustomHeaders(), registry, meterRegistry, customizers);
    }

    /**
     * Holder that prevents the optional Responses ChatClient from becoming a shared unqualified bean.
     *
     * @param chatClient ChatClient configured with the Responses adapter and native tool advisor
     */
    public record AtlasResponsesChatClient(ChatClient chatClient) {
    }
}
