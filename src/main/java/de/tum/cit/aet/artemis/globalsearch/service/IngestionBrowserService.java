package de.tum.cit.aet.artemis.globalsearch.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.dto.CourseBrowserDataDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedContentPresenceDTO;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageSetLoader.ExpectedSets;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageSetLoader.PresentSets;

/**
 * Assembles everything the content browser needs to open a course.
 * <p>
 * The point of this class is that the two id-sets are loaded exactly once. All four parts of the response derive from
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

    public IngestionBrowserService(IngestionCoverageSetLoader setLoader, IngestionBrowserWeaviateReadService browserReadService, IngestionBrowserGapService gapService) {
        this.setLoader = setLoader;
        this.browserReadService = browserReadService;
        this.gapService = gapService;
    }

    /**
     * Loads the stored entities, the content presence, and both gap lists for one course.
     *
     * @param courseId the course to inspect
     * @return everything the browser renders when it opens
     */
    public CourseBrowserDataDTO loadCourseBrowserData(long courseId) {
        List<Long> courseIds = List.of(courseId);
        ExpectedSets expected = setLoader.loadExpected(courseIds);
        // Content can only exist for a course that has lecture units, and the content reads are the expensive ones, so a
        // course without units skips them entirely rather than aggregating four collections to find nothing.
        Set<Long> contentCourseIds = expected.lectureUnits().keySet();
        PresentSets present = setLoader.loadPresent(courseIds, contentCourseIds);

        return new CourseBrowserDataDTO(browserReadService.listIndexedEntitiesForCourse(courseId), contentPresence(courseId, present),
                gapService.missingEntities(courseId, expected, present), gapService.contentGaps(courseId, expected, present));
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
