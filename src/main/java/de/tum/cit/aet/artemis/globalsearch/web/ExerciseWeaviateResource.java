package de.tum.cit.aet.artemis.globalsearch.web;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.ExerciseSchema;
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
     * Access control:
     * - Students: only see non-exam exercises with a release date in the past
     * - Tutors/TAs: see all non-exam exercises; see exam exercises only after the exam has ended
     * - Editors/Instructors/Admins: see all exercises
     *
     * @param courseId the ID of the course to fetch exercises for
     * @return the ResponseEntity with status 200 (OK) and the list of programming exercises in body
     */
    @GetMapping("courses/{courseId}/programming-exercises/weaviate")
    @EnforceAtLeastStudent
    public ResponseEntity<List<GlobalSearchResultDTO>> getProgrammingExercisesFromWeaviate(@PathVariable Long courseId) {
        log.debug("REST request to get programming exercises from Weaviate for course {}", courseId);

        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        boolean isAtLeastTutor = authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
        var exerciseProperties = exerciseWeaviateService.fetchProgrammingExercisesForCourse(courseId, isAtLeastTutor);

        Map<Long, Course> courseMap = Map.of(courseId, course);
        var filteredResults = filterResultsByAccessRights(exerciseProperties, user, courseMap);
        var exercises = filteredResults.stream().map(GlobalSearchResultDTO::fromExerciseProperties).toList();

        return ResponseEntity.ok(exercises);
    }

    /**
     * GET /search?q=:query&type=:type&courseId=:courseId&limit=:limit&sortBy=:sortBy : perform unified semantic search across entity types.
     * <p>
     * This endpoint provides a unified search interface that can search across multiple entity types
     * (exercises, pages, features, courses, etc.) with a consistent response format.
     * Currently supports exercises only, but designed to be extensible for other types.
     * <p>
     * When courseId is not specified, the search is performed globally across all courses
     * the authenticated user has access to (as a student, TA, editor, or instructor).
     * Results are filtered per course based on the user's role in each course.
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
            User user = userRepository.getUserWithGroupsAndAuthorities();
            List<Map<String, Object>> searchResults;
            Map<Long, Course> courseMap;

            if (courseId != null) {
                // Course-specific search: validate access and search within that course
                Course course = courseRepository.findByIdElseThrow(courseId);
                authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
                courseMap = Map.of(courseId, course);

                if (isEmptyQuery) {
                    searchResults = exerciseWeaviateService.fetchRecentExercises(courseId, effectiveLimit, sortBy);
                }
                else {
                    searchResults = exerciseWeaviateService.searchExercises(query, courseId, effectiveLimit);
                }
            }
            else {
                // Global search: get all courses accessible to the user and search across them
                boolean isAdmin = authCheckService.isAdmin(user);

                if (isAdmin) {
                    // Admins see everything - no filtering needed
                    if (isEmptyQuery) {
                        searchResults = exerciseWeaviateService.fetchRecentExercisesInCourses(null, effectiveLimit, sortBy);
                    }
                    else {
                        searchResults = exerciseWeaviateService.searchExercisesInCourses(query, null, effectiveLimit);
                    }

                    var resultDTOs = searchResults.stream().map(GlobalSearchResultDTO::fromExerciseProperties).toList();
                    return ResponseEntity.ok(resultDTOs);
                }

                // Regular users: get courses based on group memberships
                List<Course> accessibleCourses = courseRepository.findAllAccessibleCoursesForUser(user.getGroups(), false);

                if (accessibleCourses.isEmpty()) {
                    return ResponseEntity.ok(List.of());
                }

                courseMap = accessibleCourses.stream().collect(Collectors.toMap(Course::getId, Function.identity()));
                Set<Long> accessibleCourseIds = courseMap.keySet();

                if (isEmptyQuery) {
                    searchResults = exerciseWeaviateService.fetchRecentExercisesInCourses(accessibleCourseIds, effectiveLimit, sortBy);
                }
                else {
                    searchResults = exerciseWeaviateService.searchExercisesInCourses(query, accessibleCourseIds, effectiveLimit);
                }
            }

            var filteredResults = filterResultsByAccessRights(searchResults, user, courseMap);
            var resultDTOs = filteredResults.stream().map(GlobalSearchResultDTO::fromExerciseProperties).toList();
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

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Map<Long, Course> courseMap;

        if (courseId != null) {
            Course course = courseRepository.findByIdElseThrow(courseId);
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
            courseMap = Map.of(courseId, course);
        }
        else {
            // Global search: restrict to accessible courses
            boolean isAdmin = authCheckService.isAdmin(user);
            if (isAdmin) {
                var searchResults = exerciseWeaviateService.searchExercises(query, null, effectiveLimit);
                var resultDTOs = searchResults.stream().map(GlobalSearchResultDTO::fromExerciseProperties).toList();
                return ResponseEntity.ok(resultDTOs);
            }

            List<Course> accessibleCourses = courseRepository.findAllAccessibleCoursesForUser(user.getGroups(), false);
            if (accessibleCourses.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }

            courseMap = accessibleCourses.stream().collect(Collectors.toMap(Course::getId, Function.identity()));
        }

        var searchResults = exerciseWeaviateService.searchExercises(query, courseId, effectiveLimit);
        var filteredResults = filterResultsByAccessRights(searchResults, user, courseMap);
        var resultDTOs = filteredResults.stream().map(GlobalSearchResultDTO::fromExerciseProperties).toList();
        return ResponseEntity.ok(resultDTOs);
    }

    /**
     * Filters search results based on the user's access rights per course.
     * <p>
     * Access rules:
     * <ul>
     * <li>Editors, instructors, and admins: see all exercises including exam exercises at any time</li>
     * <li>Tutors/TAs: see all regular exercises; see exam exercises only after the exam has ended</li>
     * <li>Students: cannot see exam exercises; can only see exercises with a release date in the past (or no release date)</li>
     * </ul>
     *
     * @param results   the raw search results from Weaviate
     * @param user      the authenticated user with groups loaded
     * @param courseMap map of course ID to Course object for role lookup
     * @return filtered results the user is allowed to see
     */
    private List<Map<String, Object>> filterResultsByAccessRights(List<Map<String, Object>> results, User user, Map<Long, Course> courseMap) {
        ZonedDateTime now = ZonedDateTime.now();

        return results.stream().filter(result -> {
            Object courseIdObj = result.get(ExerciseSchema.Properties.COURSE_ID);
            if (courseIdObj == null) {
                return false;
            }

            long resultCourseId = ((Number) courseIdObj).longValue();
            Course course = courseMap.get(resultCourseId);
            if (course == null) {
                return false;
            }

            // Editors, instructors, and admins see everything
            if (authCheckService.isAtLeastEditorInCourse(course, user)) {
                return true;
            }

            Boolean isExamExercise = (Boolean) result.get(ExerciseSchema.Properties.IS_EXAM_EXERCISE);
            boolean isAtLeastTA = authCheckService.isAtLeastTeachingAssistantInCourse(course, user);

            if (Boolean.TRUE.equals(isExamExercise)) {
                if (isAtLeastTA) {
                    // TAs can see exam exercises only after the exam has ended
                    ZonedDateTime examEndDate = parseDate(result.get(ExerciseSchema.Properties.EXAM_END_DATE));
                    if (examEndDate == null) {
                        return false;
                    }
                    return examEndDate.isBefore(now);
                }
                // Students never see exam exercises
                return false;
            }

            // Regular (non-exam) exercise
            if (isAtLeastTA) {
                return true;
            }

            // Students: only see exercises with release date in the past
            ZonedDateTime releaseDate = parseDate(result.get(ExerciseSchema.Properties.RELEASE_DATE));
            if (releaseDate == null) {
                return true;
            }
            return releaseDate.isBefore(now);
        }).toList();
    }

    /**
     * Parses a date value from Weaviate properties, handling both OffsetDateTime and String formats.
     *
     * @param value the date value from Weaviate (may be OffsetDateTime, String, or null)
     * @return the parsed ZonedDateTime, or null if the value is null or unparseable
     */
    private static ZonedDateTime parseDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toZonedDateTime();
        }
        if (value instanceof String dateStr) {
            return ZonedDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        return null;
    }
}
