package de.tum.cit.aet.artemis.globalsearch.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.dto.ProgrammingExerciseWeaviateDTO;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.dto.ExerciseSearchResultDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.GlobalSearchResultDTO;
import de.tum.cit.aet.artemis.globalsearch.service.ExerciseWeaviateService;

/**
 * REST controller for Weaviate-based exercise search operations.
 */
@Lazy
@Conditional(WeaviateEnabled.class)
@RestController
@RequestMapping("api/")
public class ExerciseWeaviateResource {

    private static final Logger log = LoggerFactory.getLogger(ExerciseWeaviateResource.class);

    private final ExerciseWeaviateService exerciseWeaviateService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    private final String serverUrl;

    public ExerciseWeaviateResource(ExerciseWeaviateService exerciseWeaviateService, CourseRepository courseRepository, AuthorizationCheckService authCheckService,
            @Value("${server.url}") String serverUrl) {
        this.exerciseWeaviateService = exerciseWeaviateService;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
        this.serverUrl = serverUrl;
    }

    /**
     * GET /courses/:courseId/programming-exercises/weaviate : get programming exercises for a course from Weaviate.
     * <p>
     * Students will only see exercises with a release date in the past.
     * Tutors and above will see all exercises.
     *
     * @param courseId the ID of the course to fetch exercises for
     * @return the ResponseEntity with status 200 (OK) and the list of programming exercises in body
     */
    @GetMapping("courses/{courseId}/programming-exercises/weaviate")
    @EnforceAtLeastStudent
    public ResponseEntity<List<ProgrammingExerciseWeaviateDTO>> getProgrammingExercisesFromWeaviate(@PathVariable Long courseId) {
        log.debug("REST request to get programming exercises from Weaviate for course {}", courseId);

        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);

        // Check if user is at least a tutor to determine filtering
        boolean isAtLeastTutor = authCheckService.isAtLeastTeachingAssistantInCourse(courseId);

        var exerciseProperties = exerciseWeaviateService.fetchProgrammingExercisesForCourse(courseId, isAtLeastTutor);
        var exercises = exerciseProperties.stream().map(properties -> ProgrammingExerciseWeaviateDTO.fromWeaviateProperties(properties, serverUrl)).toList();

        return ResponseEntity.ok(exercises);
    }

    /**
     * GET /search?q=:query&type=:type&courseId=:courseId&limit=:limit : perform unified semantic search across entity types.
     * <p>
     * This endpoint provides a unified search interface that can search across multiple entity types
     * (exercises, pages, features, courses, etc.) with a consistent response format.
     * Currently supports exercises only, but designed to be extensible for other types.
     *
     * @param query    the search query
     * @param type     optional entity type to filter by (e.g., "exercise", "page", "course")
     * @param courseId optional course ID to filter by
     * @param limit    maximum number of results (default: 10, max: 100)
     * @return the ResponseEntity with status 200 (OK) and the list of unified search results in body
     */
    @GetMapping("search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<GlobalSearchResultDTO>> globalSearch(@RequestParam("q") String query, @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "courseId", required = false) Long courseId, @RequestParam(value = "limit", defaultValue = "10") int limit) {

        log.debug("REST request for global search with query: '{}', type: {}, courseId: {}, limit: {}", query, type, courseId, limit);

        if (query.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Apply limit bounds: minimum 1, maximum 100
        int effectiveLimit = Math.min(Math.max(limit, 1), 100);

        // Validate course access if courseId is specified
        if (courseId != null) {
            Course course = courseRepository.findByIdElseThrow(courseId);
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        }

        // For now, only support exercise search (type filter is optional for future extensibility)
        if (type == null || "exercise".equals(type)) {
            var searchResults = exerciseWeaviateService.searchExercises(query, courseId, effectiveLimit);
            var resultDTOs = searchResults.stream().map(GlobalSearchResultDTO::fromExerciseProperties).toList();
            return ResponseEntity.ok(resultDTOs);
        }

        // Return empty list for unsupported types (ready to add more types in the future)
        return ResponseEntity.ok(List.of());
    }

    /**
     * GET /exercises/search?q=:query&courseId=:courseId&limit=:limit : perform semantic search on exercises.
     *
     * @param query    the search query
     * @param courseId optional course ID to filter by
     * @param limit    maximum number of results (default: 10, max: 100)
     * @return the ResponseEntity with status 200 (OK) and the list of exercise search results in body
     */
    @GetMapping("exercises/search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<ExerciseSearchResultDTO>> searchExercises(@RequestParam("q") String query, @RequestParam(value = "courseId", required = false) Long courseId,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {

        log.debug("REST request to search exercises with query: '{}', courseId: {}, limit: {}", query, courseId, limit);

        if (query.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Apply limit bounds: minimum 1, maximum 100
        int effectiveLimit = Math.min(Math.max(limit, 1), 100);

        // Validate course access if courseId is specified
        if (courseId != null) {
            Course course = courseRepository.findByIdElseThrow(courseId);
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        }

        var searchResults = exerciseWeaviateService.searchExercises(query, courseId, effectiveLimit);
        var resultDTOs = searchResults.stream().map(ExerciseSearchResultDTO::fromWeaviateProperties).toList();
        return ResponseEntity.ok(resultDTOs);
    }
}
