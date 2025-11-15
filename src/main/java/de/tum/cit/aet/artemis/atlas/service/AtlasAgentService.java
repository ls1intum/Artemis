package de.tum.cit.aet.artemis.atlas.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import jakarta.annotation.Nullable;

import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.AtlasAgentHistoryMessageDTO;
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

    private static final String PREVIEW_DATA_START_MARKER = "%%PREVIEW_DATA_START%%";

    private static final String PREVIEW_DATA_END_MARKER = "%%PREVIEW_DATA_END%%";

    // Session timeout duration: 2 hours of inactivity
    private static final Duration SESSION_EXPIRY_DURATION = Duration.ofHours(2);

    // Maximum number of concurrent sessions to prevent unbounded memory growth
    private static final int MAX_SESSIONS = 5000;

    /**
     * Track which agent is active for each session.
     * Uses Guava Cache with automatic eviction to prevent memory leaks:
     * - Entries expire after 2 hours of inactivity (expireAfterAccess)
     * - Maximum 5000 sessions cached (maximumSize)
     * - Automatic cleanup on access and during GC
     */
    private final Cache<String, AgentType> sessionAgentMap = CacheBuilder.newBuilder().expireAfterAccess(SESSION_EXPIRY_DURATION).maximumSize(MAX_SESSIONS).build();

    private final Cache<String, List<CompetencyExpertToolsService.CompetencyOperation>> sessionCompetencyDataCache = CacheBuilder.newBuilder()
            .expireAfterAccess(SESSION_EXPIRY_DURATION).maximumSize(MAX_SESSIONS).build();

    private final ChatClient chatClient;

    private final AtlasPromptTemplateService templateService;

    private final ToolCallbackProvider mainAgentToolCallbackProvider;

    private final ToolCallbackProvider competencyExpertToolCallbackProvider;

    private final ChatMemory chatMemory;

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
        return sessionCompetencyDataCache.getIfPresent(sessionId);
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
        sessionCompetencyDataCache.invalidate(sessionId);
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
        if (chatClient == null) {
            return CompletableFuture.completedFuture(new AgentChatResult("Atlas Agent is not available. Please contact your administrator.", false));
        }

        try {
            // Set sessionId in ThreadLocal so tools can access it
            CompetencyExpertToolsService.setCurrentSessionId(sessionId);
            resetCompetencyModifiedFlag();
            CompetencyExpertToolsService.clearAllPreviews();

            // Determine which agent should handle this message (Guava Cache returns null if not present)
            AgentType activeAgent = sessionAgentMap.getIfPresent(sessionId);
            if (activeAgent == null) {
                activeAgent = AgentType.MAIN_AGENT;
            }
            // Route to the appropriate agent
            String response = delegateTheRightAgent(message, courseId, sessionId, activeAgent);

            // Check for delegation markers and update session state
            if (response.contains(DELEGATE_TO_COMPETENCY_EXPERT)) {
                // Extract brief from marker: %%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%:brief_content]
                String brief = extractBriefFromDelegationMarker(response);

                // Delegate to Competency Expert
                String delegationResponse = delegateTheRightAgent(brief, courseId, sessionId, AgentType.COMPETENCY_EXPERT);

                // Retrieve preview data from ThreadLocal (set by Competency Expert's previewCompetencies tool)
                SingleCompetencyPreviewResponseDTO singlePreview = CompetencyExpertToolsService.getSinglePreview();
                BatchCompetencyPreviewResponseDTO batchPreview = CompetencyExpertToolsService.getBatchPreview();

                // Embed preview data in the response text so it persists in chat memory
                String responseWithEmbeddedData = embedPreviewDataInResponse(delegationResponse, singlePreview, batchPreview);

                // Replace the last assistant message with the version containing embedded preview data
                // The MessageChatMemoryAdvisor already added the response, but without preview data
                if (chatMemory != null && !responseWithEmbeddedData.equals(delegationResponse)) {
                    // Remove the last assistant message added by the advisor
                    List<Message> messages = chatMemory.get(sessionId);
                    if (!messages.isEmpty() && messages.getLast().getMessageType() == MessageType.ASSISTANT) {
                        // Create a new list without the last message
                        List<Message> updatedMessages = new java.util.ArrayList<>(messages.subList(0, messages.size() - 1));
                        // Add the message with embedded preview data
                        updatedMessages.add(new AssistantMessage(responseWithEmbeddedData));
                        // Clear and re-add all messages
                        chatMemory.clear(sessionId);
                        updatedMessages.forEach(msg -> chatMemory.add(sessionId, msg));
                    }
                }

                // Return immediately with Competency Expert's response and preview data
                // Stay on MAIN_AGENT - Atlas Core continues managing the workflow
                return CompletableFuture.completedFuture(new AgentChatResult(delegationResponse, competencyModifiedInCurrentRequest.get(), singlePreview, batchPreview));
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
                    SingleCompetencyPreviewResponseDTO singlePreview = CompetencyExpertToolsService.getSinglePreview();
                    BatchCompetencyPreviewResponseDTO batchPreview = CompetencyExpertToolsService.getBatchPreview();

                    return CompletableFuture.completedFuture(new AgentChatResult(creationResponse, competencyModifiedInCurrentRequest.get(), singlePreview, batchPreview));
                }
                else {
                    String creationResponse = delegateTheRightAgent(CREATE_APPROVED_COMPETENCY, courseId, sessionId, AgentType.COMPETENCY_EXPERT);

                    SingleCompetencyPreviewResponseDTO singlePreview = CompetencyExpertToolsService.getSinglePreview();
                    BatchCompetencyPreviewResponseDTO batchPreview = CompetencyExpertToolsService.getBatchPreview();

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
            SingleCompetencyPreviewResponseDTO singlePreview = CompetencyExpertToolsService.getSinglePreview();
            BatchCompetencyPreviewResponseDTO batchPreview = CompetencyExpertToolsService.getBatchPreview();

            // Use the LLM's natural language response
            String finalResponse = (!response.trim().isEmpty()) ? response : "I apologize, but I couldn't generate a response.";

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

        ToolCallingChatOptions options = AzureOpenAiChatOptions.builder().deploymentName("gpt-4o").temperature(0.2).build();

        ChatClientRequestSpec promptSpec = chatClient.prompt().system(systemPrompt).user(message).options(options);

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

        int markerEnd = startIndex + DELEGATE_TO_COMPETENCY_EXPERT.length();
        if (markerEnd >= response.length() || response.charAt(markerEnd) != ':') {
            return "";
        }
        int nextMarker = response.indexOf("%%", markerEnd + 1);
        String briefSection = nextMarker == -1 ? response.substring(markerEnd + 1) : response.substring(markerEnd + 1, nextMarker);
        return briefSection.strip();
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
     * Retrieves the conversation history for a given session as DTOs.
     * Filters out internal system messages (delegation markers and briefings) and extracts preview data from messages.
     *
     * @param sessionId The session/conversation ID
     * @return List of conversation history messages as DTOs with preview data
     */
    public List<AtlasAgentHistoryMessageDTO> getConversationHistoryAsDTO(String sessionId) {
        try {
            if (chatMemory == null) {
                return List.of();
            }
            List<Message> messages = chatMemory.get(sessionId);

            if (messages.isEmpty()) {
                return List.of();
            }

            var result = new java.util.ArrayList<AtlasAgentHistoryMessageDTO>();

            for (Message message : messages) {
                String text = message.getText();
                boolean isUser = message.getMessageType() == MessageType.USER;

                // Skip internal system messages (delegation markers and briefings)
                boolean isBriefing = text.startsWith("TOPIC:") || text.startsWith("TOPICS:")
                        || (text.contains("REQUIREMENTS:") && text.contains("CONSTRAINTS:") && text.contains("CONTEXT:"));
                boolean isDelegationMarker = text.contains(DELEGATE_TO_COMPETENCY_EXPERT) || text.contains(RETURN_TO_MAIN_AGENT);

                if (isBriefing || isDelegationMarker) {
                    continue;
                }

                // Extract preview data from message text if present
                PreviewDataResult extracted = extractPreviewDataFromMessage(text);

                result.add(new AtlasAgentHistoryMessageDTO(extracted.cleanedText(), isUser, extracted.singlePreview(), extracted.batchPreview()));
            }

            return result;
        }
        catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Check if the Atlas Agent service is available and properly configured.
     *
     * @return true if the service is ready, false otherwise
     */
    public boolean isAvailable() {
        return chatClient != null && chatMemory != null;
    }

    /**
     * Embed preview data as a JSON marker in the response text.
     * This allows preview data to persist in chat memory and be reconstructed when loading history.
     *
     * @param response      The agent's response text
     * @param singlePreview Optional single competency preview
     * @param batchPreview  Optional batch competency preview
     * @return The response text with embedded preview data marker
     */
    private String embedPreviewDataInResponse(String response, @Nullable SingleCompetencyPreviewResponseDTO singlePreview,
            @Nullable BatchCompetencyPreviewResponseDTO batchPreview) {
        if (singlePreview == null && batchPreview == null) {
            return response;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            PreviewDataContainer container = new PreviewDataContainer(singlePreview, batchPreview);
            String jsonData = mapper.writeValueAsString(container);

            // Append marker with JSON data to the response
            return response + " " + PREVIEW_DATA_START_MARKER + jsonData + PREVIEW_DATA_END_MARKER;
        }
        catch (JsonProcessingException e) {
            // If JSON serialization fails, return response without preview data
            return response;
        }
    }

    /**
     * Extract preview data from a message text that contains embedded preview markers.
     *
     * @param messageText The message text potentially containing preview data markers
     * @return PreviewDataResult containing the cleaned text and extracted preview data
     */
    private PreviewDataResult extractPreviewDataFromMessage(String messageText) {
        int startIndex = messageText.indexOf(PREVIEW_DATA_START_MARKER);
        if (startIndex == -1) {
            return new PreviewDataResult(messageText, null, null);
        }

        int endIndex = messageText.indexOf(PREVIEW_DATA_END_MARKER, startIndex);
        if (endIndex == -1) {
            return new PreviewDataResult(messageText, null, null);
        }

        // Extract the JSON data between markers
        int jsonStart = startIndex + PREVIEW_DATA_START_MARKER.length();
        String jsonData = messageText.substring(jsonStart, endIndex);

        // Remove the marker section from the text
        String cleanedText = (messageText.substring(0, startIndex) + messageText.substring(endIndex + PREVIEW_DATA_END_MARKER.length())).trim();

        try {
            ObjectMapper mapper = new ObjectMapper();
            PreviewDataContainer container = mapper.readValue(jsonData, PreviewDataContainer.class);
            return new PreviewDataResult(cleanedText, container.singlePreview(), container.batchPreview());
        }
        catch (JsonProcessingException e) {
            // If parsing fails, return cleaned text without preview data
            return new PreviewDataResult(cleanedText, null, null);
        }
    }

    /**
     * Generates a unique session ID for a user's conversation in a specific course.
     * This ensures each user has their own isolated chat history per course.
     * Centralized generation prevents security risks from client-controlled session IDs.
     *
     * @param courseId the course ID
     * @param userId   the user ID
     * @return the generated session ID in format "course_{courseId}_user_{userId}"
     */
    public String generateSessionId(Long courseId, Long userId) {
        return String.format("course_%d_user_%d", courseId, userId);
    }

    /**
     * Container for embedding preview data in message text.
     */
    record PreviewDataContainer(@Nullable SingleCompetencyPreviewResponseDTO singlePreview, @Nullable BatchCompetencyPreviewResponseDTO batchPreview) {
    }

    /**
     * Result of extracting preview data from a message.
     */
    record PreviewDataResult(String cleanedText, @Nullable SingleCompetencyPreviewResponseDTO singlePreview, @Nullable BatchCompetencyPreviewResponseDTO batchPreview) {
    }
}
