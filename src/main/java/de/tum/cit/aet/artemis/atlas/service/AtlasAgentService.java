package de.tum.cit.aet.artemis.atlas.service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;

/**
 * Service for Atlas Agent functionality with Azure OpenAI integration.
 * Handles chat interactions and competency-related AI assistance.
 * Manages multi-agent orchestration between Main Agent and sub-agents (Competency Expert).
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class AtlasAgentService {

    private enum AgentType {
        MAIN_AGENT, COMPETENCY_EXPERT
    }

    private static final String DELEGATE_TO_COMPETENCY_EXPERT = "[DELEGATE_TO_COMPETENCY_EXPERT]";

    private static final String RETURN_TO_MAIN_AGENT = "[RETURN_TO_MAIN_AGENT]";

    // Track which agent is active for each session
    private final Map<String, AgentType> sessionAgentMap = new ConcurrentHashMap<>();

    private final ChatClient chatClient;

    private final AtlasPromptTemplateService templateService;

    private final ToolCallbackProvider mainAgentToolCallbackProvider;

    private final ToolCallbackProvider competencyExpertToolCallbackProvider;

    private final ChatMemory chatMemory;

    private final CompetencyExpertToolsService competencyExpertToolsService;

    public AtlasAgentService(@Autowired(required = false) ChatClient chatClient, AtlasPromptTemplateService templateService,
            @Autowired(required = false) @Qualifier("mainAgentToolCallbackProvider") ToolCallbackProvider mainAgentToolCallbackProvider,
            @Autowired(required = false) @Qualifier("competencyExpertToolCallbackProvider") ToolCallbackProvider competencyExpertToolCallbackProvider,
            @Autowired(required = false) ChatMemory chatMemory, @Autowired(required = false) CompetencyExpertToolsService competencyExpertToolsService) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.mainAgentToolCallbackProvider = mainAgentToolCallbackProvider;
        this.competencyExpertToolCallbackProvider = competencyExpertToolCallbackProvider;
        this.chatMemory = chatMemory;
        this.competencyExpertToolsService = competencyExpertToolsService;
    }

    /**
     * Process a chat message with multi-agent orchestration.
     * Routes to the appropriate agent based on session state and delegation markers.
     *
     * @param message   The user's message
     * @param courseId  The course ID for context
     * @param sessionId The session ID for chat memory and agent tracking
     * @return Result containing the AI response and competency modification flag
     */
    public CompletableFuture<AgentChatResult> processChatMessage(String message, Long courseId, String sessionId) {
        try {
            // Determine which agent should handle this message
            AgentType activeAgent = sessionAgentMap.getOrDefault(sessionId, AgentType.MAIN_AGENT);

            // Route to the appropriate agent
            String response;
            if (activeAgent == AgentType.COMPETENCY_EXPERT) {
                response = processWithCompetencyExpert(message, courseId, sessionId);
            }
            else {
                response = processWithMainAgent(message, courseId, sessionId);
            }

            // Check for delegation markers and update session state
            if (response.contains(DELEGATE_TO_COMPETENCY_EXPERT)) {
                sessionAgentMap.put(sessionId, AgentType.COMPETENCY_EXPERT);
                // Remove the marker from the response before returning to user
                response = response.replace(DELEGATE_TO_COMPETENCY_EXPERT, "").trim();

                // Immediately invoke Competency Expert with the original user message
                // This allows the expert to react to what the instructor already said
                String expertResponse = processWithCompetencyExpert(message, courseId, sessionId);
                response = response + "\n\n---\n\n" + expertResponse;
            }
            else if (response.contains(RETURN_TO_MAIN_AGENT)) {
                sessionAgentMap.put(sessionId, AgentType.MAIN_AGENT);
                // Remove the marker from the response before returning to user
                response = response.replace(RETURN_TO_MAIN_AGENT, "").trim();
            }

            // Check if competency was created
            boolean competenciesModified = competencyExpertToolsService != null && competencyExpertToolsService.wasCompetencyCreated();

            String finalResponse = response != null && !response.trim().isEmpty() ? response : "I apologize, but I couldn't generate a response.";

            return CompletableFuture.completedFuture(new AgentChatResult(finalResponse, competenciesModified));

        }
        catch (Exception e) {
            return CompletableFuture.completedFuture(new AgentChatResult("I apologize, but I'm having trouble processing your request right now. Please try again later.", false));
        }
    }

    /**
     * Process message with the Main Agent (Requirements Engineer/Orchestrator).
     *
     * @param message   The user's message
     * @param courseId  The course ID for context
     * @param sessionId The session ID for chat memory
     * @return The agent's response
     */
    private String processWithMainAgent(String message, Long courseId, String sessionId) {
        // Load main agent system prompt
        String resourcePath = "/prompts/atlas/agent_system_prompt.st";
        Map<String, String> variables = Map.of();
        String systemPrompt = templateService.render(resourcePath, variables);

        ToolCallingChatOptions options = AzureOpenAiChatOptions.builder().deploymentName("gpt-4o").temperature(1.0).build();

        ChatClientRequestSpec promptSpec = chatClient.prompt().system(systemPrompt).user(String.format("Course ID: %d\n\n%s", courseId, message)).options(options);

        // Add chat memory advisor
        if (chatMemory != null) {
            promptSpec = promptSpec.advisors(MessageChatMemoryAdvisor.builder(chatMemory).conversationId(sessionId).build());
        }

        // Add main agent tools
        if (mainAgentToolCallbackProvider != null) {
            promptSpec = promptSpec.toolCallbacks(mainAgentToolCallbackProvider);
        }

        // Execute the chat
        return promptSpec.call().content();
    }

    /**
     * Process message with the Competency Expert sub-agent.
     *
     * @param message   The user's message
     * @param courseId  The course ID for context
     * @param sessionId The session ID for chat memory
     * @return The agent's response
     */
    private String processWithCompetencyExpert(String message, Long courseId, String sessionId) {
        // Load competency expert system prompt
        String resourcePath = "/prompts/atlas/competency_expert_system_prompt.st";
        Map<String, String> variables = Map.of();
        String systemPrompt = templateService.render(resourcePath, variables);

        ToolCallingChatOptions options = AzureOpenAiChatOptions.builder().deploymentName("gpt-4o").temperature(1.0).build();

        ChatClientRequestSpec promptSpec = chatClient.prompt().system(systemPrompt).user(String.format("Course ID: %d\n\n%s", courseId, message)).options(options);

        // Add chat memory advisor
        if (chatMemory != null) {
            promptSpec = promptSpec.advisors(MessageChatMemoryAdvisor.builder(chatMemory).conversationId(sessionId).build());
        }

        // Add competency expert tools
        if (competencyExpertToolCallbackProvider != null) {
            promptSpec = promptSpec.toolCallbacks(competencyExpertToolCallbackProvider);
        }

        // Execute the chat
        return promptSpec.call().content();
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
            return false;
        }
    }
}
