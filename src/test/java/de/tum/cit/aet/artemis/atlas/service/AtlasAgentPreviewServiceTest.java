package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;

import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.BatchRelationPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyRelationPreviewDTO;
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
