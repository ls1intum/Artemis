package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.dto.IrisFullscreenContextDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageContextDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisSlidesContextDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisVideoContextDTO;

/**
 * Tests for extracting lectureUnitId from fullscreen context.
 * This logic is used in IrisChatPipelineExecutionService to filter RAG search to a specific lecture unit.
 */
class IrisFullscreenContextExtractionTest {

    /**
     * Extracts lectureUnitId from fullscreen context using the same logic as in IrisChatPipelineExecutionService.
     */
    private Long extractLectureUnitId(List<IrisMessageContextDTO> context) {
        return context.stream().filter(IrisFullscreenContextDTO.class::isInstance).map(IrisFullscreenContextDTO.class::cast).map(IrisFullscreenContextDTO::lectureUnitId)
                .findFirst().orElse(null);
    }

    @Test
    void extractsLectureUnitIdFromFullscreenContext() {
        // Given: A fullscreen context with lectureUnitId 123
        var fullscreenContext = new IrisFullscreenContextDTO(123L);
        List<IrisMessageContextDTO> context = List.of(fullscreenContext);

        // When: Extract lectureUnitId
        Long lectureUnitId = extractLectureUnitId(context);

        // Then: The lectureUnitId should be extracted
        assertThat(lectureUnitId).isEqualTo(123L);
    }

    @Test
    void returnsNullWhenNoFullscreenContext() {
        // Given: Context with only video and slides (no fullscreen)
        var videoContext = new IrisVideoContextDTO(123L, 45.2);
        var slidesContext = new IrisSlidesContextDTO(123L, 5);
        List<IrisMessageContextDTO> context = List.of(videoContext, slidesContext);

        // When: Extract lectureUnitId
        Long lectureUnitId = extractLectureUnitId(context);

        // Then: Should return null (no fullscreen context)
        assertThat(lectureUnitId).isNull();
    }

    @Test
    void returnsNullWhenContextIsEmpty() {
        // Given: Empty context
        List<IrisMessageContextDTO> context = List.of();

        // When: Extract lectureUnitId
        Long lectureUnitId = extractLectureUnitId(context);

        // Then: Should return null
        assertThat(lectureUnitId).isNull();
    }

}
