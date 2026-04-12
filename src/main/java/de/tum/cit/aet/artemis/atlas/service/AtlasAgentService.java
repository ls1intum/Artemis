package de.tum.cit.aet.artemis.atlas.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.AtlasAgentChatResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.AtlasAgentHistoryMessageDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.BatchRelationPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyRelationPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.ExerciseCompetencyMappingDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.RelationGraphPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.SingleRelationPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.service.CompetencyExpertToolsService.CompetencyOperation;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/**
 * Service for Atlas Agent functionality with Azure OpenAI integration.
 * Handles chat interactions and competency-related AI assistance.
 * Manages multi-agent orchestration between Main Agent and Sub-agents (Competency Expert Sub-agent).
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class AtlasAgentService {

    private static final Logger log = LoggerFactory.getLogger(AtlasAgentService.class);

    enum AgentType {
        MAIN_AGENT, COMPETENCY_EXPERT, COMPETENCY_MAPPER, EXERCISE_MAPPER
    }

    private static final String CREATE_APPROVED_COMPETENCY = "[CREATE_APPROVED_COMPETENCY]";

    private static final String CREATE_APPROVED_RELATION = "[CREATE_APPROVED_RELATION]";

    private static final String CREATE_APPROVED_EXERCISE_MAPPING = "[CREATE_APPROVED_EXERCISE_MAPPING]";

    private static final Pattern PLAN_MARKER_PATTERN = Pattern.compile("%%ARTEMIS_PLAN:([A-Z_]+)(?::exerciseId=(\\d+))?(?::exerciseTitle=([^%]*))?%%");

    private final ChatClient chatClient;

    private final ChatMemory chatMemory;

    private final AtlasAgentDelegationService delegationService;

    private final AtlasAgentToolCallbackService toolCallbackFactory;

    private final AtlasAgentToolsService toolsService;

    private final ObjectMapper objectMapper = JsonObjectMapper.get();

    public Boolean getCompetencyModifiedInCurrentRequest() {
        return competencyModifiedInCurrentRequest.get();
    }

    private static final ThreadLocal<Boolean> competencyModifiedInCurrentRequest = ThreadLocal.withInitial(() -> false);

    private final AtlasAgentSessionCacheService atlasAgentSessionCacheService;

    private final ExecutionPlanStateManagerService executionPlanStateManagerService;

    private final AtlasAgentPreviewService previewService;

    public AtlasAgentService(@Nullable ChatClient chatClient, @Nullable ChatMemory chatMemory, AtlasAgentDelegationService delegationService,
            AtlasAgentToolCallbackService toolCallbackFactory, AtlasAgentToolsService toolsService, ExecutionPlanStateManagerService executionPlanStateManagerService,
            AtlasAgentSessionCacheService atlasAgentSessionCacheService, AtlasAgentPreviewService previewService) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.delegationService = delegationService;
        this.toolCallbackFactory = toolCallbackFactory;
        this.toolsService = toolsService;
        this.executionPlanStateManagerService = executionPlanStateManagerService;
        this.atlasAgentSessionCacheService = atlasAgentSessionCacheService;
        this.previewService = previewService;
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
            return new AtlasAgentChatResponseDTO("Atlas Agent is not available. Please contact your administrator.", ZonedDateTime.now(), false, null, null, null, null);
        }

        try {
            CompetencyExpertToolsService.setCurrentSessionId(sessionId);
            AtlasAgentToolsService.setCurrentCourseId(courseId);
            AtlasAgentToolsService.setCurrentSessionId(sessionId);
            resetCompetencyModifiedFlag();

            // Check for cancel command when plan is active
            if (isCancelCommand(message) && executionPlanStateManagerService.hasPlan(sessionId)) {
                executionPlanStateManagerService.cancelPlan(sessionId);
                return new AtlasAgentChatResponseDTO("Plan cancelled.", ZonedDateTime.now(), false, null, null, null, null);
            }

            if (message.equals(CREATE_APPROVED_COMPETENCY)) {
                return handleCompetencyApproval(sessionId, courseId);
            }
            else if (message.equals(CREATE_APPROVED_RELATION)) {
                return handleRelationApproval(sessionId, courseId);
            }
            else if (message.startsWith(CREATE_APPROVED_EXERCISE_MAPPING)) {
                return handleExerciseMappingApproval(sessionId, courseId, message);
            }

            // Call main agent — it may invoke delegation tools (delegateToCompetencyExpert, etc.) during response generation
            String response = delegateToAgent(AgentType.MAIN_AGENT, message, courseId, sessionId);

            // Detect and initialize plan if orchestrator output a plan marker
            detectAndInitializePlan(response, message, sessionId);

            // Detect approval markers in the response (user said "create it" / "looks good" and the main agent output the marker)
            if (response != null && response.strip().equals(CREATE_APPROVED_COMPETENCY)) {
                return handleCompetencyApproval(sessionId, courseId);
            }
            else if (response != null && response.strip().equals(CREATE_APPROVED_RELATION)) {
                return handleRelationApproval(sessionId, courseId);
            }
            else if (response != null && response.strip().startsWith(CREATE_APPROVED_EXERCISE_MAPPING)) {
                return handleExerciseMappingApproval(sessionId, courseId, response.strip());
            }

            // Collect all preview data from ThreadLocals (whichever sub-agent was invoked via delegation tools)
            List<CompetencyPreviewDTO> competencyPreviews = CompetencyExpertToolsService.getAndClearPreviews();

            SingleRelationPreviewResponseDTO singleRelationPreview = CompetencyMappingToolsService.getSingleRelationPreview();
            BatchRelationPreviewResponseDTO batchRelationPreview = CompetencyMappingToolsService.getBatchRelationPreview();
            RelationGraphPreviewDTO relationGraphPreview = CompetencyMappingToolsService.getRelationGraphPreview();

            ExerciseCompetencyMappingDTO exerciseMappingPreview = ExerciseMappingToolsService.getExerciseMappingPreview();
            if (exerciseMappingPreview == null) {
                exerciseMappingPreview = atlasAgentSessionCacheService.getCachedExerciseMappingPreview(sessionId);
            }

            List<CompetencyRelationPreviewDTO> relationPreviews = previewService.convertToRelationPreviewsList(singleRelationPreview, batchRelationPreview);
            RelationGraphPreviewDTO graphForDto = relationGraphPreview;

            boolean hasPreviewData = (competencyPreviews != null && !competencyPreviews.isEmpty()) || (relationPreviews != null && !relationPreviews.isEmpty())
                    || graphForDto != null || exerciseMappingPreview != null;
            log.debug("processChatMessage: hasPreviewData={}, competencyPreviews={}, relationPreviews={}, exerciseMapping={}, responseLength={}", hasPreviewData,
                    competencyPreviews != null ? competencyPreviews.size() : "null", relationPreviews != null ? relationPreviews.size() : "null", exerciseMappingPreview != null,
                    response != null ? response.length() : 0);

            // Store preview data in Hazelcast cache (keyed by assistant message index) instead of embedding markers in chat memory.
            // The current response was already added to chat memory by Spring AI's MessageChatMemoryAdvisor,
            // so the index of the current response = count - 1.
            if (hasPreviewData) {
                int assistantIndex = countAssistantMessages(sessionId) - 1;
                log.debug("processChatMessage: storing preview at assistantIndex={}", assistantIndex);
                if (assistantIndex >= 0) {
                    atlasAgentSessionCacheService.storePreviewForMessage(sessionId, assistantIndex,
                            new AtlasAgentSessionCacheService.MessagePreviewData(competencyPreviews, relationPreviews, graphForDto, exerciseMappingPreview));
                }
            }

            String clientResponse = response;

            return new AtlasAgentChatResponseDTO(clientResponse, ZonedDateTime.now(), competencyModifiedInCurrentRequest.get(),
                    competencyPreviews != null && !competencyPreviews.isEmpty() ? competencyPreviews : null,
                    relationPreviews != null && !relationPreviews.isEmpty() ? relationPreviews : null, graphForDto, exerciseMappingPreview);
        }
        catch (Exception e) {
            log.error("Error processing chat message for session {}", sessionId, e);
            if (executionPlanStateManagerService.hasPlan(sessionId)) {
                executionPlanStateManagerService.cancelPlan(sessionId);
            }
            return new AtlasAgentChatResponseDTO("I apologize, but I'm having trouble processing your request right now. Please try again later.", ZonedDateTime.now(), false, null,
                    null, null, null);
        }
        finally {
            competencyModifiedInCurrentRequest.remove();
            CompetencyExpertToolsService.clearCurrentSessionId();
            CompetencyExpertToolsService.getAndClearPreviews();
            CompetencyMappingToolsService.clearCurrentSessionId();
            CompetencyMappingToolsService.clearAllPreviews();
            ExerciseMappingToolsService.clearUserSelectedMappings();
            ExerciseMappingToolsService.clearExerciseMappingPreview();
            AtlasAgentToolsService.clearCurrentCourseId();
            AtlasAgentToolsService.clearCurrentSessionId();
            atlasAgentSessionCacheService.clearCachedExerciseMappingPreview(sessionId);
        }

    }

    /**
     * Delegate message processing to the specified agent type.
     *
     * @param agentType the type of agent to use
     * @param message   the user's message
     * @param courseId  the course ID for context
     * @param sessionId the session ID for chat memory
     * @return the agent's response
     */
    String delegateToAgent(AgentType agentType, String message, Long courseId, String sessionId) {
        return delegateToAgent(agentType, message, courseId, sessionId, true);
    }

    /**
     * Delegate message processing to the specified agent type.
     *
     * @param agentType    the type of agent to use
     * @param message      the user's message
     * @param courseId     the course ID for context
     * @param sessionId    the session ID for chat memory
     * @param saveToMemory whether to add message to chat memory
     * @return the agent's response
     */
    String delegateToAgent(AgentType agentType, String message, Long courseId, String sessionId, boolean saveToMemory) {
        String resourcePath = getPromptResourcePath(agentType);
        ToolCallbackProvider toolCallbackProvider = getToolCallbackProvider(agentType);
        return delegationService.delegateToAgent(resourcePath, message, courseId, sessionId, saveToMemory, toolCallbackProvider);
    }

    private ToolCallbackProvider getToolCallbackProvider(AgentType agentType) {
        return switch (agentType) {
            case MAIN_AGENT -> toolCallbackFactory.createMainAgentProvider(toolsService);
            case COMPETENCY_EXPERT -> toolCallbackFactory.createCompetencyExpertProvider();
            case COMPETENCY_MAPPER -> toolCallbackFactory.createCompetencyMapperProvider();
            case EXERCISE_MAPPER -> toolCallbackFactory.createExerciseMapperProvider();
        };
    }

    static String getPromptResourcePath(AgentType agentType) {
        return switch (agentType) {
            case MAIN_AGENT -> "/prompts/atlas/agent_system_prompt.st";
            case COMPETENCY_EXPERT -> "/prompts/atlas/competency_expert_system_prompt.st";
            case COMPETENCY_MAPPER -> "/prompts/atlas/competency_mapper_system_prompt.st";
            case EXERCISE_MAPPER -> "/prompts/atlas/exercise_mapper_system_prompt.st";
        };
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
     * Check if the Atlas Agent service is available and properly configured.
     *
     * @return true if the service is ready, false otherwise
     */
    public boolean isAvailable() {
        return chatClient != null && chatMemory != null;
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

            var result = new ArrayList<AtlasAgentHistoryMessageDTO>();
            Map<Integer, AtlasAgentSessionCacheService.MessagePreviewData> previewHistory = atlasAgentSessionCacheService.getPreviewHistory(sessionId);
            int assistantIndex = 0;

            for (Message message : messages) {
                String text = message.getText();
                boolean isUser = message.getMessageType() == MessageType.USER;

                // Skip internal system messages (briefings, action confirmations, and plan continuations)
                boolean isBriefing = text.startsWith("TOPIC:") || text.startsWith("TOPICS:") || text.startsWith("ACTION:")
                        || (text.contains("REQUIREMENTS:") && text.contains("CONSTRAINTS:") && text.contains("CONTEXT:"));
                boolean isActionConfirmation = text.equals(CREATE_APPROVED_RELATION) || text.equals(CREATE_APPROVED_COMPETENCY) || text.equals(CREATE_APPROVED_EXERCISE_MAPPING)
                        || text.startsWith(CREATE_APPROVED_EXERCISE_MAPPING + ":");
                boolean isPlanContinuation = text.startsWith("MULTI-STEP PLAN CONTINUATION");

                if (isBriefing || isActionConfirmation || isPlanContinuation) {
                    if (!isUser) {
                        assistantIndex++;
                    }
                    continue;
                }

                if (isUser) {
                    result.add(new AtlasAgentHistoryMessageDTO(text, true, null, null, null, null));
                }
                else {
                    AtlasAgentSessionCacheService.MessagePreviewData previewData = previewHistory.get(assistantIndex);
                    assistantIndex++;
                    String historyText = text;
                    result.add(new AtlasAgentHistoryMessageDTO(historyText, false, previewData != null ? previewData.competencyPreviews() : null,
                            previewData != null ? previewData.relationPreviews() : null, previewData != null ? previewData.relationGraphPreview() : null,
                            previewData != null ? previewData.exerciseMappingPreview() : null));
                }
            }

            return result;
        }
        catch (Exception e) {
            return List.of();
        }
    }

    public String generateSessionId(Long courseId, Long userId) {
        return String.format("course_%d_user_%d", courseId, userId);
    }

    /**
     * Handles competency approval (button click or text approval).
     * Extracted to ensure consistent handling regardless of how approval is triggered.
     *
     * @param sessionId the session ID
     * @param courseId  the course ID
     * @return the response DTO with potential plan continuation
     */
    private AtlasAgentChatResponseDTO handleCompetencyApproval(String sessionId, Long courseId) {
        log.info("handleCompetencyApproval: starting for session={}, courseId={}", sessionId, courseId);
        List<CompetencyOperation> cachedData = atlasAgentSessionCacheService.getCachedPendingCompetencyOperations(sessionId);

        String creationResponse = delegateToAgent(AgentType.COMPETENCY_EXPERT, CREATE_APPROVED_COMPETENCY, courseId, sessionId);
        List<CompetencyPreviewDTO> previews = CompetencyExpertToolsService.getAndClearPreviews();
        log.info("handleCompetencyApproval: received {} previews, viewOnly statuses: {}", previews.size(), previews.stream().map(p -> p.title() + "=" + p.viewOnly()).toList());

        if (cachedData != null && !cachedData.isEmpty()) {
            atlasAgentSessionCacheService.clearCachedPendingCompetencyOperations(sessionId);
        }

        // Store preview data in cache for history reconstruction
        if (!previews.isEmpty()) {
            int assistantIndex = countAssistantMessages(sessionId) - 1;
            if (assistantIndex >= 0) {
                atlasAgentSessionCacheService.storePreviewForMessage(sessionId, assistantIndex, new AtlasAgentSessionCacheService.MessagePreviewData(previews, null, null, null));
            }
        }

        // Filter out viewOnly=true previews - user already saw these during preview phase
        List<CompetencyPreviewDTO> actionablePreviews = previews.stream().filter(p -> !Boolean.TRUE.equals(p.viewOnly())).toList();
        if (actionablePreviews.isEmpty()) {
            actionablePreviews = null;
        }

        // Build best-effort StepResult from saved previews
        ExecutionPlanStateManagerService.StepResult stepResult = buildStepResultFromPreviews(previews);
        log.info("handleCompetencyApproval: stepResult={}, hasPlan={}", stepResult != null ? stepResult.summary() + " IDs:" + stepResult.ids() : "null",
                executionPlanStateManagerService.hasPlan(sessionId));

        // Attempt plan continuation
        return handlePlanContinuationAfterApproval(sessionId, courseId, creationResponse, stepResult,
                new AtlasAgentChatResponseDTO(creationResponse, ZonedDateTime.now(), true, actionablePreviews, null, null, null));
    }

    /**
     * Handles relation approval (button click or text approval).
     * Extracted to ensure consistent handling regardless of how approval is triggered.
     *
     * @param sessionId the session ID
     * @param courseId  the course ID
     * @return the response DTO with potential plan continuation
     */
    private AtlasAgentChatResponseDTO handleRelationApproval(String sessionId, Long courseId) {
        List<CompetencyRelationDTO> cachedRelationData = atlasAgentSessionCacheService.getCachedRelationData(sessionId);

        CompetencyMappingToolsService.setCurrentSessionId(sessionId);

        String creationResponse = delegateToAgent(AgentType.COMPETENCY_MAPPER, CREATE_APPROVED_RELATION, courseId, sessionId);

        SingleRelationPreviewResponseDTO singleRelationPreview = CompetencyMappingToolsService.getSingleRelationPreview();
        BatchRelationPreviewResponseDTO batchRelationPreview = CompetencyMappingToolsService.getBatchRelationPreview();
        RelationGraphPreviewDTO relationGraphPreview = CompetencyMappingToolsService.getRelationGraphPreview();

        if (cachedRelationData != null && !cachedRelationData.isEmpty()) {
            atlasAgentSessionCacheService.clearCachedRelationOperations(sessionId);
        }

        List<CompetencyRelationPreviewDTO> relationPreviews = previewService.convertToRelationPreviewsList(singleRelationPreview, batchRelationPreview);

        // Store preview data in cache for history reconstruction
        if (relationPreviews != null && !relationPreviews.isEmpty()) {
            int assistantIndex = countAssistantMessages(sessionId) - 1;
            if (assistantIndex >= 0) {
                atlasAgentSessionCacheService.storePreviewForMessage(sessionId, assistantIndex,
                        new AtlasAgentSessionCacheService.MessagePreviewData(null, relationPreviews, relationGraphPreview, null));
            }
        }

        ExecutionPlanStateManagerService.StepResult stepResult = buildStepResultFromRelationPreviews(relationPreviews);

        return handlePlanContinuationAfterApproval(sessionId, courseId, creationResponse, stepResult,
                new AtlasAgentChatResponseDTO(creationResponse, ZonedDateTime.now(), false, null, relationPreviews, relationGraphPreview, null));
    }

    /**
     * Handles exercise mapping approval (button click or text approval).
     * Extracted to ensure consistent handling regardless of how approval is triggered.
     * <p>
     * When the message contains a JSON payload (format: {@code [CREATE_APPROVED_EXERCISE_MAPPING]:{...}}),
     * the user-selected competency IDs and weights are parsed and stored in a ThreadLocal so that
     * {@link ExerciseMappingToolsService#saveExerciseCompetencyMappings} uses them instead of
     * re-deriving mappings from the LLM's memory.
     *
     * @param sessionId       the session ID
     * @param courseId        the course ID
     * @param originalMessage the original message containing the approval (with optional JSON payload)
     * @return the response DTO with potential plan continuation
     */
    private AtlasAgentChatResponseDTO handleExerciseMappingApproval(String sessionId, Long courseId, String originalMessage) {
        int payloadStart = originalMessage.indexOf(':');
        if (payloadStart != -1) {
            String json = originalMessage.substring(payloadStart + 1);
            try {
                record MappingSelection(Long competencyId, Double weight) {
                }
                record ApprovalPayload(Long exerciseId, List<MappingSelection> mappings) {
                }
                ApprovalPayload payload = objectMapper.readValue(json, ApprovalPayload.class);
                if (payload.mappings() != null && !payload.mappings().isEmpty()) {
                    List<ExerciseMappingToolsService.ExerciseCompetencyMappingOperation> selected = payload.mappings().stream()
                            .map(m -> new ExerciseMappingToolsService.ExerciseCompetencyMappingOperation(m.competencyId(), m.weight(), false, false)).toList();
                    ExerciseMappingToolsService.setUserSelectedMappings(selected);
                }
            }
            catch (Exception e) {
                log.warn("Could not parse exercise mapping approval payload, falling back to LLM-provided mappings: {}", e.getMessage());
            }
        }

        ExerciseMappingToolsService.setCurrentSessionId(sessionId);
        String creationResponse = delegateToAgent(AgentType.EXERCISE_MAPPER, CREATE_APPROVED_EXERCISE_MAPPING, courseId, sessionId);

        ExecutionPlanStateManagerService.StepResult stepResult = new ExecutionPlanStateManagerService.StepResult(List.of(), "Exercise mappings saved");

        // Attempt plan continuation
        return handlePlanContinuationAfterApproval(sessionId, courseId, creationResponse, stepResult,
                new AtlasAgentChatResponseDTO(creationResponse, ZonedDateTime.now(), false, null, null, null, null));
    }

    /**
     * Checks if the user message is a cancel/stop command.
     *
     * @param message the user's message
     * @return true if the message is a cancel command
     */
    private boolean isCancelCommand(String message) {
        String lowerMessage = message.toLowerCase().trim();
        return lowerMessage.equals("cancel") || lowerMessage.equals("stop") || lowerMessage.equals("abort") || lowerMessage.equals("cancel plan")
                || lowerMessage.equals("stop plan") || lowerMessage.equals("cancel workflow") || lowerMessage.equals("stop workflow");
    }

    /**
     * Delegates to the next agent in the plan and returns the response.
     *
     * @param agentType        the type of agent to delegate to
     * @param brief            the enriched brief for the agent
     * @param courseId         the course ID
     * @param sessionId        the session ID
     * @param previousResponse the response from the previous step (for context)
     * @return the response DTO from the next agent
     */
    private AtlasAgentChatResponseDTO delegateToNextStepAgent(ExecutionPlanStateManagerService.AgentType agentType, String brief, Long courseId, String sessionId,
            String previousResponse) {

        log.info("delegateToNextStepAgent: delegating to agent={} for session={}", agentType, sessionId);
        AgentType internalAgentType = mapPlanAgentToInternal(agentType);

        // Set appropriate session ID for tool calls
        switch (agentType) {
            case COMPETENCY_EXPERT -> CompetencyExpertToolsService.setCurrentSessionId(sessionId);
            case COMPETENCY_MAPPER -> CompetencyMappingToolsService.setCurrentSessionId(sessionId);
            case EXERCISE_MAPPER -> ExerciseMappingToolsService.setCurrentSessionId(sessionId);
        }

        // Use saveToMemory=false to prevent internal brief from appearing in chat history
        String delegationResponse = delegateToAgent(internalAgentType, brief, courseId, sessionId, false);
        log.info("delegateToNextStepAgent: agent={} responded with {} chars", agentType, delegationResponse != null ? delegationResponse.length() : 0);

        // Manually add the next step's response to chat memory (since saveToMemory was false)
        String combinedResponse = previousResponse + "\n\n---\n\nContinuing to next step...\n\n" + delegationResponse;

        // Retrieve previews based on agent type and build response
        switch (agentType) {
            case COMPETENCY_EXPERT -> {
                List<CompetencyPreviewDTO> previews = CompetencyExpertToolsService.getAndClearPreviews();
                log.info("delegateToNextStepAgent: COMPETENCY_EXPERT produced {} previews", previews.size());
                previewService.addAssistantMessageToMemory(sessionId, combinedResponse);
                if (!previews.isEmpty()) {
                    int assistantIdx = countAssistantMessages(sessionId) - 1;
                    atlasAgentSessionCacheService.storePreviewForMessage(sessionId, assistantIdx, new AtlasAgentSessionCacheService.MessagePreviewData(previews, null, null, null));
                }
                return new AtlasAgentChatResponseDTO(combinedResponse, ZonedDateTime.now(), competencyModifiedInCurrentRequest.get(), previews, null, null, null);
            }
            case COMPETENCY_MAPPER -> {
                SingleRelationPreviewResponseDTO singlePreview = CompetencyMappingToolsService.getSingleRelationPreview();
                BatchRelationPreviewResponseDTO batchPreview = CompetencyMappingToolsService.getBatchRelationPreview();
                RelationGraphPreviewDTO graphPreview = CompetencyMappingToolsService.getRelationGraphPreview();
                log.info("delegateToNextStepAgent: COMPETENCY_MAPPER produced singlePreview={}, batchPreview={}, graphPreview={}", singlePreview != null, batchPreview != null,
                        graphPreview != null);
                previewService.addAssistantMessageToMemory(sessionId, combinedResponse);
                List<CompetencyRelationPreviewDTO> relationPreviews = previewService.convertToRelationPreviewsList(singlePreview, batchPreview);
                log.info("delegateToNextStepAgent: COMPETENCY_MAPPER converted to {} relation previews", relationPreviews != null ? relationPreviews.size() : 0);
                if (relationPreviews != null && !relationPreviews.isEmpty()) {
                    int assistantIdx = countAssistantMessages(sessionId) - 1;
                    atlasAgentSessionCacheService.storePreviewForMessage(sessionId, assistantIdx,
                            new AtlasAgentSessionCacheService.MessagePreviewData(null, relationPreviews, graphPreview, null));
                }
                return new AtlasAgentChatResponseDTO(combinedResponse, ZonedDateTime.now(), competencyModifiedInCurrentRequest.get(), null, relationPreviews, graphPreview, null);
            }
            case EXERCISE_MAPPER -> {
                // Fall back to Hazelcast when ThreadLocal is empty (cross-node execution)
                ExerciseCompetencyMappingDTO exercisePreview = ExerciseMappingToolsService.getExerciseMappingPreview();
                if (exercisePreview == null) {
                    exercisePreview = atlasAgentSessionCacheService.getCachedExerciseMappingPreview(sessionId);
                }
                log.info("delegateToNextStepAgent: EXERCISE_MAPPER produced exercisePreview={}", exercisePreview != null);
                previewService.addAssistantMessageToMemory(sessionId, combinedResponse);
                if (exercisePreview != null) {
                    int assistantIdx = countAssistantMessages(sessionId) - 1;
                    atlasAgentSessionCacheService.storePreviewForMessage(sessionId, assistantIdx,
                            new AtlasAgentSessionCacheService.MessagePreviewData(null, null, null, exercisePreview));
                }
                return new AtlasAgentChatResponseDTO(combinedResponse, ZonedDateTime.now(), competencyModifiedInCurrentRequest.get(), null, null, null, exercisePreview);
            }
            default -> {
                return new AtlasAgentChatResponseDTO(delegationResponse, ZonedDateTime.now(), competencyModifiedInCurrentRequest.get(), null, null, null, null);
            }
        }
    }

    /**
     * Maps the ExecutionPlanStateManager.AgentType to the internal AgentType enum.
     *
     * @param planAgentType the plan agent type
     * @return the internal agent type
     */
    private AgentType mapPlanAgentToInternal(ExecutionPlanStateManagerService.AgentType planAgentType) {
        return switch (planAgentType) {
            case COMPETENCY_EXPERT -> AgentType.COMPETENCY_EXPERT;
            case COMPETENCY_MAPPER -> AgentType.COMPETENCY_MAPPER;
            case EXERCISE_MAPPER -> AgentType.EXERCISE_MAPPER;
        };
    }

    /**
     * Detects and initializes an execution plan from the orchestrator's response.
     * Looks for %%ARTEMIS_PLAN:TEMPLATE_NAME%% markers in the response.
     *
     * @param response  the orchestrator's response
     * @param userGoal  the original user message (used as plan goal)
     * @param sessionId the session ID
     */
    private void detectAndInitializePlan(String response, String userGoal, String sessionId) {
        // Guard: never re-initialize a plan that's already active for this session
        if (executionPlanStateManagerService.hasPlan(sessionId)) {
            return;
        }
        Matcher matcher = PLAN_MARKER_PATTERN.matcher(response);
        if (matcher.find()) {
            String templateName = matcher.group(1);
            ExecutionPlanStateManagerService.PlanTemplate template = ExecutionPlanStateManagerService.parseTemplate(templateName);
            if (template != null) {
                Long exerciseId = matcher.group(2) != null ? Long.parseLong(matcher.group(2)) : null;
                String exerciseTitle = matcher.group(3) != null ? matcher.group(3).trim() : null;
                executionPlanStateManagerService.initializePlan(sessionId, template, userGoal, exerciseId, exerciseTitle);
            }
            else {
                log.warn("detectAndInitializePlan: unknown template name '{}' in response for session={}", templateName, sessionId);
            }
        }
    }

    /**
     * Handles plan continuation after a successful approval.
     * If a plan exists, advances to the next step and delegates to the next agent.
     * If no plan exists or no next step, returns the fallback response.
     *
     * @param sessionId        the session ID
     * @param courseId         the course ID
     * @param previousResponse the response from the completed step
     * @param stepResult       best-effort step result (may be null)
     * @param fallbackResponse the response to return if no plan continuation occurs
     * @return the response DTO, potentially with next step preview appended
     */
    private AtlasAgentChatResponseDTO handlePlanContinuationAfterApproval(String sessionId, Long courseId, String previousResponse,
            ExecutionPlanStateManagerService.@Nullable StepResult stepResult, AtlasAgentChatResponseDTO fallbackResponse) {
        if (!executionPlanStateManagerService.hasPlan(sessionId)) {
            log.info("handlePlanContinuation: no plan found for session={}, returning fallback", sessionId);
            return fallbackResponse;
        }

        Optional<ExecutionPlanStateManagerService.NextStepContext> nextStepOpt = executionPlanStateManagerService.completeStepAndGetNext(sessionId, stepResult);
        if (nextStepOpt.isEmpty()) {
            log.info("handlePlanContinuation: no next step for session={} (plan complete or expired), returning fallback", sessionId);
            return fallbackResponse;
        }

        ExecutionPlanStateManagerService.NextStepContext nextStep = nextStepOpt.get();
        log.info("handlePlanContinuation: advancing to next step for session={}, nextAgent={}, previousResults={}", sessionId, nextStep.agentType(),
                nextStep.previousResults().size());
        String enrichedBrief = buildEnrichedBrief(nextStep);

        return delegateToNextStepAgent(nextStep.agentType(), enrichedBrief, courseId, sessionId, previousResponse);
    }

    /**
     * Builds an enriched brief for the next agent in the plan.
     * Includes the user's original goal and results from previous steps.
     *
     * @param nextStep the context for the next step
     * @return the enriched brief string
     */
    private String buildEnrichedBrief(ExecutionPlanStateManagerService.NextStepContext nextStep) {
        StringBuilder brief = new StringBuilder();

        // Add agent-specific action instruction so the agent knows exactly what to do
        String actionInstruction = getActionInstructionForAgent(nextStep);
        brief.append("ACTION: ").append(actionInstruction).append("\n");

        List<ExecutionPlanStateManagerService.StepResult> previousResults = nextStep.previousResults();
        if (!previousResults.isEmpty()) {
            brief.append("CONTEXT FROM PREVIOUS STEPS:\n");
            for (ExecutionPlanStateManagerService.StepResult result : previousResults) {
                brief.append("- ").append(result.summary());
                if (!result.ids().isEmpty()) {
                    brief.append(" (IDs: ").append(result.ids().stream().map(String::valueOf).collect(Collectors.joining(", "))).append(")");
                }
                brief.append("\n");
            }
        }

        brief.append("ORIGINAL USER REQUEST: ").append(nextStep.userGoal()).append("\n");
        brief.append("IMPORTANT: Do NOT repeat work from previous steps. Only perform the ACTION specified above.\n");

        log.info("Built enriched brief for session, agent={}, brief={}", nextStep.agentType(), brief);
        return brief.toString();
    }

    /**
     * Returns a clear, specific action instruction for the given agent type
     * to be used in the enriched brief during plan continuation.
     * This prevents agents from being confused about what to do.
     *
     * @param nextStep the context for the next step
     * @return a specific instruction string for the agent
     */
    private String getActionInstructionForAgent(ExecutionPlanStateManagerService.NextStepContext nextStep) {
        return switch (nextStep.agentType()) {
            case COMPETENCY_MAPPER -> "Suggest relation mappings between the competencies from the previous step. "
                    + "Call getCourseCompetencies first to get the competency IDs, then call suggestRelationMappingsUsingML or "
                    + "use previewRelationMappings to suggest appropriate relations (ASSUMES, EXTENDS, MATCHES) between them. " + "Set viewOnly=false for the preview.";
            case EXERCISE_MAPPER -> buildExerciseMapperInstruction(nextStep.exerciseId(), nextStep.exerciseTitle());
            case COMPETENCY_EXPERT -> "Create or update competencies as described in the original user request. " + "Use the context from previous steps if available.";
        };
    }

    private String buildExerciseMapperInstruction(@Nullable Long exerciseId, @Nullable String exerciseTitle) {
        if (exerciseId != null && exerciseTitle != null && !exerciseTitle.isBlank()) {
            return "Map the competencies from the previous step to the exercise below. " + "EXERCISE_ID: " + exerciseId + " | EXERCISE_TITLE: " + exerciseTitle + ". "
                    + "Call getCourseCompetencies to get competency IDs, then call " + "previewExerciseCompetencyMapping with viewOnly=false to show the interactive preview. "
                    + "Do NOT ask the user how they want to adjust mappings - the preview UI is interactive and handles that.";
        }
        // Fallback if exercise was not embedded in the plan marker (should not happen in normal flow)
        return "Map the competencies from the previous step to exercises in the course. " + "Call getCourseCompetencies to get competency IDs, then call "
                + "previewExerciseCompetencyMapping with viewOnly=false to show the interactive preview. "
                + "Do NOT ask the user how they want to adjust mappings - the preview UI is interactive and handles that.";
    }

    /**
     * Counts the number of assistant messages currently in chat memory for the given session.
     * Used to determine the index for storing preview data in the preview history cache.
     *
     * @param sessionId the session ID
     * @return the count of assistant messages
     */
    private int countAssistantMessages(String sessionId) {
        if (chatMemory == null) {
            return 0;
        }
        List<Message> messages = chatMemory.get(sessionId);
        return (int) messages.stream().filter(m -> m.getMessageType() == MessageType.ASSISTANT).count();
    }

    /**
     * Builds a best-effort StepResult from competency previews.
     *
     * @param previews the competency previews (may be null or empty)
     * @return the step result, or null if no meaningful data
     */
    private ExecutionPlanStateManagerService.@Nullable StepResult buildStepResultFromPreviews(@Nullable List<CompetencyPreviewDTO> previews) {
        if (previews == null || previews.isEmpty()) {
            return null;
        }

        List<Long> ids = previews.stream().map(CompetencyPreviewDTO::competencyId).filter(Objects::nonNull).toList();

        String summary = "Created competencies: " + previews.stream().map(CompetencyPreviewDTO::title).collect(Collectors.joining(", "));

        return new ExecutionPlanStateManagerService.StepResult(ids, summary);
    }

    /**
     * Builds a best-effort StepResult from relation previews.
     *
     * @param relationPreviews the relation previews (may be null or empty)
     * @return the step result, or null if no meaningful data
     */
    private ExecutionPlanStateManagerService.@Nullable StepResult buildStepResultFromRelationPreviews(@Nullable List<CompetencyRelationPreviewDTO> relationPreviews) {
        if (relationPreviews == null || relationPreviews.isEmpty()) {
            return null;
        }

        List<Long> ids = relationPreviews.stream().map(CompetencyRelationPreviewDTO::relationId).filter(Objects::nonNull).toList();

        String summary = "Created " + relationPreviews.size() + " relation(s)";

        return new ExecutionPlanStateManagerService.StepResult(ids, summary);
    }
}
