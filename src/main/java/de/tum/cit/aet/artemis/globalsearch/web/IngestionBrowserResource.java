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
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedContentObjectDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedContentPresenceDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.MissingContentDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.MissingEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionBrowserGapService;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionBrowserWeaviateReadService;

/**
 * Admin-only, read-only endpoints for the per-course content browser: what the index holds for a course, and what it is
 * missing. Only available when Weaviate is enabled; every endpoint requires admin.
 * <p>
 * Four of these are fired together when the browser opens a course, and are therefore scoped and bounded so that opening
 * a course stays a cheap operation. The fifth is fetched only when an admin selects a collection under a lecture unit,
 * because the content objects are the one payload heavy enough that loading them up front would not pay for itself.
 */
@Profile(PROFILE_CORE)
@Conditional(WeaviateEnabled.class)
@EnforceAdmin
@Lazy
@RestController
@RequestMapping("api/global-search/admin/")
public class IngestionBrowserResource {

    private static final String ENTITY_NAME = "ingestionBrowser";

    private final IngestionBrowserWeaviateReadService browserReadService;

    private final IngestionBrowserGapService gapService;

    private final CourseRepository courseRepository;

    public IngestionBrowserResource(IngestionBrowserWeaviateReadService browserReadService, IngestionBrowserGapService gapService, CourseRepository courseRepository) {
        this.browserReadService = browserReadService;
        this.gapService = gapService;
        this.courseRepository = courseRepository;
    }

    /**
     * GET .../courses/{courseId}/indexed-entities : the {@code SearchableEntities} rows stored for a course, each with the
     * properties Weaviate actually holds, for the browser's tree and its stored-record panes.
     *
     * @param courseId the course to inspect
     * @return the stored rows
     */
    @GetMapping("courses/{courseId}/indexed-entities")
    public ResponseEntity<List<IndexedEntityDTO>> getIndexedEntities(@PathVariable long courseId) {
        requireCourse(courseId);
        return ResponseEntity.ok(browserReadService.listIndexedEntitiesForCourse(courseId));
    }

    /**
     * GET .../courses/{courseId}/indexed-content : which lecture units hold content in each Iris collection, so the tree
     * knows which units to give a slides, transcript, summary or segments node. Presence only; the objects behind a node
     * are fetched when it is selected.
     *
     * @param courseId the course to inspect
     * @return one entry per content key that has any content for the course
     */
    @GetMapping("courses/{courseId}/indexed-content")
    public ResponseEntity<List<IndexedContentPresenceDTO>> getIndexedContent(@PathVariable long courseId) {
        requireCourse(courseId);
        return ResponseEntity.ok(browserReadService.listContentPresenceForCourse(courseId));
    }

    /**
     * GET .../courses/{courseId}/missing-entities : the entities the database expects to be indexed for a course that the
     * index does not hold, resolved to their titles.
     *
     * @param courseId the course to inspect
     * @return the missing entities, named
     */
    @GetMapping("courses/{courseId}/missing-entities")
    public ResponseEntity<List<MissingEntityDTO>> getMissingEntities(@PathVariable long courseId) {
        requireCourse(courseId);
        return ResponseEntity.ok(gapService.missingEntitiesForCourse(courseId));
    }

    /**
     * GET .../courses/{courseId}/content-gaps : the lecture units whose slide or transcript content was never ingested.
     * Empty when Iris is not enabled, since nothing ingests lecture content in that configuration.
     *
     * @param courseId the course to inspect
     * @return the per-unit content gaps, named
     */
    @GetMapping("courses/{courseId}/content-gaps")
    public ResponseEntity<List<MissingContentDTO>> getContentGaps(@PathVariable long courseId) {
        requireCourse(courseId);
        return ResponseEntity.ok(gapService.contentGapsForCourse(courseId));
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
