package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.BatchRelationPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyRelationPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.ExerciseCompetencyMappingDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.SingleRelationPreviewResponseDTO;

@ExtendWith(MockitoExtension.class)
class AtlasAgentPreviewServiceTest {

    @Mock
    private ChatMemory chatMemory;

    private AtlasAgentPreviewService previewService;

    private AtlasAgentPreviewService previewServiceNullMemory;

    @BeforeEach
    void setUp() {
        previewService = new AtlasAgentPreviewService(chatMemory);
        previewServiceNullMemory = new AtlasAgentPreviewService(null);
    }

    @Nested
    class EmbedPreviewDataInResponse {

        @Test
        void shouldReturnOriginalResponseWhenPreviewsNull() {
            String response = "Some response";
            String result = previewService.embedPreviewDataInResponse(response, null);
            assertThat(result).isEqualTo(response);
        }

        @Test
        void shouldReturnOriginalResponseWhenPreviewsEmpty() {
            String response = "Some response";
            String result = previewService.embedPreviewDataInResponse(response, List.of());
            assertThat(result).isEqualTo(response);
        }

        @Test
        void shouldEmbedPreviewDataWhenPreviewsPresent() {
            String response = "Some response";
            CompetencyPreviewDTO preview = new CompetencyPreviewDTO("OOP", "Object-Oriented Programming", "APPLY", null, null);
            String result = previewService.embedPreviewDataInResponse(response, List.of(preview));
            assertThat(result).startsWith("Some response");
            assertThat(result).contains("%%PREVIEW_DATA_START%%");
            assertThat(result).contains("%%PREVIEW_DATA_END%%");
            assertThat(result).contains("OOP");
        }
    }

    @Nested
    class EmbedRelationPreviewDataInResponse {

        @Test
        void shouldReturnOriginalResponseWhenAllNull() {
            String response = "Some response";
            String result = previewService.embedRelationPreviewDataInResponse(response, null, null, null);
            assertThat(result).isEqualTo(response);
        }

        @Test
        void shouldEmbedSingleRelationPreview() {
            CompetencyRelationPreviewDTO relation = new CompetencyRelationPreviewDTO(null, 1L, "OOP", 2L, "Design Patterns", RelationType.ASSUMES, false);
            SingleRelationPreviewResponseDTO single = new SingleRelationPreviewResponseDTO(true, relation, false);

            String result = previewService.embedRelationPreviewDataInResponse("Response", single, null, null);
            assertThat(result).contains("%%PREVIEW_DATA_START%%");
            assertThat(result).contains("singleRelationPreview");
        }

        @Test
        void shouldEmbedBatchRelationPreview() {
            CompetencyRelationPreviewDTO rel = new CompetencyRelationPreviewDTO(null, 1L, "A", 2L, "B", RelationType.EXTENDS, false);
            BatchRelationPreviewResponseDTO batch = new BatchRelationPreviewResponseDTO(true, 1, List.of(rel), false);

            String result = previewService.embedRelationPreviewDataInResponse("Response", null, batch, null);
            assertThat(result).contains("%%PREVIEW_DATA_START%%");
            assertThat(result).contains("batchRelationPreview");
        }
    }

    @Nested
    class EmbedExerciseMappingPreviewDataInResponse {

        @Test
        void shouldReturnOriginalResponseWhenPreviewNull() {
            String response = "Some response";
            String result = previewService.embedExerciseMappingPreviewDataInResponse(response, null);
            assertThat(result).isEqualTo(response);
        }

        @Test
        void shouldEmbedExerciseMappingPreview() {
            ExerciseCompetencyMappingDTO.CompetencyMappingOptionDTO option = new ExerciseCompetencyMappingDTO.CompetencyMappingOptionDTO(1L, "OOP", 0.5, false, true);
            ExerciseCompetencyMappingDTO preview = new ExerciseCompetencyMappingDTO(42L, "Java Basics", List.of(option), false);

            String result = previewService.embedExerciseMappingPreviewDataInResponse("Response", preview);
            assertThat(result).contains("%%PREVIEW_DATA_START%%");
            assertThat(result).contains("exerciseMappingPreview");
            assertThat(result).contains("Java Basics");
        }
    }

