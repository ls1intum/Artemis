package de.tum.cit.aet.artemis.globalsearch.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.dto.CourseIndexDriftDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedContentDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.MissingContentDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.MissingEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.CourseIndexCensusService;
import de.tum.cit.aet.artemis.globalsearch.service.CourseIndexCensusService.CourseCensus;
import de.tum.cit.aet.artemis.globalsearch.service.CourseIndexDriftService;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * Admin REST endpoint for read-only, live per-course index drift (indexed vs expected per entity type).
 * Nothing is persisted or written; the drift is recomputed on every request from the database and Weaviate.
 */
@Lazy
@RestController
@Conditional(WeaviateEnabled.class)
@RequestMapping("api/global-search/admin/")
public class GlobalSearchDriftAdminResource {

    private static final Logger log = LoggerFactory.getLogger(GlobalSearchDriftAdminResource.class);

    private final CourseIndexDriftService driftService;

    private final CourseIndexCensusService censusService;

    private final SearchableEntityWeaviateService searchableEntityWeaviateService;

    private final CourseRepository courseRepository;

    public GlobalSearchDriftAdminResource(CourseIndexDriftService driftService, CourseIndexCensusService censusService,
            SearchableEntityWeaviateService searchableEntityWeaviateService, CourseRepository courseRepository) {
        this.driftService = driftService;
        this.censusService = censusService;
        this.searchableEntityWeaviateService = searchableEntityWeaviateService;
        this.courseRepository = courseRepository;
    }

    /**
     * GET api/global-search/admin/index-census : per-course, per-type index drift for every course.
     * <p>
     * Computed live across all courses, so it scales with the number of courses; serving a persisted census snapshot
     * is the planned optimization.
     *
     * @return the per-course census, one entry per course
     */
    @GetMapping("index-census")
    @EnforceAdmin
    @Operation(summary = "All-course index census", description = "Per-course, per-type indexed vs expected drift across all courses, computed live")
    @ApiResponse(responseCode = "200", description = "The per-course census")
    public ResponseEntity<List<CourseCensus>> getIndexCensus() {
        log.debug("REST request to get the all-course index census");
        return ResponseEntity.ok(censusService.censusAllCourses());
    }

    /**
     * GET api/global-search/admin/courses/{courseId}/index-drift : per-type indexed vs expected drift.
     *
     * @param courseId the course id
     * @return the per-course drift snapshot
     */
    @GetMapping("courses/{courseId}/index-drift")
    @EnforceAdmin
    @Operation(summary = "Per-course index drift", description = "Indexed vs expected row counts per entity type for a course, computed live")
    @ApiResponse(responseCode = "200", description = "The per-course drift snapshot")
    public ResponseEntity<CourseIndexDriftDTO> getCourseIndexDrift(@PathVariable long courseId) {
        log.debug("REST request to get the index drift for course {}", courseId);
        courseRepository.findByIdElseThrow(courseId);
        return ResponseEntity.ok(driftService.getDrift(courseId));
    }

    /**
     * GET api/global-search/admin/courses/{courseId}/indexed-entities : the objects actually stored in Weaviate for a
     * course, each with its type, title, and ingestion time, for the admin course-content browser.
     *
     * @param courseId the course id
     * @return the indexed entities for the course
     */
    @GetMapping("courses/{courseId}/indexed-entities")
    @EnforceAdmin
    @Operation(summary = "Indexed entities for a course", description = "The objects stored in Weaviate for a course with their titles and ingestion times")
    @ApiResponse(responseCode = "200", description = "The indexed entities")
    public ResponseEntity<List<IndexedEntityDTO>> getIndexedEntities(@PathVariable long courseId) {
        log.debug("REST request to get the indexed entities for course {}", courseId);
        courseRepository.findByIdElseThrow(courseId);
        return ResponseEntity.ok(searchableEntityWeaviateService.listIndexedEntitiesForCourse(courseId));
    }

    /**
     * GET api/global-search/admin/courses/{courseId}/indexed-content : the objects stored in the Iris lecture-content
     * collections (slide chunks, transcript segments, unit summaries, aligned segments) for a course.
     *
     * @param courseId the course id
     * @return one group per non-empty Iris content collection, each with its stored objects
     */
    @GetMapping("courses/{courseId}/indexed-content")
    @EnforceAdmin
    @Operation(summary = "Indexed lecture content for a course", description = "Objects stored in the Iris lecture-content collections for a course")
    @ApiResponse(responseCode = "200", description = "The indexed lecture content")
    public ResponseEntity<List<IndexedContentDTO>> getIndexedContent(@PathVariable long courseId) {
        log.debug("REST request to get the indexed lecture content for course {}", courseId);
        courseRepository.findByIdElseThrow(courseId);
        return ResponseEntity.ok(searchableEntityWeaviateService.listIndexedContentForCourse(courseId));
    }

    /**
     * GET api/global-search/admin/courses/{courseId}/missing-entities : the entities the database expects to be indexed
     * for a course but that are absent from Weaviate, by name, for the admin course-content browser.
     *
     * @param courseId the course id
     * @return the missing entities, sorted by type then id
     */
    @GetMapping("courses/{courseId}/missing-entities")
    @EnforceAdmin
    @Operation(summary = "Missing entities for a course", description = "Entities expected by the database but not present in Weaviate, resolved to their titles")
    @ApiResponse(responseCode = "200", description = "The missing entities")
    public ResponseEntity<List<MissingEntityDTO>> getMissingEntities(@PathVariable long courseId) {
        log.debug("REST request to get the missing entities for course {}", courseId);
        courseRepository.findByIdElseThrow(courseId);
        return ResponseEntity.ok(censusService.missingEntitiesForCourse(courseId));
    }

    /**
     * GET api/global-search/admin/courses/{courseId}/content-gaps : the lecture units expected to have ingested content
     * (slides from a PDF, transcript from a video) that is absent from the Iris collections, by name and kind.
     *
     * @param courseId the course id
     * @return the per-unit content gaps
     */
    @GetMapping("courses/{courseId}/content-gaps")
    @EnforceAdmin
    @Operation(summary = "Content gaps for a course", description = "Lecture units expected to have ingested slide or transcript content that is missing from the Iris collections")
    @ApiResponse(responseCode = "200", description = "The per-unit content gaps")
    public ResponseEntity<List<MissingContentDTO>> getContentGaps(@PathVariable long courseId) {
        log.debug("REST request to get the content gaps for course {}", courseId);
        courseRepository.findByIdElseThrow(courseId);
        return ResponseEntity.ok(censusService.contentGapsForCourse(courseId));
    }
}
