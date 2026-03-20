package de.tum.cit.aet.artemis.globalsearch.web;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
import io.weaviate.client6.v1.api.collections.query.Filter;

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
     * GET /search?q=:query&type=:type&courseId=:courseId&limit=:limit&sortBy=:sortBy : perform unified semantic search across entity types.
     * <p>
     * This endpoint provides a unified search interface that can search across multiple entity types
     * (exercises, pages, features, courses, etc.) with a consistent response format.
     * Currently supports exercises only, but designed to be extensible for other types.
     * <p>
     * When courseId is not specified, the search is performed globally across all courses
     * the authenticated user has access to (as a student, TA, editor, or instructor).
     * Results are filtered per course based on the user's role in each course via Weaviate filters.
     * <p>
     * When query is empty, the endpoint returns recent items.
     *
     * @param query    the search query (can be empty to get recent items)
     * @param type     optional entity type to filter by (e.g., "exercise", "page", "course")
     * @param courseId optional course ID to filter by (if null, searches across all accessible courses)
     * @param limit    maximum number of results (default: 10, max: 100)
     * @return the ResponseEntity with status 200 (OK) and the list of unified search results in body
     */
    @GetMapping("search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<GlobalSearchResultDTO>> globalSearch(@RequestParam("q") String query, @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "courseId", required = false) Long courseId, @RequestParam(value = "limit", defaultValue = "10") int limit) {

        log.debug("REST request for global search with query: '{}', type: {}, courseId: {}, limit: {}", query, type, courseId, limit);

        // Normalize type parameter: trim, lowercase, treat blank as null
        String normalizedType = type != null ? type.trim().toLowerCase(Locale.ROOT) : null;
        if (normalizedType != null && normalizedType.isEmpty()) {
            normalizedType = null;
        }

        if (normalizedType != null && !"exercise".equals(normalizedType)) {
            return ResponseEntity.badRequest().build();
        }

        boolean isEmptyQuery = query == null || query.trim().isEmpty();
        int effectiveLimit = Math.min(Math.max(limit, 1), 100);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        FilterResult filterResult = buildFilterForUser(user, courseId);
        if (!filterResult.hasAccess()) {
            return ResponseEntity.ok(List.of());
        }
        Filter filter = filterResult.filter();

        List<Map<String, Object>> searchResults;
        if (isEmptyQuery) {
            searchResults = exerciseWeaviateService.fetchExercisesWithFilter(filter, effectiveLimit);
        }
        else {
            searchResults = exerciseWeaviateService.searchExercisesWithFilter(query, filter, effectiveLimit);
        }

        var resultDTOs = searchResults.stream().map(GlobalSearchResultDTO::fromExerciseProperties).toList();
        return ResponseEntity.ok(resultDTOs);
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

        int effectiveLimit = Math.min(Math.max(limit, 1), 100);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        FilterResult filterResult = buildFilterForUser(user, courseId);
        if (!filterResult.hasAccess()) {
            return ResponseEntity.ok(List.of());
        }
        Filter filter = filterResult.filter();

        var searchResults = exerciseWeaviateService.searchExercisesWithFilter(query, filter, effectiveLimit);
        var resultDTOs = searchResults.stream().map(GlobalSearchResultDTO::fromExerciseProperties).toList();
        return ResponseEntity.ok(resultDTOs);
    }

    /**
     * Result of building a user-specific Weaviate filter.
     *
     * @param filter    the Weaviate filter to apply ({@code null} means no filter, i.e. admin access)
     * @param hasAccess whether the user has access to any courses; if {@code false}, callers should return an empty result
     */
    private record FilterResult(Filter filter, boolean hasAccess) {
    }

    /**
     * Builds a Weaviate filter scoped to the courses the user may access.
     * <p>
     * Handles three cases:
     * <ol>
     * <li>Single course (courseId != null): verifies student role and returns a course+role filter</li>
     * <li>Admin: no filter needed (returns {@code null} filter with {@code hasAccess = true})</li>
     * <li>Multi-course: groups accessible courses by role and returns a combined filter</li>
     * </ol>
     *
     * @param user     the authenticated user
     * @param courseId optional course ID; if {@code null}, searches across all accessible courses
     * @return a {@link FilterResult} containing the filter and an access flag
     */
    private FilterResult buildFilterForUser(User user, Long courseId) {
        if (courseId != null) {
            Course course = courseRepository.findByIdElseThrow(courseId);
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
            return new FilterResult(buildFilterForSingleCourse(user, course), true);
        }

        if (authCheckService.isAdmin(user)) {
            return new FilterResult(null, true);
        }

        List<Course> accessibleCourses = courseRepository.findAllAccessibleCoursesForUser(user.getGroups(), false);
        if (accessibleCourses.isEmpty()) {
            return new FilterResult(null, false);
        }
        return new FilterResult(buildFilterForMultipleCourses(user, accessibleCourses), true);
    }

    // -- Filter building helpers --

    /**
     * Builds a combined Weaviate filter for a single course, incorporating course ID and role-based access control.
     */
    private Filter buildFilterForSingleCourse(User user, Course course) {
        Filter courseFilter = Filter.property(ExerciseSchema.Properties.COURSE_ID).eq(course.getId());
        Filter accessFilter = buildAccessFilterForRole(getUserRoleInCourse(user, course));
        return accessFilter != null ? Filter.and(courseFilter, accessFilter) : courseFilter;
    }

    /**
     * Builds a combined Weaviate filter for multiple courses, grouping courses by the user's role
     * and applying appropriate access control filters per group.
     * <p>
     * Courses are grouped into three role levels:
     * - Editor+ (editors, instructors): no access restrictions on exercises
     * - TA: see all regular exercises, exam exercises only after the exam has ended
     * - Student: see released regular exercises only, exam exercises only after the exam has started
     * <p>
     * Each group gets a filter of: (course_id IN group_ids) AND (role-based access filter).
     * All groups are OR-ed together.
     */
    private Filter buildFilterForMultipleCourses(User user, List<Course> courses) {
        List<Long> editorCourseIds = new ArrayList<>();
        List<Long> taCourseIds = new ArrayList<>();
        List<Long> studentCourseIds = new ArrayList<>();

        for (Course course : courses) {
            Role role = getUserRoleInCourse(user, course);
            switch (role) {
                case EDITOR, INSTRUCTOR -> editorCourseIds.add(course.getId());
                case TEACHING_ASSISTANT -> taCourseIds.add(course.getId());
                default -> studentCourseIds.add(course.getId());
            }
        }

        List<Filter> groupFilters = new ArrayList<>();

        if (!editorCourseIds.isEmpty()) {
            groupFilters.add(buildCourseIdOrFilter(editorCourseIds));
        }
        if (!taCourseIds.isEmpty()) {
            Filter courseFilter = buildCourseIdOrFilter(taCourseIds);
            Filter accessFilter = buildAccessFilterForRole(Role.TEACHING_ASSISTANT);
            groupFilters.add(accessFilter != null ? Filter.and(courseFilter, accessFilter) : courseFilter);
        }
        if (!studentCourseIds.isEmpty()) {
            Filter courseFilter = buildCourseIdOrFilter(studentCourseIds);
            Filter accessFilter = buildAccessFilterForRole(Role.STUDENT);
            groupFilters.add(accessFilter != null ? Filter.and(courseFilter, accessFilter) : courseFilter);
        }

        if (groupFilters.isEmpty()) {
            return null;
        }
        if (groupFilters.size() == 1) {
            return groupFilters.getFirst();
        }
        return Filter.or(groupFilters.toArray(new Filter[0]));
    }

    /**
     * Builds a Weaviate OR filter for a list of course IDs: (course_id = id1 OR course_id = id2 OR ...).
     */
    private static Filter buildCourseIdOrFilter(List<Long> courseIds) {
        Filter filter = Filter.property(ExerciseSchema.Properties.COURSE_ID).eq(courseIds.getFirst());
        for (int i = 1; i < courseIds.size(); i++) {
            filter = Filter.or(filter, Filter.property(ExerciseSchema.Properties.COURSE_ID).eq(courseIds.get(i)));
        }
        return filter;
    }

    /**
     * Determines the user's highest role in the given course.
     *
     * @return INSTRUCTOR if at least editor, TEACHING_ASSISTANT if TA, STUDENT otherwise
     */
    private Role getUserRoleInCourse(User user, Course course) {
        if (authCheckService.isAtLeastEditorInCourse(course, user)) {
            return Role.EDITOR;
        }
        if (authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return Role.TEACHING_ASSISTANT;
        }
        return Role.STUDENT;
    }

    /**
     * Builds a Weaviate filter for role-based exercise access control.
     * <p>
     * Access rules:
     * <ul>
     * <li>Editors/Instructors/Admins: no filter needed (see everything)</li>
     * <li>TAs: see all regular exercises; exam exercises only after the exam has ended</li>
     * <li>Students: regular exercises only if released (release_date &lt;= now or null);
     * exam exercises only after the exam has started (exam_start_date &lt;= now)</li>
     * </ul>
     *
     * @param role the user's role
     * @return the access control filter, or null if no filtering is needed
     */
    private static Filter buildAccessFilterForRole(Role role) {
        if (role == Role.EDITOR || role == Role.INSTRUCTOR) {
            return null;
        }

        OffsetDateTime now = OffsetDateTime.now();

        if (role == Role.TEACHING_ASSISTANT) {
            // TAs: regular exercises always visible; exam exercises only after exam end
            return Filter.or(Filter.property(ExerciseSchema.Properties.IS_EXAM_EXERCISE).eq(false),
                    Filter.and(Filter.property(ExerciseSchema.Properties.IS_EXAM_EXERCISE).eq(true), Filter.property(ExerciseSchema.Properties.EXAM_END_DATE).lte(now)));
        }

        // Students: released regular exercises OR exam exercises after exam start
        Filter releasedRegularExercises = Filter.and(Filter.property(ExerciseSchema.Properties.IS_EXAM_EXERCISE).eq(false),
                Filter.or(Filter.property(ExerciseSchema.Properties.RELEASE_DATE).lte(now), Filter.property(ExerciseSchema.Properties.RELEASE_DATE).isNull()));

        Filter startedExamExercises = Filter.and(Filter.property(ExerciseSchema.Properties.IS_EXAM_EXERCISE).eq(true),
                Filter.property(ExerciseSchema.Properties.EXAM_START_DATE).lte(now));

        return Filter.or(releasedRegularExercises, startedExamExercises);
    }
}
