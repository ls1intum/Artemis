package de.tum.cit.aet.artemis.globalsearch.web;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
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

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    public ExerciseWeaviateResource(ExerciseWeaviateService exerciseWeaviateService, CourseRepository courseRepository, UserRepository userRepository,
            AuthorizationCheckService authCheckService) {
        this.exerciseWeaviateService = exerciseWeaviateService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
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
    public ResponseEntity<List<GlobalSearchResultDTO>> getProgrammingExercisesFromWeaviate(@PathVariable Long courseId) {
        log.debug("REST request to get programming exercises from Weaviate for course {}", courseId);

        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);

        // Check if user is at least a tutor to determine filtering
        boolean isAtLeastTutor = authCheckService.isAtLeastTeachingAssistantInCourse(courseId);

        var exerciseProperties = exerciseWeaviateService.fetchProgrammingExercisesForCourse(courseId, isAtLeastTutor);
        var exercises = exerciseProperties.stream().map(GlobalSearchResultDTO::fromExerciseProperties).toList();

        return ResponseEntity.ok(exercises);
    }

    // TODO we still need to take care of the case that a user has different access rights in different courses (e.g. student in one course, tutor in another course) - we currently
    // assume that the user has the same access rights in all courses, which is not always the case. We need to implement a more fine-grained access control mechanism that checks
    // the user's access rights for each course individually when performing a global search. This will ensure that users only see search results from courses they have access to,
    // and that the results are filtered appropriately based on their role in each course.
    /**
     * GET /search?q=:query&type=:type&courseId=:courseId&limit=:limit&sortBy=:sortBy : perform unified semantic search across entity types.
     * <p>
     * This endpoint provides a unified search interface that can search across multiple entity types
     * (exercises, pages, features, courses, etc.) with a consistent response format.
     * Currently supports exercises only, but designed to be extensible for other types.
     * <p>
     * When courseId is not specified, the search is performed globally across all courses
     * the authenticated user has access to (as a student, TA, editor, or instructor).
     * <p>
     * When query is empty, the endpoint returns recent items (sorted by the sortBy parameter if provided).
     *
     * @param query    the search query (can be empty to get recent items)
     * @param type     optional entity type to filter by (e.g., "exercise", "page", "course")
     * @param courseId optional course ID to filter by (if null, searches across all accessible courses)
     * @param limit    maximum number of results (default: 10, max: 100)
     * @param sortBy   optional sort field (e.g., "dueDate") - only used when query is empty
     * @return the ResponseEntity with status 200 (OK) and the list of unified search results in body
     */
    @GetMapping("search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<GlobalSearchResultDTO>> globalSearch(@RequestParam("q") String query, @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "courseId", required = false) Long courseId, @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "sortBy", required = false) String sortBy) {

        log.debug("REST request for global search with query: '{}', type: {}, courseId: {}, limit: {}, sortBy: {}", query, type, courseId, limit, sortBy);

        boolean isEmptyQuery = query == null || query.trim().isEmpty();

        // Apply limit bounds: minimum 1, maximum 100
        int effectiveLimit = Math.min(Math.max(limit, 1), 100);

        // For now, only support exercise search (type filter is optional for future extensibility)
        if (type == null || "exercise".equals(type)) {
            List<Map<String, Object>> searchResults;

            if (courseId != null) {
                // Course-specific search: validate access and search within that course
                Course course = courseRepository.findByIdElseThrow(courseId);
                authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);

                if (isEmptyQuery) {
                    // For empty queries, fetch recent exercises instead of searching
                    searchResults = exerciseWeaviateService.fetchRecentExercises(courseId, effectiveLimit, sortBy);
                }
                else {
                    searchResults = exerciseWeaviateService.searchExercises(query, courseId, effectiveLimit);
                }
            }
            else {
                // Global search: get all courses accessible to the user and search across them
                User user = userRepository.getUserWithGroupsAndAuthorities();
                boolean isAdmin = authCheckService.isAdmin(user);

                log.debug("Global search - User: {}, isAdmin: {}, authorities: {}", user.getLogin(), isAdmin, user.getAuthorities());

                if (isAdmin) {
                    // Admins have access to all courses - skip course filtering for better performance
                    log.debug("Admin user - searching across all courses without course ID filter");
                    if (isEmptyQuery) {
                        // For empty queries, fetch recent exercises instead of searching
                        searchResults = exerciseWeaviateService.fetchRecentExercisesInCourses(null, effectiveLimit, sortBy);
                    }
                    else {
                        searchResults = exerciseWeaviateService.searchExercisesInCourses(query, null, effectiveLimit);
                    }
                }
                else {
                    // Regular users: get courses based on group memberships
                    List<Course> accessibleCourses = courseRepository.findAllAccessibleCoursesForUser(user.getGroups(), false);
                    log.debug("Regular user - fetched {} accessible courses based on groups: {}", accessibleCourses.size(), user.getGroups());

                    if (accessibleCourses.isEmpty()) {
                        // User has no accessible courses, return empty results
                        log.warn("User {} has no accessible courses for global search", user.getLogin());
                        return ResponseEntity.ok(List.of());
                    }

                    Set<Long> accessibleCourseIds = accessibleCourses.stream().map(Course::getId).collect(Collectors.toSet());
                    log.debug("Searching exercises in {} courses with IDs: {}", accessibleCourseIds.size(), accessibleCourseIds);

                    if (isEmptyQuery) {
                        // For empty queries, fetch recent exercises instead of searching
                        searchResults = exerciseWeaviateService.fetchRecentExercisesInCourses(accessibleCourseIds, effectiveLimit, sortBy);
                    }
                    else {
                        searchResults = exerciseWeaviateService.searchExercisesInCourses(query, accessibleCourseIds, effectiveLimit);
                    }
                }
                log.debug("Found {} search results", searchResults.size());
            }

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
    public ResponseEntity<List<GlobalSearchResultDTO>> searchExercises(@RequestParam("q") String query, @RequestParam(value = "courseId", required = false) Long courseId,
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
        var resultDTOs = searchResults.stream().map(GlobalSearchResultDTO::fromExerciseProperties).toList();
        return ResponseEntity.ok(resultDTOs);
    }
}
