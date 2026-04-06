package de.tum.cit.aet.artemis.atlas.service;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.BatchRelationPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyRelationPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.ExerciseCompetencyMappingDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.RelationGraphPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.SingleRelationPreviewResponseDTO;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/**
 * Service for embedding and extracting preview data in Atlas Agent chat responses.
 * Handles serialisation of competency, relation, and exercise mapping preview DTOs
 * into hidden markers so they persist across chat memory reloads.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class AtlasAgentPreviewService {

    private static final String PREVIEW_DATA_START_MARKER = "%%PREVIEW_DATA_START%%";

    private static final String PREVIEW_DATA_END_MARKER = "%%PREVIEW_DATA_END%%";

    private final ObjectMapper objectMapper = JsonObjectMapper.get();

    private final ChatMemory chatMemory;

    public AtlasAgentPreviewService(@Nullable ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    /**
     * Container for embedding preview data in message text.
     */
    record PreviewDataContainer(@Nullable List<CompetencyPreviewDTO> previews) {
    }

    /**
     * Result of extracting preview data from a message.
     */
    public record PreviewDataResult(String cleanedText, @Nullable List<CompetencyPreviewDTO> previews, @Nullable List<CompetencyRelationPreviewDTO> relationPreviews,
            @Nullable RelationGraphPreviewDTO relationGraphPreview, @Nullable ExerciseCompetencyMappingDTO exerciseMappingPreview) {
    }

    /**
     * Container for embedding relation preview data in message text.
     */
    record RelationPreviewDataContainer(@Nullable SingleRelationPreviewResponseDTO singleRelationPreview, @Nullable BatchRelationPreviewResponseDTO batchRelationPreview,
            @Nullable RelationGraphPreviewDTO relationGraphPreview) {
    }

    /**
     * Container for embedding exercise mapping preview data in message text.
     */
    record ExerciseMappingPreviewDataContainer(@Nullable ExerciseCompetencyMappingDTO exerciseMappingPreview) {
    }

    /**
     * Embed preview data as a JSON marker in the response text.
     * This allows preview data to persist in chat memory and be reconstructed when loading history.
     *
     * @param response the agent's response text
     * @param previews optional list of competency previews
     * @return the response text with embedded preview data marker
     */
    public String embedPreviewDataInResponse(String response, @Nullable List<CompetencyPreviewDTO> previews) {
        if (previews == null || previews.isEmpty()) {
            return response;
        }

        try {
            PreviewDataContainer container = new PreviewDataContainer(previews);
            String jsonData = objectMapper.writeValueAsString(container);

            return response + " " + PREVIEW_DATA_START_MARKER + jsonData + PREVIEW_DATA_END_MARKER;
        }
        catch (JsonProcessingException e) {
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
     * @param relationGraphPreview  Optional relation graph preview
     * @return The response text with embedded relation preview data marker
     */
    public String embedRelationPreviewDataInResponse(String response, @Nullable SingleRelationPreviewResponseDTO singleRelationPreview,
            @Nullable BatchRelationPreviewResponseDTO batchRelationPreview, @Nullable RelationGraphPreviewDTO relationGraphPreview) {
        if (singleRelationPreview == null && batchRelationPreview == null && relationGraphPreview == null) {
            return response;
        }

        try {
            RelationPreviewDataContainer container = new RelationPreviewDataContainer(singleRelationPreview, batchRelationPreview, relationGraphPreview);
            String jsonData = objectMapper.writeValueAsString(container);

            return response + " " + PREVIEW_DATA_START_MARKER + jsonData + PREVIEW_DATA_END_MARKER;
        }
        catch (JsonProcessingException e) {
            return response;
        }
    }

    /**
     * Embed exercise mapping preview data as a JSON marker in the response text.
     * This allows exercise mapping preview data to persist in chat memory and be reconstructed when loading history.
     *
     * @param response               The agent's response text
     * @param exerciseMappingPreview Optional exercise mapping preview
     * @return The response text with embedded exercise mapping preview data marker
     */
    public String embedExerciseMappingPreviewDataInResponse(String response, @Nullable ExerciseCompetencyMappingDTO exerciseMappingPreview) {
        if (exerciseMappingPreview == null) {
            return response;
        }

        try {
            ExerciseMappingPreviewDataContainer container = new ExerciseMappingPreviewDataContainer(exerciseMappingPreview);
            String jsonData = objectMapper.writeValueAsString(container);

            return response + " " + PREVIEW_DATA_START_MARKER + jsonData + PREVIEW_DATA_END_MARKER;
        }
        catch (JsonProcessingException e) {
            return response;
        }
    }

    /**
     * Extract preview data from a message text that contains embedded preview markers.
     * Handles competency, relation, and exercise mapping preview data.
     *
     * @param messageText The message text potentially containing preview data markers
     * @return PreviewDataResult containing the cleaned text and extracted preview data
     */
    public PreviewDataResult extractPreviewDataFromMessage(String messageText) {
        int startIndex = messageText.indexOf(PREVIEW_DATA_START_MARKER);
        if (startIndex == -1) {
            return new PreviewDataResult(messageText, null, null, null, null);
        }

        int endIndex = messageText.indexOf(PREVIEW_DATA_END_MARKER, startIndex);
        if (endIndex == -1) {
            return new PreviewDataResult(messageText, null, null, null, null);
        }

        int jsonStart = startIndex + PREVIEW_DATA_START_MARKER.length();
        String jsonData = messageText.substring(jsonStart, endIndex);

        String cleanedText = (messageText.substring(0, startIndex) + messageText.substring(endIndex + PREVIEW_DATA_END_MARKER.length())).trim();

        try {
            JsonNode node = objectMapper.readTree(jsonData);

            if (node.has("exerciseMappingPreview")) {
                ExerciseMappingPreviewDataContainer exerciseContainer = objectMapper.treeToValue(node, ExerciseMappingPreviewDataContainer.class);
                return new PreviewDataResult(cleanedText, null, null, null, exerciseContainer.exerciseMappingPreview());
            }

            if (node.has("singleRelationPreview") || node.has("batchRelationPreview") || node.has("relationGraphPreview")) {
                RelationPreviewDataContainer relationContainer = objectMapper.treeToValue(node, RelationPreviewDataContainer.class);
                List<CompetencyRelationPreviewDTO> relationPreviews = convertToRelationPreviewsList(relationContainer.singleRelationPreview(),
                        relationContainer.batchRelationPreview());
                return new PreviewDataResult(cleanedText, null, relationPreviews, relationContainer.relationGraphPreview(), null);
            }

            PreviewDataContainer container = objectMapper.treeToValue(node, PreviewDataContainer.class);
            return new PreviewDataResult(cleanedText, container.previews(), null, null, null);
        }
        catch (JsonProcessingException e) {
            return new PreviewDataResult(cleanedText, null, null, null, null);
        }
    }

    /**
     * Updates the last assistant message in chat memory with embedded preview data.
     * This ensures preview data persists in conversation history.
     *
     * @param sessionId                the session ID
     * @param responseWithEmbeddedData the response text with embedded preview data
     * @param originalResponse         the original response text without embedded data
     */
    public void updateChatMemoryWithEmbeddedData(String sessionId, String responseWithEmbeddedData, String originalResponse) {
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
     * Converts single/batch relation preview DTOs to a unified list of CompetencyRelationPreviewDTO.
     *
     * @param singleRelationPreview Optional single relation preview
     * @param batchRelationPreview  Optional batch relation preview
     * @return Unified list of relation previews, or null if no previews exist
     */
    public List<CompetencyRelationPreviewDTO> convertToRelationPreviewsList(@Nullable SingleRelationPreviewResponseDTO singleRelationPreview,
            @Nullable BatchRelationPreviewResponseDTO batchRelationPreview) {
        List<CompetencyRelationPreviewDTO> result = new ArrayList<>();

        if (singleRelationPreview != null && singleRelationPreview.preview() && singleRelationPreview.relation() != null) {
            result.add(singleRelationPreview.relation());
        }

        if (batchRelationPreview != null && batchRelationPreview.batchPreview() && batchRelationPreview.relations() != null) {
            result.addAll(batchRelationPreview.relations());
        }

        return result.isEmpty() ? null : result;
    }

    /**
     * Adds an assistant message directly to chat memory.
     * Used for plan continuation responses that are generated with saveToMemory=false.
     *
     * @param sessionId the session ID
     * @param message   the assistant message text
     */
    public void addAssistantMessageToMemory(String sessionId, String message) {
        if (chatMemory != null) {
            chatMemory.add(sessionId, new AssistantMessage(message));
        }
    }
}
