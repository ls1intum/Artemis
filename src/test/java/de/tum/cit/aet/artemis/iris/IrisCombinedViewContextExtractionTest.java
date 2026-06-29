package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.dto.IrisCombinedViewContextDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageContextDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisSlidesContextDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisVideoContextDTO;

/**
 * Tests for extracting lectureUnitId from combined view context.
 * This logic is used in IrisChatPipelineExecutionService to filter RAG search to a specific lecture unit.
 */
class IrisCombinedViewContextExtractionTest {

    /**
     * Extracts lectureUnitId from combined view context using the same logic as in IrisChatPipelineExecutionService.
     */
    private Long extractLectureUnitId(List<IrisMessageContextDTO> context) {
        return context.stream().filter(IrisCombinedViewContextDTO.class::isInstance).map(IrisCombinedViewContextDTO.class::cast).map(IrisCombinedViewContextDTO::lectureUnitId)
                .findFirst().orElse(null);
    }

    @Test
    void extractsLectureUnitIdFromCombinedViewContext() {
        // Given: A combined view context whose nested slides context carries lectureUnitId 123
        var combinedViewContext = new IrisCombinedViewContextDTO(new IrisSlidesContextDTO(123L, 5), null);
        List<IrisMessageContextDTO> context = List.of(combinedViewContext);

        // When: Extract lectureUnitId
        Long lectureUnitId = extractLectureUnitId(context);

        // Then: The lectureUnitId should be derived from the nested context
        assertThat(lectureUnitId).isEqualTo(123L);
    }

    @Test
    void returnsNullWhenNoCombinedViewContext() {
        // Given: Context with only video and slides (no combined view)
        var videoContext = new IrisVideoContextDTO(123L, 45.2);
        var slidesContext = new IrisSlidesContextDTO(123L, 5);
        List<IrisMessageContextDTO> context = List.of(videoContext, slidesContext);

        // When: Extract lectureUnitId
        Long lectureUnitId = extractLectureUnitId(context);

        // Then: Should return null (no combined view context)
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
