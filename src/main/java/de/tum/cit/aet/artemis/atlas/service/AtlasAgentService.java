package de.tum.cit.aet.artemis.atlas.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
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
import de.tum.cit.aet.artemis.atlas.dto.AtlasAgentChatResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.AtlasAgentHistoryMessageDTO;
import de.tum.cit.aet.artemis.atlas.dto.BatchRelationPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.RelationGraphPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.SingleRelationPreviewResponseDTO;

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
        MAIN_AGENT, COMPETENCY_EXPERT, COMPETENCY_MAPPER
    }

    private static final String DELEGATE_TO_COMPETENCY_EXPERT = "%%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%";

    private static final String DELEGATE_TO_COMPETENCY_MAPPER = "%%ARTEMIS_DELEGATE_TO_COMPETENCY_MAPPER%%";

    private static final String CREATE_APPROVED_COMPETENCY = "[CREATE_APPROVED_COMPETENCY]";

    private static final String CREATE_APPROVED_RELATION = "[CREATE_APPROVED_RELATION]";

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

    private final Cache<String, List<CompetencyExpertToolsService.CompetencyOperation>> sessionPendingCompetencyOperationsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(SESSION_EXPIRY_DURATION).maximumSize(MAX_SESSIONS).build();

    private final Cache<String, List<CompetencyMappingToolsService.RelationOperation>> sessionPendingRelationOperationsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(SESSION_EXPIRY_DURATION).maximumSize(MAX_SESSIONS).build();

    private final ChatClient chatClient;

    private final AtlasPromptTemplateService templateService;

    private final ToolCallbackProvider mainAgentToolCallbackProvider;

    private final ToolCallbackProvider competencyExpertToolCallbackProvider;

    private final ToolCallbackProvider competencyMapperToolCallbackProvider;

    private final ChatMemory chatMemory;

    @Value("${atlas.chat-model:gpt-4o}")
    private String deploymentName;

    @Value("${atlas.chat-temperature:0.2}")
    private double temperature;

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
    public List<CompetencyExpertToolsService.CompetencyOperation> getCachedPendingCompetencyOperations(String sessionId) {
        return sessionPendingCompetencyOperationsCache.getIfPresent(sessionId);
    }

    /**
     * Cache pending competency operations for a session.
     * Called after preview generation to enable deterministic refinements.
     *
     * @param sessionId  the session ID
     * @param operations the competency operations to cache
     */
    public void cachePendingCompetencyOperations(String sessionId, List<CompetencyExpertToolsService.CompetencyOperation> operations) {
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

    /**
     * Get the cached relation operations for a session.
     * Used by Competency Mapper to retrieve previous relation data.
     *
     * @param sessionId the session ID
     * @return the cached relation operations, or null if none exist
     */
    public List<CompetencyMappingToolsService.RelationOperation> getCachedRelationData(String sessionId) {
        return sessionPendingRelationOperationsCache.getIfPresent(sessionId);
    }

    /**
     * Cache relation operations for a session.
     * Called after preview generation to enable tracking.
     *
     * @param sessionId  the session ID
     * @param operations the relation operations to cache
     */
    public void cacheRelationOperations(String sessionId, List<CompetencyMappingToolsService.RelationOperation> operations) {
        sessionPendingRelationOperationsCache.put(sessionId, operations);
    }

    /**
     * Clear cached relation operations for a session.
     * Called after successful save or when starting a new relation flow.
     *
     * @param sessionId the session ID
     */
    public void clearCachedRelationOperations(String sessionId) {
        sessionPendingRelationOperationsCache.invalidate(sessionId);
    }

    public AtlasAgentService(@Nullable ChatClient chatClient, AtlasPromptTemplateService templateService, @Nullable ToolCallbackProvider mainAgentToolCallbackProvider,
            @Nullable ToolCallbackProvider competencyExpertToolCallbackProvider, @Nullable ToolCallbackProvider competencyMapperToolCallbackProvider,
            @Nullable ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.mainAgentToolCallbackProvider = mainAgentToolCallbackProvider;
        this.competencyExpertToolCallbackProvider = competencyExpertToolCallbackProvider;
        this.competencyMapperToolCallbackProvider = competencyMapperToolCallbackProvider;
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
    public CompletableFuture<AtlasAgentChatResponseDTO> processChatMessage(String message, Long courseId, String sessionId) {
        if (chatClient == null) {
            return CompletableFuture.completedFuture(
                    new AtlasAgentChatResponseDTO("Atlas Agent is not available. Please contact your administrator.", ZonedDateTime.now(), false, null, null, null));
        }

        try {
            CompetencyExpertToolsService.setCurrentSessionId(sessionId);
            resetCompetencyModifiedFlag();
            CompetencyExpertToolsService.clearAllPreviews();

            String response = delegateTheRightAgent(message, courseId, sessionId);

            if (response.contains(DELEGATE_TO_COMPETENCY_EXPERT)) {
                String brief = extractBriefFromDelegationMarker(response);

                sessionAgentMap.put(sessionId, AgentType.COMPETENCY_EXPERT);
                String delegationResponse = delegateTheRightAgent(brief, courseId, sessionId);

                List<CompetencyPreviewDTO> previews = CompetencyExpertToolsService.getPreviews();

                String responseWithEmbeddedData = embedPreviewDataInResponse(delegationResponse, previews);

                // Replace the last assistant message with the version containing embedded preview data
                // The MessageChatMemoryAdvisor already added the response, but without preview data
                if (chatMemory != null && !responseWithEmbeddedData.equals(delegationResponse)) {
                    List<Message> messages = chatMemory.get(sessionId);
                    if (!messages.isEmpty() && messages.getLast().getMessageType() == MessageType.ASSISTANT) {
                        List<Message> updatedMessages = new ArrayList<>(messages.subList(0, messages.size() - 1));
                        updatedMessages.add(new AssistantMessage(responseWithEmbeddedData));
                        chatMemory.clear(sessionId);
                        updatedMessages.forEach(msg -> chatMemory.add(sessionId, msg));
                    }
                }

                sessionAgentMap.put(sessionId, AgentType.MAIN_AGENT);
                return CompletableFuture
                        .completedFuture(new AtlasAgentChatResponseDTO(delegationResponse, ZonedDateTime.now(), competencyModifiedInCurrentRequest.get(), previews, null, null));
            }
            else if (response.contains(CREATE_APPROVED_COMPETENCY) || message.equals(CREATE_APPROVED_COMPETENCY)) {
                sessionAgentMap.put(sessionId, AgentType.COMPETENCY_EXPERT);
                List<CompetencyExpertToolsService.CompetencyOperation> cachedData = getCachedPendingCompetencyOperations(sessionId);

                // Replace the user message in chat memory with a friendly version
                if (chatMemory != null && message.equals(CREATE_APPROVED_COMPETENCY)) {
                    List<Message> messages = chatMemory.get(sessionId);
                    if (!messages.isEmpty() && messages.getLast().getMessageType() == MessageType.USER) {
                        List<Message> updatedMessages = new ArrayList<>(messages.subList(0, messages.size() - 1));
                        updatedMessages.add(new UserMessage("Create the competency"));
                        chatMemory.clear(sessionId);
                        updatedMessages.forEach(msg -> chatMemory.add(sessionId, msg));
                    }
                }

                String creationResponse = delegateTheRightAgent(CREATE_APPROVED_COMPETENCY, courseId, sessionId);

                List<CompetencyPreviewDTO> previews = CompetencyExpertToolsService.getPreviews();

                String responseWithEmbeddedData = embedPreviewDataInResponse(creationResponse, previews);

                if (cachedData != null && !cachedData.isEmpty()) {
                    clearCachedPendingCompetencyOperations(sessionId);
                }

                // Update chat memory with embedded preview data
                if (chatMemory != null && !responseWithEmbeddedData.equals(creationResponse)) {
                    List<Message> messages = chatMemory.get(sessionId);
                    if (!messages.isEmpty() && messages.getLast().getMessageType() == MessageType.ASSISTANT) {
                        List<Message> updatedMessages = new ArrayList<>(messages.subList(0, messages.size() - 1));
                        updatedMessages.add(new AssistantMessage(responseWithEmbeddedData));

                        chatMemory.clear(sessionId);
                        updatedMessages.forEach(msg -> chatMemory.add(sessionId, msg));
                    }
                }
                sessionAgentMap.put(sessionId, AgentType.MAIN_AGENT);
                return CompletableFuture
                        .completedFuture(new AtlasAgentChatResponseDTO(creationResponse, ZonedDateTime.now(), competencyModifiedInCurrentRequest.get(), previews, null, null));
            }
            else if (response.contains(DELEGATE_TO_COMPETENCY_MAPPER)) {
                // Extract brief from marker
                String brief = extractBriefFromDelegationMarker(response);

                // Set session to COMPETENCY_MAPPER agent
                sessionAgentMap.put(sessionId, AgentType.COMPETENCY_MAPPER);

                // Set sessionId for tool calls
                CompetencyMappingToolsService.setCurrentSessionId(sessionId);

                // Delegate to Competency Mapper
                String delegationResponse = delegateTheRightAgent(brief, courseId, sessionId);

                System.out.println("DEBUG: Delegated to Competency Mapper");
                System.out.println("DEBUG: Delegation response: " + delegationResponse);

                // Retrieve relation preview data from ThreadLocal
                SingleRelationPreviewResponseDTO singleRelationPreview = CompetencyMappingToolsService.getSingleRelationPreview();
                BatchRelationPreviewResponseDTO batchRelationPreview = CompetencyMappingToolsService.getBatchRelationPreview();
                de.tum.cit.aet.artemis.atlas.dto.RelationGraphPreviewDTO relationGraphPreview = CompetencyMappingToolsService.getRelationGraphPreview();

                System.out.println("DEBUG: Single relation preview: " + singleRelationPreview);
                System.out.println("DEBUG: Batch relation preview: " + batchRelationPreview);
                System.out.println("DEBUG: Relation graph preview: " + relationGraphPreview);

                // Embed relation preview data in the response
                String responseWithEmbeddedData = embedRelationPreviewDataInResponse(delegationResponse, singleRelationPreview, batchRelationPreview, relationGraphPreview);

                // Replace the last assistant message with the version containing embedded preview data
                if (chatMemory != null && !responseWithEmbeddedData.equals(delegationResponse)) {
                    List<Message> messages = chatMemory.get(sessionId);
                    if (!messages.isEmpty() && messages.getLast().getMessageType() == MessageType.ASSISTANT) {
                        List<Message> updatedMessages = new java.util.ArrayList<>(messages.subList(0, messages.size() - 1));
                        updatedMessages.add(new AssistantMessage(responseWithEmbeddedData));
                        chatMemory.clear(sessionId);
                        updatedMessages.forEach(msg -> chatMemory.add(sessionId, msg));
                    }
                }

                // Reset to MAIN_AGENT
                sessionAgentMap.put(sessionId, AgentType.MAIN_AGENT);

                // Return with relation preview data (convert to unified list)
                List<de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationPreviewDTO> relationPreviews = convertToRelationPreviewsList(singleRelationPreview, batchRelationPreview);
                return CompletableFuture.completedFuture(new AtlasAgentChatResponseDTO(delegationResponse, ZonedDateTime.now(), competencyModifiedInCurrentRequest.get(), null,
                        relationPreviews, relationGraphPreview));
            }
            else if (response.contains(CREATE_APPROVED_RELATION) || message.equals(CREATE_APPROVED_RELATION) || isRelationApprovalMessage(message)) {
                // User approved the relation creation
                sessionAgentMap.put(sessionId, AgentType.COMPETENCY_MAPPER);
                CompetencyMappingToolsService.setCurrentSessionId(sessionId);

                List<CompetencyMappingToolsService.RelationOperation> cachedData = getCachedRelationData(sessionId);

                // Replace the user message in chat memory with a friendly version
                if (chatMemory != null && (message.equals(CREATE_APPROVED_RELATION) || isRelationApprovalMessage(message))) {
                    List<Message> messages = chatMemory.get(sessionId);
                    if (!messages.isEmpty() && messages.getLast().getMessageType() == MessageType.USER) {
                        List<Message> updatedMessages = new ArrayList<>(messages.subList(0, messages.size() - 1));
                        updatedMessages.add(new UserMessage("Map the relation"));
                        chatMemory.clear(sessionId);
                        updatedMessages.forEach(msg -> chatMemory.add(sessionId, msg));
                    }
                }

                String creationResponse = delegateTheRightAgent(CREATE_APPROVED_RELATION, courseId, sessionId);

                // Remove the marker from the response if it appears
                creationResponse = creationResponse.replace(CREATE_APPROVED_RELATION, "").trim();

                if (cachedData != null && !cachedData.isEmpty()) {
                    // Clear the cache after successful save
                    clearCachedRelationOperations(sessionId);
                }

                // Reset to MAIN_AGENT
                sessionAgentMap.put(sessionId, AgentType.MAIN_AGENT);

                // Don't send preview data after creation - just the success message
                return CompletableFuture
                        .completedFuture(new AtlasAgentChatResponseDTO(creationResponse, ZonedDateTime.now(), competencyModifiedInCurrentRequest.get(), null, null, null));
            }
            else if (response.contains(RETURN_TO_MAIN_AGENT)) {
                sessionAgentMap.put(sessionId, AgentType.MAIN_AGENT);
                response = response.replace(RETURN_TO_MAIN_AGENT, "").trim();
            }

            boolean competenciesModified = competencyModifiedInCurrentRequest.get();

            List<CompetencyPreviewDTO> previews = CompetencyExpertToolsService.getPreviews();

            // Use the LLM's natural language response
            String finalResponse = (!response.trim().isEmpty()) ? response : "I apologize, but I couldn't generate a response.";

            return CompletableFuture.completedFuture(new AtlasAgentChatResponseDTO(finalResponse, ZonedDateTime.now(), competenciesModified, previews, null, null));

        }
        catch (Exception e) {
            return CompletableFuture.completedFuture(new AtlasAgentChatResponseDTO("I apologize, but I'm having trouble processing your request right now. Please try again later.",
                    ZonedDateTime.now(), false, null, null, null));
        }
        finally {
            // Clean up ThreadLocal to prevent memory leaks
            competencyModifiedInCurrentRequest.remove();
            CompetencyExpertToolsService.clearCurrentSessionId();
            CompetencyExpertToolsService.clearAllPreviews();
            CompetencyMappingToolsService.clearCurrentSessionId();
            CompetencyMappingToolsService.clearAllPreviews();
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
    private String delegateTheRightAgent(String message, Long courseId, String sessionId) {
        AgentType agentType = sessionAgentMap.getIfPresent(sessionId);
        if (agentType == null) {
            agentType = AgentType.MAIN_AGENT;
        }

        String resourcePath;
        if (agentType.equals(AgentType.MAIN_AGENT)) {
            resourcePath = "/prompts/atlas/agent_system_prompt.st";
        }
        else if (agentType.equals(AgentType.COMPETENCY_EXPERT)) {
            resourcePath = "/prompts/atlas/competency_expert_system_prompt.st";
        }
        else if (agentType.equals(AgentType.COMPETENCY_MAPPER)) {
            resourcePath = "/prompts/atlas/competency_mapper_system_prompt.st";
        }
        else {
            // Fallback to main agent prompt if unknown type
            resourcePath = "/prompts/atlas/agent_system_prompt.st";
        }
        // Load agent system prompt
        Map<String, String> variables = Map.of();
        String systemPrompt = templateService.render(resourcePath, variables);

        // Append courseId to system prompt (invisible to conversation history)
        String systemPromptWithContext = systemPrompt + "\n\nCONTEXT FOR THIS REQUEST:\nCourse ID: " + courseId;

        ToolCallingChatOptions options = AzureOpenAiChatOptions.builder().deploymentName(deploymentName).temperature(temperature).build();

        ChatClientRequestSpec promptSpec = chatClient.prompt().system(systemPromptWithContext).user(message).options(options);

        if (chatMemory != null) {
            promptSpec = promptSpec.advisors(MessageChatMemoryAdvisor.builder(chatMemory).conversationId(sessionId).build());
        }

        // Add appropriate agent tools based on agent type
        if (agentType.equals(AgentType.MAIN_AGENT)) {
            if (mainAgentToolCallbackProvider != null) {
                promptSpec = promptSpec.toolCallbacks(mainAgentToolCallbackProvider);
            }
        }
        else if (agentType.equals(AgentType.COMPETENCY_EXPERT)) {
            if (competencyExpertToolCallbackProvider != null) {
                promptSpec = promptSpec.toolCallbacks(competencyExpertToolCallbackProvider);
            }
        }
        else if (agentType.equals(AgentType.COMPETENCY_MAPPER)) {
            if (competencyMapperToolCallbackProvider != null) {
                promptSpec = promptSpec.toolCallbacks(competencyMapperToolCallbackProvider);
            }
        }

        // Execute the chat
        return promptSpec.call().content();
    }

    /**
     * Extract the brief content from the delegation marker.
     * Expected format:
     * %%ARTEMIS_DELEGATE_TO_COMPETENCY_EXPERT%%:TOPIC/TOPICS: ...\\nREQUIREMENTS: ...\\nCONSTRAINTS: ...\\nCONTEXT: ...
     * %%ARTEMIS_DELEGATE_TO_COMPETENCY_MAPPER%%:TOPIC/TOPICS: ...\\nREQUIREMENTS: ...\\nCONSTRAINTS: ...\\nCONTEXT: ...
     *
     * @param response The response containing the delegation marker
     * @return The extracted brief content
     */
    private String extractBriefFromDelegationMarker(String response) {
        // Try COMPETENCY_EXPERT marker first
        int startIndex = response.indexOf(DELEGATE_TO_COMPETENCY_EXPERT);
        String marker = DELEGATE_TO_COMPETENCY_EXPERT;

        // If not found, try COMPETENCY_MAPPER marker
        if (startIndex == -1) {
            startIndex = response.indexOf(DELEGATE_TO_COMPETENCY_MAPPER);
            marker = DELEGATE_TO_COMPETENCY_MAPPER;
        }

        if (startIndex == -1) {
            return "";
        }

        int markerEnd = startIndex + marker.length();
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

            var result = new ArrayList<AtlasAgentHistoryMessageDTO>();

            for (Message message : messages) {
                String text = message.getText();
                boolean isUser = message.getMessageType() == MessageType.USER;

                // Skip internal system messages (delegation markers, briefings, and action confirmations)
                boolean isBriefing = text.startsWith("TOPIC:") || text.startsWith("TOPICS:") || text.startsWith("ACTION:")
                        || (text.contains("REQUIREMENTS:") && text.contains("CONSTRAINTS:") && text.contains("CONTEXT:"));
                boolean isDelegationMarker = text.contains(DELEGATE_TO_COMPETENCY_EXPERT) || text.contains(DELEGATE_TO_COMPETENCY_MAPPER) || text.contains(RETURN_TO_MAIN_AGENT);
                boolean isActionConfirmation = text.equals(CREATE_APPROVED_RELATION) || text.equals(CREATE_APPROVED_COMPETENCY);

                if (isBriefing || isDelegationMarker || isActionConfirmation) {
                    continue;
                }

                PreviewDataResult extracted = extractPreviewDataFromMessage(text);
                result.add(new AtlasAgentHistoryMessageDTO(extracted.cleanedText(), isUser, extracted.previews(), extracted.relationPreviews(), extracted.relationGraphPreview()));
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
     * @param response The agent's response text
     * @param previews Optional list of competency previews
     * @return The response text with embedded preview data marker
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
            // If JSON serialization fails, return response without preview data
            return response;
        }
    }

    /**
     * Embed relation preview data as a JSON marker in the response text.
     * This allows relation preview data to persist in chat memory and be reconstructed when loading history.
     *
     * @param response              The agent's response text
     * @param singleRelationPreview Optional single relation preview
     * @param batchRelationPreview  Optional batch relation preview
     * @return The response text with embedded relation preview data marker
     */
    private String embedRelationPreviewDataInResponse(String response, @Nullable SingleRelationPreviewResponseDTO singleRelationPreview,
            @Nullable BatchRelationPreviewResponseDTO batchRelationPreview, @Nullable RelationGraphPreviewDTO relationGraphPreview) {
        if (singleRelationPreview == null && batchRelationPreview == null && relationGraphPreview == null) {
            return response;
        }

        try {
            RelationPreviewDataContainer container = new RelationPreviewDataContainer(singleRelationPreview, batchRelationPreview, relationGraphPreview);
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
     * Handles both competency and relation preview data.
     *
     * @param messageText The message text potentially containing preview data markers
     * @return PreviewDataResult containing the cleaned text and extracted preview data
     */
    private PreviewDataResult extractPreviewDataFromMessage(String messageText) {
        int startIndex = messageText.indexOf(PREVIEW_DATA_START_MARKER);
        if (startIndex == -1) {
            return new PreviewDataResult(messageText, null, null, null);
        }

        int endIndex = messageText.indexOf(PREVIEW_DATA_END_MARKER, startIndex);
        if (endIndex == -1) {
            return new PreviewDataResult(messageText, null, null, null);
        }

        int jsonStart = startIndex + PREVIEW_DATA_START_MARKER.length();
        String jsonData = messageText.substring(jsonStart, endIndex);

        // Remove the marker section from the text
        String cleanedText = (messageText.substring(0, startIndex) + messageText.substring(endIndex + PREVIEW_DATA_END_MARKER.length())).trim();

        // Try to parse as competency preview first
        try {
            PreviewDataContainer container = objectMapper.readValue(jsonData, PreviewDataContainer.class);
            return new PreviewDataResult(cleanedText, container.previews(), null, null);
        }
        catch (JsonProcessingException e) {
            // If competency parsing fails, try relation preview
            try {
                RelationPreviewDataContainer relationContainer = objectMapper.readValue(jsonData, RelationPreviewDataContainer.class);
                List<CompetencyRelationPreviewDTO> relationPreviews = convertToRelationPreviewsList(relationContainer.singleRelationPreview(),
                        relationContainer.batchRelationPreview());
                return new PreviewDataResult(cleanedText, null, relationPreviews, relationContainer.relationGraphPreview());
            }
            catch (JsonProcessingException ex) {
                return new PreviewDataResult(cleanedText, null, null, null);
            }
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
    record PreviewDataContainer(@Nullable List<CompetencyPreviewDTO> previews) {
    }

    /**
     * Result of extracting preview data from a message.
     */
    record PreviewDataResult(String cleanedText, @Nullable List<CompetencyPreviewDTO> previews, @Nullable List<CompetencyRelationPreviewDTO> relationPreviews,
            @Nullable RelationGraphPreviewDTO relationGraphPreview) {
    }

    /**
     * Container for embedding relation preview data in message text.
     */
    record RelationPreviewDataContainer(@Nullable SingleRelationPreviewResponseDTO singleRelationPreview, @Nullable BatchRelationPreviewResponseDTO batchRelationPreview,
            @Nullable RelationGraphPreviewDTO relationGraphPreview) {
    }

    /**
     * Converts single/batch relation preview DTOs to a unified list of CompetencyRelationPreviewDTO.
     * This is a temporary helper during migration from the old structure.
     *
     * @param singleRelationPreview Optional single relation preview
     * @param batchRelationPreview  Optional batch relation preview
     * @return Unified list of relation previews, or null if no previews exist
     */
    private List<CompetencyRelationPreviewDTO> convertToRelationPreviewsList(@Nullable SingleRelationPreviewResponseDTO singleRelationPreview,
            @Nullable BatchRelationPreviewResponseDTO batchRelationPreview) {
        List<CompetencyRelationPreviewDTO> result = new ArrayList<>();

        if (singleRelationPreview != null && singleRelationPreview.preview()) {
            result.add(singleRelationPreview.relation());
        }

        if (batchRelationPreview != null && batchRelationPreview.batchPreview() && batchRelationPreview.relations() != null) {
            result.addAll(batchRelationPreview.relations());
        }

        return result.isEmpty() ? null : result;
    }

    /**
     * Check if the user message is a natural language approval for relation mapping.
     * Recognizes common approval phrases like "Map it", "Create it", "Yes", "Approve", etc.
     *
     * @param message The user's message
     * @return true if the message is a relation approval
     */
    private boolean isRelationApprovalMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        String normalized = message.trim().toLowerCase();

        // Common approval patterns for relation mapping
        return normalized.matches("(?i)^(map|create|yes|approve|ok|okay|confirm|looks good|perfect|great|good)\\s*(it|the relation|the mapping|relation|mapping)?[!.]*$");
    }
}
