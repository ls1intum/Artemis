package de.tum.cit.aet.artemis.core.config;

import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAutoConfigurationUtil;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;

/** Keeps OpenAI connection settings and per-request options consistent. */
@Lazy
@Configuration
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai", matchIfMissing = true)
@EnableConfigurationProperties({ OpenAiCommonProperties.class, OpenAiChatProperties.class })
public class OpenAiChatModelConfiguration {

    /**
     * Builds the model with the same resolved options as its SDK clients. Spring AI 2.0.1's auto-configuration otherwise
     * supplies a default 60-second request timeout that overrides the configured client timeout.
     */
    @Bean
    @Lazy
    @ConditionalOnMissingBean(OpenAiChatModel.class)
    OpenAiChatModel openAiChatModel(OpenAiCommonProperties common, OpenAiChatProperties chat, ToolCallingManager toolCallingManager,
            ObjectProvider<ObservationRegistry> observations, ObjectProvider<MeterRegistry> meters, ObjectProvider<ChatModelObservationConvention> convention,
            ObjectProvider<OpenAiHttpClientBuilderCustomizer> customizers) {
        var connection = OpenAiAutoConfigurationUtil.resolveCommonProperties(common, chat);
        OpenAiChatOptions options = chat.toOptions().mutate().baseUrl(connection.getBaseUrl()).apiKey(connection.getApiKey()).credential(connection.getCredential())
                .deploymentName(connection.getMicrosoftDeploymentName()).microsoftFoundryServiceVersion(connection.getMicrosoftFoundryServiceVersion())
                .organizationId(connection.getOrganizationId()).microsoftFoundry(connection.isMicrosoftFoundry()).gitHubModels(connection.isGitHubModels())
                .timeout(connection.getTimeout()).maxRetries(connection.getMaxRetries()).proxy(connection.getProxy()).customHeaders(connection.getCustomHeaders()).build();
        OpenAiChatModel model = OpenAiChatModel.builder().options(options).toolCallingManager(toolCallingManager)
                .observationRegistry(observations.getIfUnique(() -> ObservationRegistry.NOOP))
                .meterRegistry(connection.isConnectionPoolMetricsEnabled() ? meters.getIfAvailable() : null).httpClientBuilderCustomizers(customizers.orderedStream().toList())
                .build();
        convention.ifAvailable(model::setObservationConvention);
        return model;
    }
}
