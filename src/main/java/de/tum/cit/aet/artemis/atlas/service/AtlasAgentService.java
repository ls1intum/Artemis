package de.tum.cit.aet.artemis.atlas.service;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private static final String DELEGATE_TO_COMPETENCY_EXPERT = "[DELEGATE_TO_COMPETENCY_EXPERT";

    private static final String CREATE_APPROVED_COMPETENCY = "[CREATE_APPROVED_COMPETENCY]";

    private static final String RETURN_TO_MAIN_AGENT = "[RETURN_TO_MAIN_AGENT]";

    // Track which agent is active for each session
    private final Map<String, AgentType> sessionAgentMap = new ConcurrentHashMap<>();

    // Track last preview response for each session (needed for creation approval)
    private final Map<String, String> sessionLastPreviewMap = new ConcurrentHashMap<>();

    private final ChatClient chatClient;

    private final AtlasPromptTemplateService templateService;

    private final ToolCallbackProvider mainAgentToolCallbackProvider;

    private final ToolCallbackProvider competencyExpertToolCallbackProvider;

    private final ChatMemory chatMemory;

    private final CompetencyExpertToolsService competencyExpertToolsService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AtlasAgentService(@Nullable ChatClient chatClient, AtlasPromptTemplateService templateService, @Nullable ToolCallbackProvider mainAgentToolCallbackProvider,
            @Nullable ToolCallbackProvider competencyExpertToolCallbackProvider, @Nullable ChatMemory chatMemory,
            @Nullable CompetencyExpertToolsService competencyExpertToolsService) {
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
                // Extract brief from marker: [DELEGATE_TO_COMPETENCY_EXPERT:brief_content]
                String brief = extractBriefFromDelegationMarker(response);

                // Remove the entire delegation block from response
                response = removeDelegationMarker(response).trim();

                // Check if this is a batch operation - handle directly for determinism
                String expertResponse;
                // Single operation - let Competency Expert handle via LLM
                expertResponse = processWithCompetencyExpert(brief, courseId, sessionId);
                // Sanitize expert response to extract only clean JSON
                expertResponse = sanitizeCompetencyExpertResponse(expertResponse);

                // Store preview for potential creation approval
                sessionLastPreviewMap.put(sessionId, expertResponse);

                // Append expert's response (preview JSON)
                response = response + "\n\n" + expertResponse;

                // Stay on MAIN_AGENT - Atlas Core continues managing the workflow
            }
            else if (response.contains(CREATE_APPROVED_COMPETENCY)) {
                // Instructor approved the preview, instruct Competency Expert to create
                response = response.replace(CREATE_APPROVED_COMPETENCY, "").trim();

                // Send creation command to Competency Expert (inline execution)
                String creationResponse = processWithCompetencyExpert(CREATE_APPROVED_COMPETENCY, courseId, sessionId);

                // Append creation confirmation
                response = response + "\n\n" + creationResponse;

                // Stay on MAIN_AGENT for potential next competency creation
            }
            else if (response.contains(RETURN_TO_MAIN_AGENT)) {
                sessionAgentMap.put(sessionId, AgentType.MAIN_AGENT);
                // Remove the marker from the response before returning to user
                response = response.replace(RETURN_TO_MAIN_AGENT, "").trim();
            }

            // Check if competency was created
            boolean competenciesModified = competencyExpertToolsService != null && competencyExpertToolsService.wasCompetencyModified();

            String finalResponse = !response.trim().isEmpty() ? response : "I apologize, but I couldn't generate a response.";

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
     * Remove the delegation marker and its content from the response.
     *
     * @param response The response containing the delegation marker
     * @return The response with the delegation marker removed
     */
    private String removeDelegationMarker(String response) {
        int startIndex = response.indexOf(DELEGATE_TO_COMPETENCY_EXPERT);
        if (startIndex == -1) {
            return response;
        }

        // Find the closing bracket
        int endIndex = response.indexOf("]", startIndex);
        if (endIndex == -1) {
            return response;
        }

        // Remove the entire delegation block
        return response.substring(0, startIndex) + response.substring(endIndex + 1);
    }

    /**
     * Sanitize Competency Expert response by extracting only clean JSON.
     * This makes the system deterministic by removing any conversational text the LLM might add.
     *
     * @param response The raw response from Competency Expert
     * @return Clean JSON string or original response if no JSON found
     */
    private String sanitizeCompetencyExpertResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return response;
        }

        try {
            // First, remove markdown code blocks if present
            String cleaned = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");

            // Try to find JSON with "preview" or "batchPreview" keys
            int previewIndex = Math.max(cleaned.indexOf("\"preview\""), cleaned.indexOf("\"batchPreview\""));

            if (previewIndex == -1) {
                // No preview JSON found - this might be an error message or success message
                // Return original response
                return response;
            }

            // Search backwards for the opening brace
            int startIndex = cleaned.lastIndexOf('{', previewIndex);
            if (startIndex == -1) {
                return response;
            }

            // Count braces to find the matching closing brace
            int braceCount = 0;
            int endIndex = -1;
            for (int i = startIndex; i < cleaned.length(); i++) {
                if (cleaned.charAt(i) == '{') {
                    braceCount++;
                }
                else if (cleaned.charAt(i) == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        endIndex = i;
                        break;
                    }
                }
            }

            if (endIndex == -1 || endIndex <= startIndex) {
                return response;
            }

            // Extract the JSON substring
            String jsonString = cleaned.substring(startIndex, endIndex + 1);

            // Validate it's proper JSON by trying to parse it
            JsonNode jsonNode = objectMapper.readTree(jsonString);

            // Check if it has the expected structure
            if (jsonNode.has("preview") || jsonNode.has("batchPreview")) {
                // Return the clean JSON string
                return jsonString;
            }

            // JSON doesn't have expected structure
            return response;
        }
        catch (Exception e) {
            // Failed to parse or extract JSON - return original response
            return response;
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
            return false;
        }
    }
}
