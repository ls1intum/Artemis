package de.tum.cit.aet.artemis.atlas.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Nullable;

import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.BatchCompetencyPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.SingleCompetencyPreviewResponseDTO;

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

    private static final String DELEGATE_TO_COMPETENCY_EXPERT = "%%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%";

    private static final String CREATE_APPROVED_COMPETENCY = "[CREATE_APPROVED_COMPETENCY]";

    private static final String RETURN_TO_MAIN_AGENT = "%%ARTEMIS_RETURN_TO_MAIN_AGENT%%";

    // Track which agent is active for each session
    private final Map<String, AgentType> sessionAgentMap = new ConcurrentHashMap<>();

    private final ChatClient chatClient;

    private final AtlasPromptTemplateService templateService;

    private final ToolCallbackProvider mainAgentToolCallbackProvider;

    private final ToolCallbackProvider competencyExpertToolCallbackProvider;

    private final ChatMemory chatMemory;

    // Session-scoped cache for competency operation data (single source of truth for refinement and persistence)
    // Stores the actual competency data that was last previewed, enabling deterministic refinements
    private final Map<String, List<CompetencyExpertToolsService.CompetencyOperation>> sessionCompetencyDataCache = new ConcurrentHashMap<>();

    public Boolean getCompetencyModifiedInCurrentRequest() {
        return competencyModifiedInCurrentRequest.get();
    }

    private static final ThreadLocal<Boolean> competencyModifiedInCurrentRequest = ThreadLocal.withInitial(() -> false);

    /**
     * Get the cached competency data for a session.
     * Used by Competency Expert to retrieve previous preview data for refinement.
     *
     * @param sessionId the session ID
     * @return the cached competency operations, or null if none exist
     */
    public List<CompetencyExpertToolsService.CompetencyOperation> getCachedCompetencyData(String sessionId) {
        return sessionCompetencyDataCache.get(sessionId);
    }

    /**
     * Cache competency data for a session.
     * Called after preview generation to enable deterministic refinements.
     *
     * @param sessionId the session ID
     * @param data      the competency operations to cache
     */
    public void cacheCompetencyData(String sessionId, List<CompetencyExpertToolsService.CompetencyOperation> data) {
        sessionCompetencyDataCache.put(sessionId, data);
    }

    /**
     * Clear cached competency data for a session.
     * Called after successful save or when starting a new competency flow.
     *
     * @param sessionId the session ID
     */
    public void clearCachedCompetencyData(String sessionId) {
        sessionCompetencyDataCache.remove(sessionId);
    }

    public AtlasAgentService(@Nullable ChatClient chatClient, AtlasPromptTemplateService templateService, @Nullable ToolCallbackProvider mainAgentToolCallbackProvider,
            @Nullable ToolCallbackProvider competencyExpertToolCallbackProvider, @Nullable ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.mainAgentToolCallbackProvider = mainAgentToolCallbackProvider;
        this.competencyExpertToolCallbackProvider = competencyExpertToolCallbackProvider;
        this.chatMemory = chatMemory;
    }

    /**
     * Process a chat message with multi-agent orchestration.
     * Routes to the appropriate agent based on session state and delegation markers.
     * Uses ThreadLocal state tracking to detect competency modifications.
     *
     * @param message   The user's message
     * @param courseId  The course ID for context
     * @param sessionId The session ID for chat memory and agent tracking
     * @return Result containing the AI response and competency modification flag
     */
    public CompletableFuture<AgentChatResult> processChatMessage(String message, Long courseId, String sessionId) {
        try {
            // Set sessionId in ThreadLocal so tools can access it
            CompetencyExpertToolsService.setCurrentSessionId(sessionId);
            resetCompetencyModifiedFlag();
            CompetencyExpertToolsService.clearAllPreviews();

            // Determine which agent should handle this message
            AgentType activeAgent = sessionAgentMap.getOrDefault(sessionId, AgentType.MAIN_AGENT);
            // Route to the appropriate agent
            String response = delegateTheRightAgent(message, courseId, sessionId, activeAgent);

            // Check for delegation markers and update session state
            if (response.contains(DELEGATE_TO_COMPETENCY_EXPERT)) {
                // Extract brief from marker: %%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%:brief_content]
                String brief = extractBriefFromDelegationMarker(response);

                // Delegate to Competency Expert
                response = delegateTheRightAgent(brief, courseId, sessionId, AgentType.COMPETENCY_EXPERT);

                // Stay on MAIN_AGENT - Atlas Core continues managing the workflow
            }
            else if (response.contains(CREATE_APPROVED_COMPETENCY)) {
                // Agent is requesting to execute the changes
                // Retrieve the cached competency data
                List<CompetencyExpertToolsService.CompetencyOperation> cachedData = getCachedCompetencyData(sessionId);

                if (cachedData != null && !cachedData.isEmpty()) {
                    String creationResponse = delegateTheRightAgent(CREATE_APPROVED_COMPETENCY, courseId, sessionId, AgentType.COMPETENCY_EXPERT);

                    // Clear the cache after successful save
                    clearCachedCompetencyData(sessionId);

                    // Retrieve preview data from ThreadLocal (set by saveCompetencies tool)
                    SingleCompetencyPreviewResponseDTO singlePreview = CompetencyExpertToolsService.getAndClearSinglePreview();
                    BatchCompetencyPreviewResponseDTO batchPreview = CompetencyExpertToolsService.getAndClearBatchPreview();

                    return CompletableFuture.completedFuture(new AgentChatResult(creationResponse, competencyModifiedInCurrentRequest.get(), singlePreview, batchPreview));
                }
                else {
                    String creationResponse = delegateTheRightAgent(CREATE_APPROVED_COMPETENCY, courseId, sessionId, AgentType.COMPETENCY_EXPERT);

                    SingleCompetencyPreviewResponseDTO singlePreview = CompetencyExpertToolsService.getAndClearSinglePreview();
                    BatchCompetencyPreviewResponseDTO batchPreview = CompetencyExpertToolsService.getAndClearBatchPreview();

                    return CompletableFuture.completedFuture(new AgentChatResult(creationResponse, competencyModifiedInCurrentRequest.get(), singlePreview, batchPreview));
                }
            }
            else if (response.contains(RETURN_TO_MAIN_AGENT)) {
                sessionAgentMap.put(sessionId, AgentType.MAIN_AGENT);
                // Remove the marker from the response before returning to user
                response = response.replace(RETURN_TO_MAIN_AGENT, "").trim();
            }

            // Check if competency was created during this request
            boolean competenciesModified = competencyModifiedInCurrentRequest.get();

            // Retrieve preview data from ThreadLocal (set by previewCompetencies tool during execution)
            SingleCompetencyPreviewResponseDTO singlePreview = CompetencyExpertToolsService.getAndClearSinglePreview();
            BatchCompetencyPreviewResponseDTO batchPreview = CompetencyExpertToolsService.getAndClearBatchPreview();

            // Use the LLM's natural language response
            String finalResponse = (response != null && !response.trim().isEmpty()) ? response : "I apologize, but I couldn't generate a response.";

            return CompletableFuture.completedFuture(new AgentChatResult(finalResponse, competenciesModified, singlePreview, batchPreview));

        }
        catch (Exception e) {
            return CompletableFuture.completedFuture(new AgentChatResult("I apologize, but I'm having trouble processing your request right now. Please try again later.", false));
        }
        finally {
            // Clean up ThreadLocal to prevent memory leaks
            competencyModifiedInCurrentRequest.remove();
            CompetencyExpertToolsService.clearCurrentSessionId();
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
    private String delegateTheRightAgent(String message, Long courseId, String sessionId, AgentType agentType) {
        String resourcePath;
        if (agentType.equals(AgentType.MAIN_AGENT)) {
            resourcePath = "/prompts/atlas/agent_system_prompt.st";
        }
        else {
            resourcePath = "/prompts/atlas/competency_expert_system_prompt.st";
        }
        // Load agent system prompt
        Map<String, String> variables = Map.of();
        String systemPrompt = templateService.render(resourcePath, variables);

        ToolCallingChatOptions options = AzureOpenAiChatOptions.builder().deploymentName("gpt-4o").temperature(1.0).build();

        ChatClientRequestSpec promptSpec = chatClient.prompt().system(systemPrompt).user(String.format("Course ID: %d\n\n%s", courseId, message)).options(options);

        // Add chat memory advisor
        if (chatMemory != null) {
            promptSpec = promptSpec.advisors(MessageChatMemoryAdvisor.builder(chatMemory).conversationId(sessionId).build());
        }

        // Add appropriate agent tools based on agent type
        if (agentType.equals(AgentType.MAIN_AGENT)) {
            if (mainAgentToolCallbackProvider != null) {
                promptSpec = promptSpec.toolCallbacks(mainAgentToolCallbackProvider);
            }
        }
        else {
            if (competencyExpertToolCallbackProvider != null) {
                promptSpec = promptSpec.toolCallbacks(competencyExpertToolCallbackProvider);
            }
        }

        // Execute the chat
        return promptSpec.call().content();
    }

    /**
     * Extract the brief content from the delegation marker.
     * Expected format: [DELEGATE_TO_COMPETENCY_EXPERT:brief_content]
     *
     * @param response The response containing the delegation marker
     * @return The extracted brief content
     */
    private String extractBriefFromDelegationMarker(String response) {
        int startIndex = response.indexOf(DELEGATE_TO_COMPETENCY_EXPERT);
        if (startIndex == -1) {
            return "";
        }

        // Find the colon after the marker
        int colonIndex = response.indexOf(":", startIndex);
        if (colonIndex == -1) {
            return "";
        }

        // Find the closing bracket
        int endIndex = response.indexOf("]", colonIndex);
        if (endIndex == -1) {
            return "";
        }

        // Extract the brief content between colon and closing bracket
        return response.substring(colonIndex + 1, endIndex).trim();
    }

    /**
     * Marks that a competency was created during the current request.
     * This method is called by tool methods (e.g., createCompetency) to signal
     * that a competency modification occurred during tool execution.
     */
    public static void markCompetencyModified() {
        competencyModifiedInCurrentRequest.set(true);
    }

    /**
     * Check if a competency was created during the current request.
     * Used primarily for testing purposes.
     *
     * @return true if a competency was created/modified during the current request
     */
    public static boolean wasCompetencyModified() {
        return competencyModifiedInCurrentRequest.get();
    }

    /**
     * Resets the competency created flag.
     * Used primarily for testing purposes to reset state between tests.
     */
    public static void resetCompetencyModifiedFlag() {
        competencyModifiedInCurrentRequest.remove();
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
