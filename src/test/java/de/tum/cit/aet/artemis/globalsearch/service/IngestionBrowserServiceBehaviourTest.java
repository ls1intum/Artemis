package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedContentPresenceDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionTypeCountDTO;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageSetLoader.ExpectedSets;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageSetLoader.PresentSets;

class IngestionBrowserServiceBehaviourTest {

    private static final long COURSE_ID = 42L;

    @Test
    void keepsExpectedMetadataWhileSeparatelyMarkingRetainedContentThatNoLongerMatchesItsSource() {
        IngestionCoverageSetLoader setLoader = mock(IngestionCoverageSetLoader.class);
        IngestionBrowserWeaviateReadService browserReadService = mock(IngestionBrowserWeaviateReadService.class);
        IngestionBrowserGapService gapService = mock(IngestionBrowserGapService.class);
        CoverageRecomputeService coverageRecomputeService = mock(CoverageRecomputeService.class);
        IngestionBrowserService service = new IngestionBrowserService(setLoader, browserReadService, gapService, coverageRecomputeService);
        Course course = mock(Course.class);
        given(course.getId()).willReturn(COURSE_ID);

        // Unit 10 remains a valid, indexed database unit after its PDF source was removed. Unit 12 has no database row
        // left. Summaries are present-only, so 10 remains valid even though no slide or transcript is expected for it.
        ExpectedSets expected = new ExpectedSets(Map.of(), Map.of(COURSE_ID, Set.of(20L)), Map.of(COURSE_ID, Set.of(10L, 11L)), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(COURSE_ID, Set.of(11L)));
        PresentSets present = new PresentSets(Map.of(), Map.of(), Map.of(COURSE_ID, Set.of(10L, 12L)), Map.of(COURSE_ID, Set.of(11L)), Map.of(COURSE_ID, Set.of(10L, 12L)),
                Map.of(COURSE_ID, Set.of(10L, 12L)));
        given(setLoader.loadExpected(List.of(COURSE_ID))).willReturn(expected);
        given(setLoader.loadPresent(List.of(COURSE_ID))).willReturn(present);
        given(browserReadService.listIndexedEntitiesForCourse(COURSE_ID))
                .willReturn(List.of(new IndexedEntityDTO(SearchableEntitySchema.TypeValues.LECTURE, 20L, "Lecture", null, null, false),
                        new IndexedEntityDTO(SearchableEntitySchema.TypeValues.LECTURE_UNIT, 10L, "Retained unit", 20L, null, false)));
        given(gapService.missingEntities(COURSE_ID, expected, present)).willReturn(List.of());
        given(gapService.contentGaps(COURSE_ID, expected, present)).willReturn(List.of());

        var data = service.loadCourseBrowserData(course);

        assertThat(data.entities()).filteredOn(entity -> entity.type().equals(SearchableEntitySchema.TypeValues.LECTURE_UNIT)).singleElement()
                .satisfies(entity -> assertThat(entity.expected()).isTrue());
        assertThat(data.contentGaps()).isEmpty();
        assertThat(data.contentPresence()).extracting(IndexedContentPresenceDTO::key, IndexedContentPresenceDTO::unitIds, IndexedContentPresenceDTO::orphanedUnitIds)
                .containsExactly(tuple(IngestionBrowserWeaviateReadService.KEY_SLIDES, Set.of(10L, 12L), Set.of(10L, 12L)),
                        tuple(IngestionBrowserWeaviateReadService.KEY_TRANSCRIPT, Set.of(11L), Set.of()),
                        tuple(IngestionBrowserWeaviateReadService.KEY_UNIT_SUMMARY, Set.of(10L, 12L), Set.of(12L)),
                        tuple(IngestionBrowserWeaviateReadService.KEY_SEGMENTS, Set.of(10L, 12L), Set.of(12L)));
        assertThat(data.typeCounts()).extracting(IngestionTypeCountDTO::type, IngestionTypeCountDTO::expected, IngestionTypeCountDTO::indexed, IngestionTypeCountDTO::missing,
                IngestionTypeCountDTO::orphaned).contains(tuple(CoverageRecomputeService.TYPE_SLIDES, 0L, 2L, 0L, 2L),
                        tuple(CoverageRecomputeService.TYPE_TRANSCRIPT, 1L, 1L, 0L, 0L), tuple(CoverageRecomputeService.TYPE_UNIT_SUMMARY, 1L, 2L, 0L, 1L),
                        tuple(CoverageRecomputeService.TYPE_SEGMENT_SUMMARY, 1L, 2L, 0L, 1L));
    }

    @Test
    void omitsEmptyCollectionsWhenTheirExpectedAndPresentMapsHaveNoCourseEntry() {
        IngestionCoverageSetLoader setLoader = mock(IngestionCoverageSetLoader.class);
        IngestionBrowserWeaviateReadService browserReadService = mock(IngestionBrowserWeaviateReadService.class);
        IngestionBrowserGapService gapService = mock(IngestionBrowserGapService.class);
        CoverageRecomputeService coverageRecomputeService = mock(CoverageRecomputeService.class);
        IngestionBrowserService service = new IngestionBrowserService(setLoader, browserReadService, gapService, coverageRecomputeService);
        Course course = mock(Course.class);
        given(course.getId()).willReturn(COURSE_ID);
        ExpectedSets expected = new ExpectedSets(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        PresentSets present = new PresentSets(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        given(setLoader.loadExpected(List.of(COURSE_ID))).willReturn(expected);
        given(setLoader.loadPresent(List.of(COURSE_ID))).willReturn(present);
        given(browserReadService.listIndexedEntitiesForCourse(COURSE_ID)).willReturn(List.of());
        given(gapService.missingEntities(COURSE_ID, expected, present)).willReturn(List.of());
        given(gapService.contentGaps(COURSE_ID, expected, present)).willReturn(List.of());

        assertThat(service.loadCourseBrowserData(course).contentPresence()).isEmpty();
    }

    @Test
    void serializesOrphanedContentIdsOnlyWhenTheCollectionHasAny() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();

        assertThat(mapper.writeValueAsString(new IndexedContentPresenceDTO(IngestionBrowserWeaviateReadService.KEY_SLIDES, Set.of(7L), Set.of(7L))))
                .contains("\"orphanedUnitIds\":[7]");
        assertThat(mapper.writeValueAsString(new IndexedContentPresenceDTO(IngestionBrowserWeaviateReadService.KEY_SLIDES, Set.of(7L), Set.of())))
                .doesNotContain("orphanedUnitIds");
    }
}
