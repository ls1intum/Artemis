package de.tum.cit.aet.artemis.globalsearch.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.dto.CourseBrowserDataDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedContentObjectDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedEntityRecordDTO;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionBrowserService;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionBrowserWeaviateReadService;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService;

/**
 * Admin-only, read-only endpoints for the per-course content browser: what the index holds for a course, and what it is
 * missing. Only available when Weaviate is enabled.
 * <p>
 * TEMPORARY (revert before merge): relaxed from admin to instructor and moved out of the {@code /admin/} segment, for the same reason as the
 * coverage resource beside it.
 * <p>
 * Opening a course is one request, because everything it renders derives from the same two id-sets and splitting it up
 * meant reloading those sets per part. The second endpoint is fetched only when an admin selects a collection under a
 * lecture unit, since the content objects are the one payload heavy enough that loading them up front would not pay for
 * itself.
 */
@Profile(PROFILE_CORE)
@Conditional(WeaviateEnabled.class)
@Lazy
@RestController
@RequestMapping("api/global-search/ingestion-dashboard/")
public class IngestionBrowserResource {

    private static final String ENTITY_NAME = "ingestionBrowser";

    private final IngestionBrowserService browserService;

    private final IngestionBrowserWeaviateReadService browserReadService;

    private final CourseRepository courseRepository;

    public IngestionBrowserResource(IngestionBrowserService browserService, IngestionBrowserWeaviateReadService browserReadService, CourseRepository courseRepository) {
        this.browserService = browserService;
        this.browserReadService = browserReadService;
        this.courseRepository = courseRepository;
    }

    /**
     * GET .../courses/{courseId}/browser : everything the content browser renders when it opens a course, in one
     * response: the stored entities, which units hold content per Iris collection, the entities the index is missing,
     * and the per-unit content gaps.
     *
     * @param courseId the course to inspect
     * @return the browser payload for the course
     */
    @EnforceAtLeastInstructor
    @GetMapping("courses/{courseId}/browser")
    public ResponseEntity<CourseBrowserDataDTO> getCourseBrowserData(@PathVariable long courseId) {
        requireCourse(courseId);
        return ResponseEntity.ok(browserService.loadCourseBrowserData(courseId));
    }

    /**
     * GET .../courses/{courseId}/entities : the full stored records of one entity type for a course, property maps
     * included. Fetched when an admin selects a type, a lecture or a unit in the tree, because those records carry the
     * entity's body text and are not worth loading for a whole course up front.
     *
     * @param courseId the course to inspect
     * @param type     the entity type to read
     * @return the stored records of that type
     */
    @EnforceAtLeastInstructor
    @GetMapping("courses/{courseId}/entities")
    public ResponseEntity<List<IndexedEntityRecordDTO>> getIndexedEntityRecords(@PathVariable long courseId, @RequestParam String type) {
        requireCourse(courseId);
        if (!IngestionCoverageWeaviateReadService.METADATA_TYPES.contains(type)) {
            throw new BadRequestAlertException("Unknown entity type '" + type + "'; expected one of " + IngestionCoverageWeaviateReadService.METADATA_TYPES, ENTITY_NAME,
                    "unknownEntityType");
        }
        return ResponseEntity.ok(browserReadService.listIndexedEntityRecords(courseId, type));
    }

    /**
     * GET .../courses/{courseId}/units/{unitId}/content : the objects stored for one lecture unit in one Iris collection,
     * with their property maps. This is the leaf of the browser, fetched when an admin selects a collection node.
     *
     * @param courseId the course the unit belongs to, also filtered on so a mismatched pair returns nothing
     * @param unitId   the lecture unit to read
     * @param key      the content key naming the collection ({@code slides}, {@code transcript}, {@code unit_summary},
     *                     {@code segments})
     * @return the stored objects
     */
    @EnforceAtLeastInstructor
    @GetMapping("courses/{courseId}/units/{unitId}/content")
    public ResponseEntity<List<IndexedContentObjectDTO>> getUnitContent(@PathVariable long courseId, @PathVariable long unitId, @RequestParam String key) {
        requireCourse(courseId);
        if (!IngestionBrowserWeaviateReadService.isKnownContentKey(key)) {
            throw new BadRequestAlertException("Unknown content key '" + key + "'; expected one of " + IngestionBrowserWeaviateReadService.contentKeys(), ENTITY_NAME,
                    "unknownContentKey");
        }
        return ResponseEntity.ok(browserReadService.listContentObjectsForUnit(courseId, unitId, key));
    }

    /**
     * Rejects an unknown course before any index read, so a bad id is a 404 rather than an empty result that looks like a
     * fully unindexed course.
     */
    private void requireCourse(long courseId) {
        courseRepository.findByIdElseThrow(courseId);
    }
}
