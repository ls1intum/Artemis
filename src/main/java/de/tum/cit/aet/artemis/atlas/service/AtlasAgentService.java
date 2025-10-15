package de.tum.cit.aet.artemis.atlas.service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;

/**
 * Service for Atlas Agent functionality with Azure OpenAI integration.
 * Handles chat interactions and competency-related AI assistance.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class AtlasAgentService {

    private static final Logger log = LoggerFactory.getLogger(AtlasAgentService.class);

    private final ChatClient chatClient;

    private final AtlasPromptTemplateService templateService;

    private final ToolCallbackProvider toolCallbackProvider;

    public AtlasAgentService(@Autowired(required = false) ChatClient chatClient, AtlasPromptTemplateService templateService,
            @Autowired(required = false) ToolCallbackProvider toolCallbackProvider) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.toolCallbackProvider = toolCallbackProvider;
    }

    /**
     * Process a chat message for the given course and return AI response with modification status.
     * Detects competency modifications by checking if the response contains specific keywords.
     *
     * @param message   The user's message
     * @param courseId  The course ID for context
     * @param sessionId The session ID (TODO: will be used for another PR for Memory implementation including db migration)
     * @return Result containing the AI response and competency modification flag
     */
    public CompletableFuture<AgentChatResult> processChatMessage(String message, Long courseId, String sessionId) {
        try {
            // Load system prompt from external template
            String resourcePath = "/prompts/atlas/agent_system_prompt.st";
            Map<String, String> variables = Map.of(); // No variables needed for this template
            String systemPrompt = templateService.render(resourcePath, variables);

            AzureOpenAiChatOptions options = AzureOpenAiChatOptions.builder().deploymentName("gpt-4o").temperature(1.0).build();
            log.info("Atlas Agent using deployment name: {} for course {} with session {}", options.getDeploymentName(), courseId, sessionId);

            ChatClientRequestSpec promptSpec = chatClient.prompt().system(systemPrompt).user(String.format("Course ID: %d\n\n%s", courseId, message)).options(options);

            // Add tools
            if (toolCallbackProvider != null) {
                promptSpec = promptSpec.toolCallbacks(toolCallbackProvider);
            }

            String response = promptSpec.call().content();

            // if response mentions creation/modification, set flag
            boolean competenciesModified = response != null && (response.toLowerCase().contains("created") || response.toLowerCase().contains("successfully created")
                    || response.toLowerCase().contains("competency titled"));

            log.info("Successfully processed chat message for course {} with session {} (competenciesModified={})", courseId, sessionId, competenciesModified);
            String finalResponse = response != null && !response.trim().isEmpty() ? response : "I apologize, but I couldn't generate a response.";
            return CompletableFuture.completedFuture(new AgentChatResult(finalResponse, competenciesModified));

        }
        catch (Exception e) {
            log.error("Error processing chat message for course {} with session {}: {}", courseId, sessionId, e.getMessage(), e);
            return CompletableFuture.completedFuture(new AgentChatResult("I apologize, but I'm having trouble processing your request right now. Please try again later.", false));
        }
    }

    /**
     * Check if the Atlas Agent service is available and properly configured.
     *
     * @return true if the service is ready, false otherwise
     */
    public boolean isAvailable() {
        try {
            return chatClient != null;
        }
        catch (Exception e) {
            log.warn("Atlas Agent service availability check failed: {}", e.getMessage());
            return false;
        }
    }
}
