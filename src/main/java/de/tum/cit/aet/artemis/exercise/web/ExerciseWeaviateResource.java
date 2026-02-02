package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
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
import de.tum.cit.aet.artemis.exercise.service.ExerciseWeaviateService;

/**
 * REST controller for Weaviate-based exercise search operations.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/")
public class ExerciseWeaviateResource {

    private static final Logger log = LoggerFactory.getLogger(ExerciseWeaviateResource.class);

    private final ExerciseWeaviateService exerciseWeaviateService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    public ExerciseWeaviateResource(ExerciseWeaviateService exerciseWeaviateService, CourseRepository courseRepository, AuthorizationCheckService authCheckService) {
        this.exerciseWeaviateService = exerciseWeaviateService;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
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
        var exercises = exerciseProperties.stream().map(ProgrammingExerciseWeaviateDTO::fromWeaviateProperties).toList();

        return ResponseEntity.ok(exercises);
    }

    /**
     * GET /exercises/search?q=:query&courseId=:courseId&limit=:limit : perform semantic search on exercises.
     *
     * @param query    the search query
     * @param courseId optional course ID to filter by
     * @param limit    maximum number of results (default: 10)
     * @return the ResponseEntity with status 200 (OK) and the list of exercise search results in body
     */
    @GetMapping("exercises/search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<Map<String, Object>>> searchExercises(@RequestParam("q") String query, @RequestParam(value = "courseId", required = false) Long courseId,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {

        log.debug("REST request to search exercises with query: '{}', courseId: {}, limit: {}", query, courseId, limit);

        if (query.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Validate course access if courseId is specified
        if (courseId != null) {
            Course course = courseRepository.findByIdElseThrow(courseId);
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        }

        try {
            List<Map<String, Object>> searchResults = exerciseWeaviateService.searchExercises(query, courseId, limit);
            return ResponseEntity.ok(searchResults);
        }
        catch (Exception e) {
            log.error("Failed to search exercises: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }
}
