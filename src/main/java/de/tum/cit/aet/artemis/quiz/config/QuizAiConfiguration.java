package de.tum.cit.aet.artemis.quiz.config;

import java.util.List;

import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;

import io.micrometer.observation.ObservationRegistry;

@Configuration
public class QuizAiConfiguration {

    @Value("${spring.ai.azure.openai.endpoint}")
    private String endpoint;

    @Value("${spring.ai.azure.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.azure.openai.chat.options.deployment-name}")
    private String deploymentName;

    @Value("${spring.ai.azure.openai.chat.options.api-version:2025-01-01-preview}")
    private String apiVersion;

    @Bean
    public AzureOpenAiChatModel azureOpenAiChatModel(ObservationRegistry observationRegistry) {

        var clientBuilder = new OpenAIClientBuilder().endpoint(endpoint).credential(new AzureKeyCredential(apiKey));

        var options = AzureOpenAiChatOptions.builder().deploymentName(deploymentName).temperature(0.7).maxTokens(1500).build();

        // Minimal dummy ToolCallingManager: does nothing for now
        ToolCallingManager toolManager = new ToolCallingManager() {

            @Override
            public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
                return List.of();
            }

            @Override
            public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
                return null;
            }
        };

        // Spring AI 1.0.3: predicate takes (modelName, toolDefinition)
        ToolExecutionEligibilityPredicate eligibilityPredicate = (modelName, toolDefinition) -> false;

        return new AzureOpenAiChatModel(clientBuilder, options, toolManager, observationRegistry, eligibilityPredicate);
    }

    @Bean
    public ChatClient chatClient(AzureOpenAiChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
