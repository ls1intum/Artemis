package de.tum.cit.aet.artemis.globalsearch.web;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.GlobalSearchResultDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * REST controller for the unified global search endpoint backed by Weaviate.
 * <p>
 * Invokes {@link SearchableEntityWeaviateService#searchSearchableItems(String, Filter, int)} exactly
 * once per request with a compound access filter built from the user's per-course roles. Per-type
 * access rules are enforced via {@code OR}-of-{@code AND}s disjuncts gated by the row's {@code type}
 * discriminator, which is why a single request can safely cover every indexable entity type.
 */
@Lazy
@Conditional(WeaviateEnabled.class)
@RestController
@RequestMapping("api/")
@Tag(name = "Global Search", description = "Weaviate-based semantic search across all entity types")
public class GlobalSearchResource {

    private static final Logger log = LoggerFactory.getLogger(GlobalSearchResource.class);

    private static final Set<String> VALID_TYPES = Set.of(SearchableEntitySchema.TypeValues.EXERCISE, SearchableEntitySchema.TypeValues.LECTURE,
            SearchableEntitySchema.TypeValues.LECTURE_UNIT, SearchableEntitySchema.TypeValues.EXAM, SearchableEntitySchema.TypeValues.FAQ,
            SearchableEntitySchema.TypeValues.CHANNEL);

    private final SearchableEntityWeaviateService searchableEntityWeaviateService;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    public GlobalSearchResource(SearchableEntityWeaviateService searchableEntityWeaviateService, CourseRepository courseRepository, UserRepository userRepository,
            AuthorizationCheckService authCheckService) {
        this.searchableEntityWeaviateService = searchableEntityWeaviateService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * GET /api/search : unified global search across all indexable entity types.
     * <p>
     * Executes exactly one Weaviate request per invocation. Per-type access rules are encoded as a
     * compound {@code OR}-of-{@code AND}s filter so access control cannot leak across types.
     *
     * @param query    the search query (may be empty to browse recent items)
     * @param types    optional comma-separated list of types to include ({@code exercise,lecture,lecture_unit,exam,faq,channel}
     *                     or {@code all}; default {@code all})
     * @param courseId optional course id to scope the search to a single course
     * @param limit    maximum number of results (default 10, max 25)
     * @return status 200 with a list of unified search results; empty list if the user has no access
     *         or all requested types are invalid
     */
    @GetMapping("search")
    @EnforceAtLeastStudent
    @Operation(summary = "Perform a unified semantic search across entity types", description = """
            Searches across multiple entity types (exercises, lectures, lecture units, exams, FAQs, channels)
            with a consistent response format. When courseId is not specified, the search is performed
            globally across all courses the authenticated user has access to. Per-type access rules are
            enforced server-side via compound Weaviate filters.""")
    @ApiResponse(responseCode = "200", description = "Search results matching the query")
    @ApiResponse(responseCode = "400", description = "Unsupported entity type requested")
    public ResponseEntity<List<GlobalSearchResultDTO>> globalSearch(@RequestParam("q") @Parameter(description = "Search query; can be empty to retrieve recent items") String query,
            @RequestParam(value = "types", required = false) @Parameter(description = "Comma-separated entity type filter (exercise, lecture, lecture_unit, exam, faq, channel) or 'all'; default 'all'") String types,
            @RequestParam(value = "courseId", required = false) @Parameter(description = "Course ID to restrict the search to a single course") Long courseId,
            @RequestParam(value = "limit", defaultValue = "10") @Parameter(description = "Maximum number of results (1–25, default 10)") int limit) {
        log.debug("REST request for global search with query: '{}', types: {}, courseId: {}, limit: {}", query, types, courseId, limit);

        Set<String> requestedTypes = parseTypes(types);
        if (requestedTypes == null) {
            return ResponseEntity.badRequest().build();
        }

        int effectiveLimit = Math.clamp(limit, 1, 25);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        FilterBuildResult filterResult = buildSearchableItemFilter(user, courseId, requestedTypes);
        if (!filterResult.hasAccess()) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> rawResults = searchableEntityWeaviateService.searchSearchableItems(query, filterResult.filter(), effectiveLimit);

        Map<Long, String> courseNameById = resolveCourseNames(rawResults);
        List<GlobalSearchResultDTO> resultDTOs = new ArrayList<>();
        for (Map<String, Object> properties : rawResults) {
            GlobalSearchResultDTO dto = GlobalSearchResultDTO.fromSearchableItemProperties(properties, courseNameById);
            if (dto != null) {
                resultDTOs.add(dto);
            }
        }
        return ResponseEntity.ok(resultDTOs);
    }

    /**
     * Parses the raw {@code types} query parameter into a set of valid type discriminators.
     *
     * @param types the raw query parameter value
     * @return the parsed set, or {@code null} if any requested token is invalid
     */
    private static Set<String> parseTypes(String types) {
        if (types == null || types.isBlank() || "all".equalsIgnoreCase(types.trim())) {
            return new LinkedHashSet<>(VALID_TYPES);
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : types.split(",")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            if (!VALID_TYPES.contains(normalized)) {
                return null;
            }
            result.add(normalized);
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * Resolves one row per distinct {@code course_id} appearing in the Weaviate results so course
     * titles can be injected into the response DTO without denormalizing course names into Weaviate.
     */
    private Map<Long, String> resolveCourseNames(List<Map<String, Object>> rawResults) {
        Set<Long> courseIds = new HashSet<>();
        for (Map<String, Object> properties : rawResults) {
            Object raw = properties.get(SearchableEntitySchema.Properties.COURSE_ID);
            if (raw instanceof Number number) {
                courseIds.add(number.longValue());
            }
        }
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> courseNames = new HashMap<>();
        courseRepository.findAllById(courseIds).forEach(course -> courseNames.put(course.getId(), course.getTitle()));
        return courseNames;
    }

    /**
     * Compound filter build result. {@code filter} may be {@code null} (admin access: no filter),
     * and {@code hasAccess} may be {@code false} (user has no accessible courses → short-circuit empty).
     */
    private record FilterBuildResult(Filter filter, boolean hasAccess) {
    }

    /**
     * Builds the compound per-type filter for the current request. Returns:
     * <ul>
     * <li>{@code hasAccess = false} if the user has no accessible courses (caller short-circuits with empty list)</li>
     * <li>{@code filter = null} for admins (Weaviate query runs with no filter)</li>
     * <li>an {@code OR}-of-{@code AND}s filter with one disjunct per requested type otherwise</li>
     * </ul>
     */
    private FilterBuildResult buildSearchableItemFilter(User user, Long courseId, Set<String> requestedTypes) {
        if (authCheckService.isAdmin(user) && courseId == null) {
            return new FilterBuildResult(null, true);
        }

        List<Course> accessibleCourses;
        if (courseId != null) {
            Course course = courseRepository.findByIdElseThrow(courseId);
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
            accessibleCourses = List.of(course);
        }
        else {
            accessibleCourses = courseRepository.findAllAccessibleCoursesForUser(user.getGroups(), false);
            if (accessibleCourses.isEmpty()) {
                return new FilterBuildResult(null, false);
            }
        }

        CourseRoleSets roleSets = groupCoursesByRole(user, accessibleCourses);

        List<Filter> disjuncts = new ArrayList<>();
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.EXERCISE)) {
            Filter disjunct = buildExerciseDisjunct(roleSets);
            if (disjunct != null) {
                disjuncts.add(disjunct);
            }
        }
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.LECTURE)) {
            Filter disjunct = buildLectureDisjunct(roleSets);
            if (disjunct != null) {
                disjuncts.add(disjunct);
            }
        }
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.LECTURE_UNIT)) {
            Filter disjunct = buildLectureUnitDisjunct(roleSets);
            if (disjunct != null) {
                disjuncts.add(disjunct);
            }
        }
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.EXAM)) {
            Filter disjunct = buildExamDisjunct(roleSets);
            if (disjunct != null) {
                disjuncts.add(disjunct);
            }
        }
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.FAQ)) {
            Filter disjunct = buildFaqDisjunct(roleSets);
            if (disjunct != null) {
                disjuncts.add(disjunct);
            }
        }
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.CHANNEL)) {
            Filter disjunct = buildChannelDisjunct(roleSets);
            if (disjunct != null) {
                disjuncts.add(disjunct);
            }
        }

        if (disjuncts.isEmpty()) {
            return new FilterBuildResult(null, false);
        }
        if (disjuncts.size() == 1) {
            return new FilterBuildResult(disjuncts.getFirst(), true);
        }
        return new FilterBuildResult(Filter.or(disjuncts.toArray(new Filter[0])), true);
    }

    /**
     * Per-course role classification, computed once per request and reused across type disjuncts.
     */
    private record CourseRoleSets(List<Long> editorCourseIds, List<Long> taCourseIds, List<Long> studentCourseIds, List<Long> staffCourseIds, List<Long> allAccessibleCourseIds) {
    }

    private CourseRoleSets groupCoursesByRole(User user, List<Course> courses) {
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
        List<Long> staffCourseIds = new ArrayList<>(editorCourseIds.size() + taCourseIds.size());
        staffCourseIds.addAll(editorCourseIds);
        staffCourseIds.addAll(taCourseIds);

        List<Long> allAccessibleCourseIds = new ArrayList<>(courses.size());
        for (Course course : courses) {
            allAccessibleCourseIds.add(course.getId());
        }
        return new CourseRoleSets(editorCourseIds, taCourseIds, studentCourseIds, staffCourseIds, allAccessibleCourseIds);
    }

    private Role getUserRoleInCourse(User user, Course course) {
        if (authCheckService.isAtLeastEditorInCourse(course, user)) {
            return Role.EDITOR;
        }
        if (authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return Role.TEACHING_ASSISTANT;
        }
        return Role.STUDENT;
    }

    // -- Exercise disjunct --

    private Filter buildExerciseDisjunct(CourseRoleSets roleSets) {
        List<Filter> subBranches = new ArrayList<>();
        if (!roleSets.editorCourseIds().isEmpty()) {
            subBranches.add(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.editorCourseIds()));
        }
        if (!roleSets.taCourseIds().isEmpty()) {
            subBranches.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.taCourseIds()), exerciseAccessFilter(Role.TEACHING_ASSISTANT)));
        }
        if (!roleSets.studentCourseIds().isEmpty()) {
            subBranches.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.studentCourseIds()), exerciseAccessFilter(Role.STUDENT)));
        }
        Filter combined = combineOr(subBranches);
        return combined == null ? null : Filter.and(typeEquals(SearchableEntitySchema.TypeValues.EXERCISE), combined);
    }

    private static Filter exerciseAccessFilter(Role role) {
        OffsetDateTime now = OffsetDateTime.now();
        if (role == Role.TEACHING_ASSISTANT) {
            // TAs: regular exercises always visible; exam exercises only after exam end
            return Filter.or(Filter.property(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE).eq(false), Filter
                    .and(Filter.property(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE).eq(true), Filter.property(SearchableEntitySchema.Properties.EXAM_END_DATE).lte(now)));
        }
        // Students: released regular exercises OR exam exercises after exam start
        Filter releasedRegularExercises = Filter.and(Filter.property(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE).eq(false),
                Filter.or(Filter.property(SearchableEntitySchema.Properties.RELEASE_DATE).lte(now), Filter.property(SearchableEntitySchema.Properties.RELEASE_DATE).isNull()));
        Filter startedExamExercises = Filter.and(Filter.property(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE).eq(true),
                Filter.property(SearchableEntitySchema.Properties.EXAM_START_DATE).lte(now));
        return Filter.or(releasedRegularExercises, startedExamExercises);
    }

    // -- Lecture disjunct --

    private Filter buildLectureDisjunct(CourseRoleSets roleSets) {
        if (roleSets.allAccessibleCourseIds().isEmpty()) {
            return null;
        }
        return Filter.and(typeEquals(SearchableEntitySchema.TypeValues.LECTURE), courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.allAccessibleCourseIds()));
    }

    // -- LectureUnit disjunct --

    private Filter buildLectureUnitDisjunct(CourseRoleSets roleSets) {
        List<Filter> subBranches = new ArrayList<>();
        if (!roleSets.staffCourseIds().isEmpty()) {
            subBranches.add(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.staffCourseIds()));
        }
        if (!roleSets.studentCourseIds().isEmpty()) {
            subBranches.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.studentCourseIds()),
                    Filter.property(SearchableEntitySchema.Properties.UNIT_VISIBLE).eq(true)));
        }
        Filter combined = combineOr(subBranches);
        return combined == null ? null : Filter.and(typeEquals(SearchableEntitySchema.TypeValues.LECTURE_UNIT), combined);
    }

    // -- Exam disjunct --

    private Filter buildExamDisjunct(CourseRoleSets roleSets) {
        OffsetDateTime now = OffsetDateTime.now();
        List<Filter> subBranches = new ArrayList<>();
        if (!roleSets.staffCourseIds().isEmpty()) {
            subBranches.add(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.staffCourseIds()));
        }
        if (!roleSets.studentCourseIds().isEmpty()) {
            subBranches.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.studentCourseIds()),
                    Filter.property(SearchableEntitySchema.Properties.VISIBLE_DATE).lte(now)));
        }
        Filter combined = combineOr(subBranches);
        return combined == null ? null : Filter.and(typeEquals(SearchableEntitySchema.TypeValues.EXAM), combined);
    }

    // -- FAQ disjunct --

    private Filter buildFaqDisjunct(CourseRoleSets roleSets) {
        List<Filter> subBranches = new ArrayList<>();
        if (!roleSets.staffCourseIds().isEmpty()) {
            subBranches.add(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.staffCourseIds()));
        }
        if (!roleSets.studentCourseIds().isEmpty()) {
            subBranches.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.studentCourseIds()),
                    Filter.property(SearchableEntitySchema.Properties.FAQ_STATE).eq("ACCEPTED")));
        }
        Filter combined = combineOr(subBranches);
        return combined == null ? null : Filter.and(typeEquals(SearchableEntitySchema.TypeValues.FAQ), combined);
    }

    // -- Channel disjunct --

    private Filter buildChannelDisjunct(CourseRoleSets roleSets) {
        if (roleSets.allAccessibleCourseIds().isEmpty()) {
            return null;
        }
        Filter courseScope = courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.allAccessibleCourseIds());
        Filter visibility = Filter.or(Filter.property(SearchableEntitySchema.Properties.CHANNEL_IS_COURSE_WIDE).eq(true),
                Filter.property(SearchableEntitySchema.Properties.CHANNEL_IS_PUBLIC).eq(true));
        return Filter.and(typeEquals(SearchableEntitySchema.TypeValues.CHANNEL), courseScope, visibility);
    }

    // -- Shared helpers --

    private static Filter typeEquals(String type) {
        return Filter.property(SearchableEntitySchema.Properties.TYPE).eq(type);
    }

    private static Filter courseIdIn(String property, List<Long> courseIds) {
        if (courseIds.size() == 1) {
            return Filter.property(property).eq(courseIds.getFirst());
        }
        return Filter.property(property).containsAny(courseIds.toArray(new Long[0]));
    }

    private static Filter combineOr(Collection<Filter> filters) {
        List<Filter> nonNull = new ArrayList<>(filters.size());
        for (Filter f : filters) {
            if (f != null) {
                nonNull.add(f);
            }
        }
        if (nonNull.isEmpty()) {
            return null;
        }
        if (nonNull.size() == 1) {
            return nonNull.getFirst();
        }
        return Filter.or(nonNull.toArray(new Filter[0]));
    }

}
