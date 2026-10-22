package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.CourseBrowserDataDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedContentObjectDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedContentPresenceDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedEntityRecordDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionTypeCountDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.MissingContentDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.MissingEntityDTO;
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
                Map.of(COURSE_ID, Set.of(11L)), Set.of(COURSE_ID));
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
        verify(setLoader).loadExpected(List.of(COURSE_ID));
        verify(setLoader).loadPresent(List.of(COURSE_ID));
        verify(coverageRecomputeService).storeCourseCoverage(course, expected, present);

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
        ExpectedSets expected = new ExpectedSets(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Set.of());
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
        var mapper = JsonObjectMapper.get();

        assertThat(mapper.writeValueAsString(new IndexedContentPresenceDTO(IngestionBrowserWeaviateReadService.KEY_SLIDES, Set.of(7L), Set.of(7L))))
                .contains("\"orphanedUnitIds\":[7]");
        assertThat(mapper.writeValueAsString(new IndexedContentPresenceDTO(IngestionBrowserWeaviateReadService.KEY_SLIDES, Set.of(7L), Set.of())))
                .doesNotContain("orphanedUnitIds");
    }

    @Test
    void serializesSparseBrowserPayloadWithConfiguredMapperAndKeepsFalseAndZero() {
        var mapper = JsonObjectMapper.get();
        var empty = new CourseBrowserDataDTO(List.of(), List.of(), List.of(), List.of(), List.of());
        assertThat(mapper.readTree(mapper.writeValueAsString(empty)).isObject()).isTrue();
        assertThat(mapper.readTree(mapper.writeValueAsString(empty))).isEmpty();

        var entity = new IndexedEntityDTO("lecture", 0, null, null, null, false);
        var presence = new IndexedContentPresenceDTO("slides", Set.of(0L), Set.of());
        var missing = new MissingEntityDTO("lecture_unit", 0, null, null);
        var gap = new MissingContentDTO(0, null, "transcript");
        var payload = new CourseBrowserDataDTO(List.of(entity), List.of(presence), List.of(missing), List.of(gap), List.of());
        var json = mapper.readTree(mapper.writeValueAsString(payload));
        assertThat(json.get("entities").isArray()).isTrue();
        var row = json.get("entities").get(0);
        assertThat(row.propertyNames()).containsExactlyInAnyOrder("type", "entityId", "expected");
        assertThat(row.get("entityId").longValue()).isZero();
        assertThat(row.get("expected").isBoolean()).isTrue();
        assertThat(row.get("expected").booleanValue()).isFalse();
        assertThat(json.has("typeCounts")).isFalse();
        assertThat(json.get("contentPresence").get(0).get("unitIds").isArray()).isTrue();
        assertThat(json.get("contentPresence").get(0).get("unitIds").get(0).longValue()).isZero();
        assertThat(json.get("contentPresence").get(0).has("orphanedUnitIds")).isFalse();
        assertThat(json.get("missingEntities").get(0).propertyNames()).containsExactlyInAnyOrder("type", "entityId");
        assertThat(json.get("contentGaps").get(0).get("kind").stringValue()).isEqualTo("transcript");
        var blankIdentityType = mapper.readTree(mapper.writeValueAsString(new IndexedEntityDTO("", 1, null, null, null, false)));
        assertThat(blankIdentityType.has("type")).isFalse();
    }

    @Test
    void serializesOpenPropertyMapsAndOmitsEmptyBagsAndUnknownTimes() {
        var mapper = JsonObjectMapper.get();
        var record = mapper.readTree(mapper.writeValueAsString(new IndexedEntityRecordDTO("lecture", 0, null, null, Map.of())));
        assertThat(record.propertyNames()).containsExactlyInAnyOrder("type", "entityId");
        var emptyContent = mapper.readTree(mapper.writeValueAsString(new IndexedContentObjectDTO(null, Map.of())));
        assertThat(emptyContent).isEmpty();
        var content = new IndexedContentObjectDTO(null, Map.of("page_number", 0, "flag", false, "nested", Map.of("title", "<script>"), "list", List.of(0, false)));
        var json = mapper.readTree(mapper.writeValueAsString(content)).get("properties");
        assertThat(json.get("page_number").intValue()).isZero();
        assertThat(json.get("flag").booleanValue()).isFalse();
        assertThat(json.get("nested").get("title").stringValue()).isEqualTo("<script>");
        assertThat(json.get("list").isArray()).isTrue();
    }

}
