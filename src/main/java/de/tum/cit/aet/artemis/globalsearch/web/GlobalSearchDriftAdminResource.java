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
import de.tum.cit.aet.artemis.globalsearch.service.CourseIndexCensusService;
import de.tum.cit.aet.artemis.globalsearch.service.CourseIndexCensusService.CourseCensus;
import de.tum.cit.aet.artemis.globalsearch.service.CourseIndexDriftService;
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

    private final CourseRepository courseRepository;

    public GlobalSearchDriftAdminResource(CourseIndexDriftService driftService, CourseIndexCensusService censusService, CourseRepository courseRepository) {
        this.driftService = driftService;
        this.censusService = censusService;
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
}
