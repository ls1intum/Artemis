package de.tum.cit.aet.artemis.globalsearch.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.CourseBrowserDataDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedContentPresenceDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageSetLoader.ExpectedSets;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageSetLoader.PresentSets;

/**
 * Assembles everything the content browser needs to open a course.
 * <p>
 * The point of this class is that the two id-sets are loaded exactly once. Every part of the response derives from
 * them, so serving the parts from separate endpoints meant each one reloaded the same sets: opening a single course
 * previously cost around nineteen Weaviate round trips and twenty database queries, roughly half of them repeats of
 * work another part of the same page load had already done.
 * <p>
 * Content presence in particular now costs nothing extra. {@link PresentSets} already carries the distinct present-unit
 * set for each Iris collection, because the coverage matrix needs the same numbers, so the browser reads it from there
 * rather than asking Weaviate again.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class IngestionBrowserService {

    private final IngestionCoverageSetLoader setLoader;

    private final IngestionBrowserWeaviateReadService browserReadService;

    private final IngestionBrowserGapService gapService;

    private final CoverageRecomputeService coverageRecomputeService;

    public IngestionBrowserService(IngestionCoverageSetLoader setLoader, IngestionBrowserWeaviateReadService browserReadService, IngestionBrowserGapService gapService,
            CoverageRecomputeService coverageRecomputeService) {
        this.setLoader = setLoader;
        this.browserReadService = browserReadService;
        this.gapService = gapService;
        this.coverageRecomputeService = coverageRecomputeService;
    }

    /**
     * Loads the stored entities, the content presence, and both gap lists for one course, and stores the course's
     * coverage row from the same sets, so the matrix shows these numbers once the browser closes.
     *
     * @param course the course to inspect
     * @return everything the browser renders when it opens
     */
    public CourseBrowserDataDTO loadCourseBrowserData(Course course) {
        long courseId = course.getId();
        List<Long> courseIds = List.of(courseId);
        ExpectedSets expected = setLoader.loadExpected(courseIds);
        PresentSets present = setLoader.loadPresent(courseIds);
        coverageRecomputeService.storeCourseCoverage(course, expected, present);

        List<IndexedEntityDTO> entities = browserReadService.listIndexedEntitiesForCourse(courseId).stream().map(entity -> new IndexedEntityDTO(entity.type(), entity.entityId(),
                entity.title(), entity.lectureId(), entity.ingestedAt(), isExpectedMetadataEntity(courseId, entity, expected))).toList();
        return new CourseBrowserDataDTO(entities, contentPresence(courseId, present), gapService.missingEntities(courseId, expected, present),
                gapService.contentGaps(courseId, expected, present), CoverageRecomputeService.typeCountsForCourse(courseId, expected, present));
    }

    private static boolean isExpectedMetadataEntity(long courseId, IndexedEntityDTO entity, ExpectedSets expected) {
        return switch (entity.type()) {
            case SearchableEntitySchema.TypeValues.EXERCISE -> expected.exercises().getOrDefault(courseId, Set.of()).contains(entity.entityId());
            case SearchableEntitySchema.TypeValues.LECTURE -> expected.lectures().getOrDefault(courseId, Set.of()).contains(entity.entityId());
            case SearchableEntitySchema.TypeValues.LECTURE_UNIT -> expected.lectureUnits().getOrDefault(courseId, Set.of()).contains(entity.entityId());
            case SearchableEntitySchema.TypeValues.EXAM -> expected.exams().getOrDefault(courseId, Set.of()).contains(entity.entityId());
            case SearchableEntitySchema.TypeValues.FAQ -> expected.faqs().getOrDefault(courseId, Set.of()).contains(entity.entityId());
            case SearchableEntitySchema.TypeValues.CHANNEL -> expected.channels().getOrDefault(courseId, Set.of()).contains(entity.entityId());
            case SearchableEntitySchema.TypeValues.COURSE -> entity.entityId() == courseId;
            default -> false;
        };
    }

    /**
     * Reshapes the present-unit sets already loaded for the coverage diff into the per-collection form the tree draws
     * from, keeping the browser's own content keys out of the shared loader. Collections holding nothing for the course
     * are left out, so a unit only ever gets a node for a collection that has something in it.
     */
    private static List<IndexedContentPresenceDTO> contentPresence(long courseId, PresentSets present) {
        Map<String, Map<Long, Set<Long>>> byKey = Map.of(IngestionBrowserWeaviateReadService.KEY_SLIDES, present.slides(), IngestionBrowserWeaviateReadService.KEY_TRANSCRIPT,
                present.transcript(), IngestionBrowserWeaviateReadService.KEY_UNIT_SUMMARY, present.unitSummaries(), IngestionBrowserWeaviateReadService.KEY_SEGMENTS,
                present.segmentSummaries());

        List<IndexedContentPresenceDTO> presence = new ArrayList<>();
        for (String key : IngestionBrowserWeaviateReadService.contentKeys()) {
            Set<Long> unitIds = byKey.get(key).get(courseId);
            if (unitIds != null && !unitIds.isEmpty()) {
                presence.add(new IndexedContentPresenceDTO(key, unitIds));
            }
        }
        return presence;
    }
}