    @Nested
    class ExtractPreviewDataFromMessage {

        @Test
        void shouldReturnCleanTextWhenNoMarkers() {
            String message = "Plain message with no markers";
            AtlasAgentPreviewService.PreviewDataResult result = previewService.extractPreviewDataFromMessage(message);
            assertThat(result.cleanedText()).isEqualTo(message);
            assertThat(result.previews()).isNull();
            assertThat(result.relationPreviews()).isNull();
            assertThat(result.relationGraphPreview()).isNull();
            assertThat(result.exerciseMappingPreview()).isNull();
        }

        @Test
        void shouldReturnOriginalWhenEndMarkerMissing() {
            String message = "Message %%PREVIEW_DATA_START%%{no end}";
            AtlasAgentPreviewService.PreviewDataResult result = previewService.extractPreviewDataFromMessage(message);
            assertThat(result.cleanedText()).isEqualTo(message);
            assertThat(result.previews()).isNull();
        }

        @Test
        void shouldExtractCompetencyPreviews() {
            String message = "Here's a preview %%PREVIEW_DATA_START%%{\"previews\":[{\"title\":\"OOP\",\"description\":\"Desc\",\"taxonomy\":\"APPLY\",\"competencyId\":null,\"viewOnly\":null}]}%%PREVIEW_DATA_END%%";
            AtlasAgentPreviewService.PreviewDataResult result = previewService.extractPreviewDataFromMessage(message);
            assertThat(result.cleanedText()).isEqualTo("Here's a preview");
            assertThat(result.previews()).isNotNull();
            assertThat(result.previews()).hasSize(1);
            assertThat(result.previews().getFirst().title()).isEqualTo("OOP");
        }

        @Test
        void shouldExtractRelationPreview() {
            String message = "Relation preview %%PREVIEW_DATA_START%%{\"singleRelationPreview\":{\"preview\":true,\"relation\":{\"headCompetencyId\":1,\"headCompetencyTitle\":\"A\",\"tailCompetencyId\":2,\"tailCompetencyTitle\":\"B\",\"relationType\":\"ASSUMES\"},\"viewOnly\":false}}%%PREVIEW_DATA_END%%";
            AtlasAgentPreviewService.PreviewDataResult result = previewService.extractPreviewDataFromMessage(message);
            assertThat(result.cleanedText()).isEqualTo("Relation preview");
            assertThat(result.relationPreviews()).isNotNull();
        }

        @Test
        void shouldExtractExerciseMappingPreview() {
            String message = "Exercise preview %%PREVIEW_DATA_START%%{\"exerciseMappingPreview\":{\"exerciseId\":42,\"exerciseTitle\":\"Java Basics\",\"competencies\":[],\"viewOnly\":false}}%%PREVIEW_DATA_END%%";
            AtlasAgentPreviewService.PreviewDataResult result = previewService.extractPreviewDataFromMessage(message);
            assertThat(result.cleanedText()).isEqualTo("Exercise preview");
            assertThat(result.exerciseMappingPreview()).isNotNull();
            assertThat(result.exerciseMappingPreview().exerciseTitle()).isEqualTo("Java Basics");
        }

        @Test
        void shouldHandleMalformedJson() {
            String message = "Message %%PREVIEW_DATA_START%%{not valid json}%%PREVIEW_DATA_END%%";
            AtlasAgentPreviewService.PreviewDataResult result = previewService.extractPreviewDataFromMessage(message);
            assertThat(result.cleanedText()).isEqualTo("Message");
            assertThat(result.previews()).isNull();
        }
    }

    @Nested
    class UpdateChatMemoryWithEmbeddedData {

        @Test
        void shouldUpdateLastAssistantMessageWhenDataDiffers() {
            String sessionId = "test_session";
            String original = "Original response";
            String withEmbedded = "Original response %%PREVIEW_DATA_START%%{}%%PREVIEW_DATA_END%%";

            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new UserMessage("User question"));
            messages.add(new AssistantMessage(original));

            when(chatMemory.get(sessionId)).thenReturn(messages);

            previewService.updateChatMemoryWithEmbeddedData(sessionId, withEmbedded, original);

