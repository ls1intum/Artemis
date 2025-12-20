package de.tum.cit.aet.artemis.atlas.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.AtlasAgentChatResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.AtlasAgentHistoryMessageDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyPreviewDTO;
import de.tum.cit.aet.artemis.atlas.service.CompetencyExpertToolsService.CompetencyOperation;

/**
 * Service for Atlas Agent functionality with Azure OpenAI integration.
 * Handles chat interactions and competency-related AI assistance.
 * Manages multi-agent orchestration between Main Agent and Sub-agents (Competency Expert Sub-agent).
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

    private static final Duration SESSION_EXPIRY_DURATION = Duration.ofHours(2);

    // Maximum number of concurrent sessions to prevent unbounded memory growth
    private static final int MAX_SESSIONS = 5000;

    /**
     * Track which agent is active for each session.
     * Uses Guava Cache with automatic eviction to prevent memory leaks:
     * Entries expire after 2 hours of inactivity
     * Maximum 5000 sessions cached
     * Automatic cleanup on access and during garbage collection.
     */
    private final Cache<String, AgentType> sessionAgentMap = CacheBuilder.newBuilder().expireAfterAccess(SESSION_EXPIRY_DURATION).maximumSize(MAX_SESSIONS).build();

    /**
     * Track pending competency operations for each session.
     * Uses Guava Cache with automatic eviction to prevent memory leaks:
     * Entries expire after 2 hours of inactivity
     * Maximum 5000 sessions cached
     * Automatic cleanup on access and during garbage collection.
     *
     * @see <a href="https://guava.dev/releases/33.0.0-jre/api/docs/com/google/common/cache/Cache.html">Guava Cache Documentation</a>
     */
    private final Cache<String, List<CompetencyOperation>> sessionPendingCompetencyOperationsCache = CacheBuilder.newBuilder().expireAfterAccess(SESSION_EXPIRY_DURATION)
            .maximumSize(MAX_SESSIONS).build();

    private final ChatClient chatClient;

    private final AtlasPromptTemplateService templateService;

    private final ToolCallbackProvider mainAgentToolCallbackProvider;

    private final ToolCallbackProvider competencyExpertToolCallbackProvider;

    private final ChatMemory chatMemory;

    private final String deploymentName;

    private final double temperature;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Boolean getCompetencyModifiedInCurrentRequest() {
        return competencyModifiedInCurrentRequest.get();
    }

    private static final ThreadLocal<Boolean> competencyModifiedInCurrentRequest = ThreadLocal.withInitial(() -> false);

    /**
     * Get the cached pending competency operations for a session.
     * Used by Competency Expert to retrieve previous preview data for refinement.
     *
     * @param sessionId the session ID
     * @return the cached pending competency operations, or null if none exist
     */
    public List<CompetencyOperation> getCachedPendingCompetencyOperations(String sessionId) {
        return sessionPendingCompetencyOperationsCache.getIfPresent(sessionId);
    }

    /**
     * Cache pending competency operations for a session.
     * Called after preview generation to enable deterministic refinements.
     *
     * @param sessionId  the session ID
     * @param operations the competency operations to cache
     */
    public void cachePendingCompetencyOperations(String sessionId, List<CompetencyOperation> operations) {
        sessionPendingCompetencyOperationsCache.put(sessionId, operations);
    }

    /**
     * Clear cached pending competency operations for a session.
     * Called after successful save or when starting a new competency flow.
     *
     * @param sessionId the session ID
     */
    public void clearCachedPendingCompetencyOperations(String sessionId) {
        sessionPendingCompetencyOperationsCache.invalidate(sessionId);
    }

    public AtlasAgentService(@Nullable ChatClient chatClient, AtlasPromptTemplateService templateService, @Nullable ToolCallbackProvider mainAgentToolCallbackProvider,
            @Nullable ToolCallbackProvider competencyExpertToolCallbackProvider, @Nullable ChatMemory chatMemory, @Value("${atlas.chat-model:gpt-4o}") String deploymentName,
            @Value("${atlas.chat-temperature:0.2}") double temperature) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.mainAgentToolCallbackProvider = mainAgentToolCallbackProvider;
        this.competencyExpertToolCallbackProvider = competencyExpertToolCallbackProvider;
        this.chatMemory = chatMemory;
        this.deploymentName = deploymentName;
        this.temperature = temperature;
    }

    /**
     * Process a chat message with multi-agent orchestration.
     * Routes to the appropriate agent based on session state and delegation markers.
     * Uses ThreadLocal state tracking to detect competency modifications.
     *
     * @param message   the user's message
     * @param courseId  the course ID for context
     * @param sessionId the session ID for chat memory and agent tracking
     * @return result containing the AI response and competency modification flag
     */
    public AtlasAgentChatResponseDTO processChatMessage(String message, Long courseId, String sessionId) {
        if (chatClient == null) {
            return new AtlasAgentChatResponseDTO("Atlas Agent is not available. Please contact your administrator.", ZonedDateTime.now(), false, null);
        }
        try {
            CompetencyExpertToolsService.setCurrentSessionId(sessionId);
            resetCompetencyModifiedFlag();

            String response = delegateTheRightAgent(message, courseId, sessionId);

            if (response.contains(DELEGATE_TO_COMPETENCY_EXPERT)) {
                String brief = extractBriefFromDelegationMarker(response);

                sessionAgentMap.put(sessionId, AgentType.COMPETENCY_EXPERT);
                String delegationResponse = delegateTheRightAgent(brief, courseId, sessionId);

                List<CompetencyPreviewDTO> previews = CompetencyExpertToolsService.getAndClearPreviews();

                String responseWithEmbeddedData = embedPreviewDataInResponse(delegationResponse, previews);

                // Replace the last assistant message with the version containing embedded preview data
                // The MessageChatMemoryAdvisor already added the response, but without preview data
                updateChatMemoryWithEmbeddedData(sessionId, responseWithEmbeddedData, delegationResponse);

                sessionAgentMap.put(sessionId, AgentType.MAIN_AGENT);
                return new AtlasAgentChatResponseDTO(delegationResponse, ZonedDateTime.now(), competencyModifiedInCurrentRequest.get(), previews);
            }
            else if (response.contains(CREATE_APPROVED_COMPETENCY)) {
                sessionAgentMap.put(sessionId, AgentType.COMPETENCY_EXPERT);
                List<CompetencyOperation> cachedData = getCachedPendingCompetencyOperations(sessionId);

                String creationResponse = delegateTheRightAgent(CREATE_APPROVED_COMPETENCY, courseId, sessionId);
                List<CompetencyPreviewDTO> previews = CompetencyExpertToolsService.getAndClearPreviews();
                String responseWithEmbeddedData = embedPreviewDataInResponse(creationResponse, previews);

                if (cachedData != null && !cachedData.isEmpty()) {
                    clearCachedPendingCompetencyOperations(sessionId);
                }

                updateChatMemoryWithEmbeddedData(sessionId, responseWithEmbeddedData, creationResponse);

                sessionAgentMap.put(sessionId, AgentType.MAIN_AGENT);
                return new AtlasAgentChatResponseDTO(creationResponse, ZonedDateTime.now(), competencyModifiedInCurrentRequest.get(), previews);
            }
            else if (response.contains(RETURN_TO_MAIN_AGENT)) {
                sessionAgentMap.put(sessionId, AgentType.MAIN_AGENT);
                response = response.replace(RETURN_TO_MAIN_AGENT, "").trim();

                boolean competenciesModified = competencyModifiedInCurrentRequest.get();

                List<CompetencyPreviewDTO> previews = CompetencyExpertToolsService.getAndClearPreviews();

                String finalResponse = (!response.trim().isEmpty()) ? response : "I apologize, but I couldn't generate a response.";

                // Embed preview data in response for chat memory persistence
                String responseWithEmbeddedData = embedPreviewDataInResponse(finalResponse, previews);

                // Update chat memory with embedded preview data if previews exist
                updateChatMemoryWithEmbeddedData(sessionId, responseWithEmbeddedData, finalResponse);

                return new AtlasAgentChatResponseDTO(finalResponse, ZonedDateTime.now(), competenciesModified, previews);
            }

            // Default case: return the response as-is
            return new AtlasAgentChatResponseDTO(response, ZonedDateTime.now(), competencyModifiedInCurrentRequest.get(), null);
        }
        catch (Exception e) {
            return new AtlasAgentChatResponseDTO("I apologize, but I'm having trouble processing your request right now. Please try again later.", ZonedDateTime.now(), false,
                    null);
        }
        finally {
            competencyModifiedInCurrentRequest.remove();
            CompetencyExpertToolsService.clearCurrentSessionId();
        }

    }

    /**
     * Process message with the Main Agent (Requirements Engineer/Orchestrator).
     *
     * @param message   the user's message
     * @param courseId  the course ID for context
     * @param sessionId the session ID for chat memory
     * @return the agent's response
     */
    private String delegateTheRightAgent(String message, Long courseId, String sessionId) {
        AgentType agentType = sessionAgentMap.getIfPresent(sessionId);
        if (agentType == null) {
            agentType = AgentType.MAIN_AGENT;
        }

        String resourcePath;
        if (agentType.equals(AgentType.MAIN_AGENT)) {
            resourcePath = "/prompts/atlas/agent_system_prompt.st";
        }
        else {
            resourcePath = "/prompts/atlas/competency_expert_system_prompt.st";
        }
        Map<String, String> variables = Map.of();
        String systemPrompt = templateService.render(resourcePath, variables);

        // Append courseId to system prompt so that the Sub-Agents have course context (invisible to conversation history)
        String systemPromptWithContext = systemPrompt + "\n\nCONTEXT FOR THIS REQUEST:\nCourse ID: " + courseId;

        // Build chat client with memory advisor for this specific session
        ChatClient.Builder clientBuilder = chatClient.mutate();
        // Add memory advisor only for Atlas with conversation-specific session ID
        if (chatMemory != null) {
            clientBuilder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).conversationId(sessionId).build());
        }
        ChatClient sessionClient = clientBuilder.build();

        ToolCallingChatOptions options = AzureOpenAiChatOptions.builder().deploymentName(deploymentName).temperature(temperature).build();

        ChatClientRequestSpec promptSpec = sessionClient.prompt().system(systemPromptWithContext).user(message).options(options);

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
     * Updates the last assistant message in chat memory with embedded preview data.
     * This ensures preview data persists in conversation history.
     *
     * @param sessionId                the session ID
     * @param responseWithEmbeddedData the response text with embedded preview data
     * @param originalResponse         the original response text without embedded data
     */
    private void updateChatMemoryWithEmbeddedData(String sessionId, String responseWithEmbeddedData, String originalResponse) {
        if (chatMemory != null && !responseWithEmbeddedData.equals(originalResponse)) {
            List<Message> messages = chatMemory.get(sessionId);
            if (!messages.isEmpty() && messages.getLast().getMessageType() == MessageType.ASSISTANT) {
                List<Message> updatedMessages = new ArrayList<>(messages.subList(0, messages.size() - 1));
                updatedMessages.add(new AssistantMessage(responseWithEmbeddedData));

                chatMemory.clear(sessionId);
                updatedMessages.forEach(msg -> chatMemory.add(sessionId, msg));
            }
        }
    }

    /**
     * Extract the brief content from the delegation marker.
     * Expected format:
     * %%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%:TOPIC/TOPICS: ...\\nREQUIREMENTS: ...\\nCONSTRAINTS: ...\\nCONTEXT: ...
     *
     * @param response the response containing the delegation marker
     * @return the extracted brief content
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
     * Marks that a competency was modified during the current request.
     * This method is called by tool methods (e.g., createCompetency) to signal
     * that a competency modification occurred during tool execution.
     */
    public static void markCompetencyModified() {
        competencyModifiedInCurrentRequest.set(true);
    }

    /**
     * Check if a competency was modified during the current request.
     * Used primarily for testing purposes.
     *
     * @return true if a competency was created/modified during the current request
     */
    public static boolean wasCompetencyModified() {
        return competencyModifiedInCurrentRequest.get();
    }

    /**
     * Resets the competency modified flag.
     * Used primarily for testing purposes to reset state between tests.
     */
    public static void resetCompetencyModifiedFlag() {
        competencyModifiedInCurrentRequest.remove();
    }

    /**
     * Retrieves the conversation history for a given session as DTOs.
     * Filters out internal system messages (delegation markers and briefings) and extracts preview data from messages.
     *
     * @param sessionId the session/conversation ID
     * @return list of conversation history messages as DTOs with preview data
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

                PreviewDataResult extracted = extractPreviewDataFromMessage(text);
                result.add(new AtlasAgentHistoryMessageDTO(extracted.cleanedText(), isUser, extracted.previews()));
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
     * @param response the agent's response text
     * @param previews optional list of competency previews
     * @return the response text with embedded preview data marker
     */
    private String embedPreviewDataInResponse(String response, @Nullable List<CompetencyPreviewDTO> previews) {
        if (previews == null || previews.isEmpty()) {
            return response;
        }

        try {
            PreviewDataContainer container = new PreviewDataContainer(previews);
            String jsonData = objectMapper.writeValueAsString(container);

            // Append marker with JSON data to the response
            return response + " " + PREVIEW_DATA_START_MARKER + jsonData + PREVIEW_DATA_END_MARKER;
        }
        catch (JsonProcessingException e) {
            return response;
        }
    }

    /**
     * Extract preview data from a message text that contains embedded preview markers.
     *
     * @param messageText the message text potentially containing preview data markers
     * @return PreviewDataResult containing the cleaned text and extracted preview data
     */
    private PreviewDataResult extractPreviewDataFromMessage(String messageText) {
        int startIndex = messageText.indexOf(PREVIEW_DATA_START_MARKER);
        if (startIndex == -1) {
            return new PreviewDataResult(messageText, null);
        }

        int endIndex = messageText.indexOf(PREVIEW_DATA_END_MARKER, startIndex);
        if (endIndex == -1) {
            return new PreviewDataResult(messageText, null);
        }

        int jsonStart = startIndex + PREVIEW_DATA_START_MARKER.length();
        String jsonData = messageText.substring(jsonStart, endIndex);

        // Remove the marker section from the text
        String cleanedText = (messageText.substring(0, startIndex) + messageText.substring(endIndex + PREVIEW_DATA_END_MARKER.length())).trim();

        try {
            PreviewDataContainer container = objectMapper.readValue(jsonData, PreviewDataContainer.class);
            return new PreviewDataResult(cleanedText, container.previews());
        }
        catch (JsonProcessingException e) {
            return new PreviewDataResult(cleanedText, null);
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
    private record PreviewDataContainer(@Nullable List<CompetencyPreviewDTO> previews) {
    }

    /**
     * Result of extracting preview data from a message.
     */
    private record PreviewDataResult(String cleanedText, @Nullable List<CompetencyPreviewDTO> previews) {
    }
}
