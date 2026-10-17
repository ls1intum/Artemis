package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.dto.IngestionTypeCountDTO;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageSetLoader.ExpectedSets;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageSetLoader.PresentSets;

/**
 * The content counts of a course depend on the ingestion window: within it, every PDF and video unit is required to hold
 * content; outside it, content is optional, because the ingestion path does not catch up on older courses.
 */
class CoverageContentWindowTest {

    private static final long COURSE_ID = 42L;

    // Units 1 and 2 have a PDF, unit 3 a video. The index holds slides for unit 1 and for unit 9, which has no PDF (any more).
    private static final PresentSets PRESENT = new PresentSets(Map.of(), Map.of(), Map.of(COURSE_ID, Set.of(1L, 9L)), Map.of(), Map.of(), Map.of());

    private static ExpectedSets expected(Set<Long> contentCourseIds) {
        return new ExpectedSets(Map.of(), Map.of(), Map.of(COURSE_ID, Set.of(1L, 2L, 3L)), Map.of(), Map.of(), Map.of(), Map.of(COURSE_ID, Set.of(1L, 2L)),
                Map.of(COURSE_ID, Set.of(3L)), contentCourseIds);
    }

    private static IngestionTypeCountDTO count(List<IngestionTypeCountDTO> counts, String type) {
        return counts.stream().filter(count -> count.type().equals(type)).findFirst().orElseThrow();
    }

    @Test
    void aCourseWithinTheWindowIsRequiredToHoldContentForEveryPdfAndVideoUnit() {
        List<IngestionTypeCountDTO> counts = CoverageRecomputeService.typeCountsForCourse(COURSE_ID, expected(Set.of(COURSE_ID)), PRESENT);

        assertThat(count(counts, CoverageRecomputeService.TYPE_SLIDES)).isEqualTo(new IngestionTypeCountDTO(CoverageRecomputeService.TYPE_SLIDES, 2, 2, 1, 1));
        assertThat(count(counts, CoverageRecomputeService.TYPE_TRANSCRIPT)).isEqualTo(new IngestionTypeCountDTO(CoverageRecomputeService.TYPE_TRANSCRIPT, 1, 0, 1, 0));
    }

    @Test
    void aCourseOutsideTheWindowMissesNothingButStillReportsContentOfAUnitWithoutAPdf() {
        List<IngestionTypeCountDTO> counts = CoverageRecomputeService.typeCountsForCourse(COURSE_ID, expected(Set.of()), PRESENT);

        assertThat(count(counts, CoverageRecomputeService.TYPE_SLIDES)).isEqualTo(new IngestionTypeCountDTO(CoverageRecomputeService.TYPE_SLIDES, 1, 2, 0, 1));
        assertThat(count(counts, CoverageRecomputeService.TYPE_TRANSCRIPT)).isEqualTo(new IngestionTypeCountDTO(CoverageRecomputeService.TYPE_TRANSCRIPT, 0, 0, 0, 0));
    }
}