            verify(chatMemory).clear(sessionId);
            verify(chatMemory).add(sessionId, new UserMessage("User question"));
        }

        @Test
        void shouldNotUpdateWhenResponseUnchanged() {
            String sessionId = "test_session";
            String response = "Same response";

            previewService.updateChatMemoryWithEmbeddedData(sessionId, response, response);

            // chatMemory.get() should not be called since strings are equal
        }

        @Test
        void shouldNotUpdateWhenChatMemoryNull() {
            String sessionId = "test_session";
            // Should not throw
            previewServiceNullMemory.updateChatMemoryWithEmbeddedData(sessionId, "with embedded", "original");
        }

        @Test
        void shouldNotUpdateWhenMessagesEmpty() {
            String sessionId = "test_session";
            when(chatMemory.get(sessionId)).thenReturn(List.of());

            previewService.updateChatMemoryWithEmbeddedData(sessionId, "with embedded", "original");

            // Should not call clear or add
        }
    }

    @Nested
    class ConvertToRelationPreviewsList {

        @Test
        void shouldReturnNullWhenBothNull() {
            List<CompetencyRelationPreviewDTO> result = previewService.convertToRelationPreviewsList(null, null);
            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNullWhenPreviewFlagFalse() {
            CompetencyRelationPreviewDTO rel = new CompetencyRelationPreviewDTO(null, 1L, "A", 2L, "B", RelationType.ASSUMES, null);
            SingleRelationPreviewResponseDTO single = new SingleRelationPreviewResponseDTO(false, rel, false);

            List<CompetencyRelationPreviewDTO> result = previewService.convertToRelationPreviewsList(single, null);
            assertThat(result).isNull();
        }

        @Test
        void shouldReturnSingleRelationWhenPreviewTrue() {
            CompetencyRelationPreviewDTO rel = new CompetencyRelationPreviewDTO(null, 1L, "A", 2L, "B", RelationType.ASSUMES, null);
            SingleRelationPreviewResponseDTO single = new SingleRelationPreviewResponseDTO(true, rel, false);

            List<CompetencyRelationPreviewDTO> result = previewService.convertToRelationPreviewsList(single, null);
            assertThat(result).isNotNull();
            assertThat(result).hasSize(1);
        }

        @Test
        void shouldReturnBatchRelations() {
            CompetencyRelationPreviewDTO rel1 = new CompetencyRelationPreviewDTO(null, 1L, "A", 2L, "B", RelationType.ASSUMES, null);
            CompetencyRelationPreviewDTO rel2 = new CompetencyRelationPreviewDTO(null, 3L, "C", 4L, "D", RelationType.EXTENDS, null);
            BatchRelationPreviewResponseDTO batch = new BatchRelationPreviewResponseDTO(true, 2, List.of(rel1, rel2), false);

            List<CompetencyRelationPreviewDTO> result = previewService.convertToRelationPreviewsList(null, batch);
            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
        }

        @Test
        void shouldCombineSingleAndBatchRelations() {
            CompetencyRelationPreviewDTO single = new CompetencyRelationPreviewDTO(null, 1L, "A", 2L, "B", RelationType.ASSUMES, null);
            CompetencyRelationPreviewDTO batchRel = new CompetencyRelationPreviewDTO(null, 3L, "C", 4L, "D", RelationType.EXTENDS, null);

            SingleRelationPreviewResponseDTO singleDTO = new SingleRelationPreviewResponseDTO(true, single, false);
            BatchRelationPreviewResponseDTO batchDTO = new BatchRelationPreviewResponseDTO(true, 1, List.of(batchRel), false);

            List<CompetencyRelationPreviewDTO> result = previewService.convertToRelationPreviewsList(singleDTO, batchDTO);
            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    class AddAssistantMessageToMemory {

        @Test
        void shouldAddMessageWhenChatMemoryPresent() {
            String sessionId = "test_session";
            String message = "Assistant reply";

            previewService.addAssistantMessageToMemory(sessionId, message);

            verify(chatMemory).add(sessionId, new AssistantMessage(message));
        }

        @Test
        void shouldNotThrowWhenChatMemoryNull() {
            // Should not throw
            previewServiceNullMemory.addAssistantMessageToMemory("session", "message");
        }
    }
}
