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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.api.StudentExamApi;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
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
 * Invokes {@link SearchableEntityWeaviateService#searchSearchableEntities(String, Filter, int)} exactly
 * once per request with a compound access filter built from the user's per-course roles. Per-type
 * access rules are enforced via {@code OR}-of-{@code AND}s disjuncts gated by the row's {@code type}
 * discriminator, which is why a single request can safely cover every indexable entity type.
 */
@Lazy
@Conditional(WeaviateEnabled.class)
@RestController
@RequestMapping("api/")
@Tag(name = "Global Search Resource", description = "Weaviate-based semantic search across courses, exercises, lectures, lecture units, exams, FAQs, and public communication channels including their messages and replies")
public class GlobalSearchResource {

    private static final Logger log = LoggerFactory.getLogger(GlobalSearchResource.class);

    private static final Set<String> VALID_TYPES = Set.of(SearchableEntitySchema.TypeValues.EXERCISE, SearchableEntitySchema.TypeValues.LECTURE,
            SearchableEntitySchema.TypeValues.LECTURE_UNIT, SearchableEntitySchema.TypeValues.EXAM, SearchableEntitySchema.TypeValues.FAQ,
            SearchableEntitySchema.TypeValues.CHANNEL, SearchableEntitySchema.TypeValues.COURSE, SearchableEntitySchema.TypeValues.POST,
            SearchableEntitySchema.TypeValues.ANSWER_POST);

    private final SearchableEntityWeaviateService searchableEntityWeaviateService;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseRepository exerciseRepository;

    private final ChannelRepository channelRepository;

    private final StudentExamApi studentExamRepository;

    public GlobalSearchResource(SearchableEntityWeaviateService searchableEntityWeaviateService, CourseRepository courseRepository, UserRepository userRepository,
            AuthorizationCheckService authCheckService, ExerciseRepository exerciseRepository, ChannelRepository channelRepository, StudentExamApi studentExamRepository) {
        this.searchableEntityWeaviateService = searchableEntityWeaviateService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.exerciseRepository = exerciseRepository;
        this.channelRepository = channelRepository;
        this.studentExamRepository = studentExamRepository;
    }

    /**
     * GET /api/search : unified global search across all indexable entity types.
     * <p>
     * Executes exactly one Weaviate request per invocation. Per-type access rules are encoded as a
     * compound {@code OR}-of-{@code AND}s filter so access control cannot leak across types.
     *
     * @param query    the search query (may be empty to browse recent items)
     * @param types    optional comma-separated list of types to include ({@code exercise,lecture,lecture_unit,exam,faq,channel,course,post,answer_post}
     *                     or {@code all}; default {@code all})
     * @param courseId optional course id to scope the search to a single course
     * @param limit    maximum number of results (default 10, max 25)
     * @return status 200 with a list of unified search results; empty list if the user has no access
     *         or all requested types are invalid
     */
    @GetMapping("search")
    @EnforceAtLeastStudent
    @Operation(summary = "Perform a unified semantic search across entity types", description = """
            Searches across multiple entity types (exercises, lectures, lecture units, exams, FAQs, channels, courses, posts, answer posts)
            with a consistent response format. When courseId is not specified, the search is performed
            globally across all courses the authenticated user has access to. Per-type access rules are
            enforced server-side via compound Weaviate filters.""")
    @ApiResponse(responseCode = "200", description = "Search results matching the query")
    @ApiResponse(responseCode = "400", description = "Unsupported entity type requested")
    public ResponseEntity<List<GlobalSearchResultDTO>> globalSearch(@RequestParam("q") @Parameter(description = "Search query; can be empty to retrieve recent items") String query,
            @RequestParam(value = "types", required = false) @Parameter(description = "Comma-separated entity type filter (exercise, lecture, lecture_unit, exam, faq, channel, course, post, answer_post) or 'all'; default 'all'") String types,
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

        List<Map<String, Object>> rawResults = searchableEntityWeaviateService.searchSearchableEntities(query, filterResult.filter(), effectiveLimit);

        Map<Long, Course> coursesById;
        Set<Long> staffCourseIds;
        Set<Long> editorCourseIds;
        if (filterResult.accessibleCoursesById() != null) {
            // Non-admin (or admin with courseId): reuse courses already fetched during filter building
            coursesById = filterResult.accessibleCoursesById();
            staffCourseIds = filterResult.staffCourseIds();
            editorCourseIds = filterResult.editorCourseIds();
        }
        else {
            // Admin global search: result courses unknown until after query, fetch now
            coursesById = resolveCoursesById(rawResults);
            staffCourseIds = coursesById.keySet();
            editorCourseIds = coursesById.keySet();
        }
        Map<Long, String> courseNameById = new HashMap<>();
        coursesById.forEach((id, course) -> courseNameById.put(id, course.getTitle()));

        Map<Long, Long> exerciseGroupIdByExerciseId = resolveExerciseGroupIds(rawResults);
        Map<Long, String> channelNameById = resolveChannelNames(rawResults);
        List<GlobalSearchResultDTO> resultDTOs = new ArrayList<>();
        for (Map<String, Object> properties : rawResults) {
            GlobalSearchResultDTO dto = GlobalSearchResultDTO.fromSearchableItemProperties(properties, courseNameById, exerciseGroupIdByExerciseId, staffCourseIds, editorCourseIds,
                    channelNameById);
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
     * Resolves one {@link Course} per distinct {@code course_id} appearing in the Weaviate results.
     * Only used as fallback for admin-global searches where accessible courses are not pre-fetched
     * during filter building (the result courses are unknown until after the Weaviate query).
     */
    private Map<Long, Course> resolveCoursesById(List<Map<String, Object>> rawResults) {
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
        Map<Long, Course> result = new HashMap<>();
        courseRepository.findAllById(courseIds).forEach(course -> result.put(course.getId(), course));
        return result;
    }

    /**
     * Resolves the exercise group IDs for all exam exercises appearing in the Weaviate results.
     * This is needed to build the correct course-management routing URL for exam exercise results.
     */
    private Map<Long, Long> resolveExerciseGroupIds(List<Map<String, Object>> rawResults) {
        Set<Long> examExerciseIds = new HashSet<>();
        for (Map<String, Object> properties : rawResults) {
            Object type = properties.get(SearchableEntitySchema.Properties.TYPE);
            if (!SearchableEntitySchema.TypeValues.EXERCISE.equals(type)) {
                continue;
            }
            Object examId = properties.get(SearchableEntitySchema.Properties.EXAM_ID);
            if (examId == null) {
                continue;
            }
            Object rawId = properties.get(SearchableEntitySchema.Properties.ENTITY_ID);
            if (rawId instanceof Number number) {
                examExerciseIds.add(number.longValue());
            }
        }
        if (examExerciseIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        for (var dto : exerciseRepository.findExerciseAndGroupIdsByExerciseIds(examExerciseIds)) {
            result.put(dto.exerciseId(), dto.exerciseGroupId());
        }
        return result;
    }

    /**
     * Resolves channel names for all post and answer_post results in the Weaviate results.
     */
    private Map<Long, String> resolveChannelNames(List<Map<String, Object>> rawResults) {
        Set<Long> channelIds = new HashSet<>();
        for (Map<String, Object> properties : rawResults) {
            Object type = properties.get(SearchableEntitySchema.Properties.TYPE);
            if (!SearchableEntitySchema.TypeValues.POST.equals(type) && !SearchableEntitySchema.TypeValues.ANSWER_POST.equals(type)) {
                continue;
            }
            Object rawId = properties.get(SearchableEntitySchema.Properties.CHANNEL_ID);
            if (rawId instanceof Number number) {
                channelIds.add(number.longValue());
            }
        }
        if (channelIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        channelRepository.findAllById(channelIds).forEach(channel -> result.put(channel.getId(), channel.getName()));
        return result;
    }

    /**
     * Compound filter build result. {@code filter} may be {@code null} (admin access: no filter),
     * and {@code hasAccess} may be {@code false} (user has no accessible courses → short-circuit empty).
     * <p>
     * {@code accessibleCoursesById} and {@code staffCourseIds} are populated for non-admin paths
     * (and admin-with-courseId) so the caller can resolve course names and staff membership without
     * a redundant database round-trip. Both are {@code null} for admin-global (no courseId filter)
     * searches, where the result courses are unknown until after the Weaviate query.
     */
    private record FilterBuildResult(Filter filter, boolean hasAccess, Map<Long, Course> accessibleCoursesById, Set<Long> staffCourseIds, Set<Long> editorCourseIds) {
    }

    /**
     * Per-student exam registration data, pre-fetched once per request to scope exam and exam
     * exercise visibility to the student's actual registrations and assigned exercises.
     *
     * @param registeredExamIds       IDs of non-test exams where the student has a StudentExam
     * @param assignedExamExerciseIds IDs of exercises assigned to the student's StudentExams
     */
    private record StudentExamInfo(Set<Long> registeredExamIds, Set<Long> assignedExamExerciseIds) {
    }

    private StudentExamInfo fetchStudentExamInfo(long userId, List<Long> studentCourseIds) {
        if (studentCourseIds.isEmpty()) {
            return null;
        }
        Set<Long> registeredExamIds = studentExamRepository.findRegisteredNonTestExamIdsByUserIdAndCourseIds(userId, studentCourseIds);
        Set<Long> assignedExerciseIds = studentExamRepository.findAssignedExamExerciseIdsByUserIdAndCourseIds(userId, studentCourseIds);
        return new StudentExamInfo(registeredExamIds, assignedExerciseIds);
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
            if (!VALID_TYPES.equals(requestedTypes)) {
                return new FilterBuildResult(buildTypeDiscriminatorFilter(requestedTypes), true, null, null, null);
            }
            return new FilterBuildResult(null, true, null, null, null);
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
                return new FilterBuildResult(null, false, null, null, null);
            }
        }

        CourseRoleSets roleSets = groupCoursesByRole(user, accessibleCourses);
        StudentExamInfo studentExamInfo = fetchStudentExamInfo(user.getId(), roleSets.studentCourseIds());

        Map<Long, Course> accessibleCoursesById = new HashMap<>();
        for (Course course : accessibleCourses) {
            accessibleCoursesById.put(course.getId(), course);
        }
        Set<Long> staffCourseIds = new HashSet<>(roleSets.staffCourseIds());
        Set<Long> editorCourseIds = new HashSet<>(roleSets.editorCourseIds());

        List<Filter> disjuncts = new ArrayList<>();
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.EXERCISE)) {
            Filter disjunct = buildExerciseDisjunct(roleSets, studentExamInfo);
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
            Filter disjunct = buildExamDisjunct(roleSets, studentExamInfo);
            if (disjunct != null) {
                disjuncts.add(disjunct);
            }

            // When the exam filter is active, also include exercises that belong to exams
            boolean isExerciseTypeAlreadyRequested = requestedTypes.contains(SearchableEntitySchema.TypeValues.EXERCISE);
            if (!isExerciseTypeAlreadyRequested) {
                Filter examExerciseDisjunct = buildExamExerciseDisjunct(roleSets, studentExamInfo);
                if (examExerciseDisjunct != null) {
                    disjuncts.add(examExerciseDisjunct);
                }
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
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.COURSE)) {
            Filter disjunct = buildCourseDisjunct(roleSets);
            if (disjunct != null) {
                disjuncts.add(disjunct);
            }
        }
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.POST)) {
            Filter disjunct = buildPostDisjunct(roleSets);
            if (disjunct != null) {
                disjuncts.add(disjunct);
            }
        }
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.ANSWER_POST)) {
            Filter disjunct = buildAnswerPostDisjunct(roleSets);
            if (disjunct != null) {
                disjuncts.add(disjunct);
            }
        }

        if (disjuncts.isEmpty()) {
            return new FilterBuildResult(null, false, null, null, null);
        }
        if (disjuncts.size() == 1) {
            return new FilterBuildResult(disjuncts.getFirst(), true, accessibleCoursesById, staffCourseIds, editorCourseIds);
        }
        return new FilterBuildResult(Filter.or(disjuncts.toArray(new Filter[0])), true, accessibleCoursesById, staffCourseIds, editorCourseIds);
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

    /**
     * Builds the exercise type disjunct. Editors see all exercises in their courses; teaching
     * assistants see exercises with non-automatic assessment (regular exercises unconditionally,
     * exam exercises only after the exam ends); students see released regular exercises and exam
     * exercises after the exam starts.
     *
     * @param roleSets the per-course role classification for the current user
     * @return a filter matching exercises the user may access, or {@code null} if no courses qualify
     */
    private Filter buildExerciseDisjunct(CourseRoleSets roleSets, StudentExamInfo studentExamInfo) {
        List<Filter> subBranches = new ArrayList<>();
        if (!roleSets.editorCourseIds().isEmpty()) {
            subBranches.add(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.editorCourseIds()));
        }
        if (!roleSets.taCourseIds().isEmpty()) {
            subBranches.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.taCourseIds()), exerciseAccessFilter(Role.TEACHING_ASSISTANT, null)));
        }
        if (!roleSets.studentCourseIds().isEmpty()) {
            subBranches.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.studentCourseIds()), exerciseAccessFilter(Role.STUDENT, studentExamInfo)));
        }
        Filter combined = combineOr(subBranches);
        return combined == null ? null : Filter.and(typeEquals(SearchableEntitySchema.TypeValues.EXERCISE), combined);
    }

    private static Filter exerciseAccessFilter(Role role, StudentExamInfo studentExamInfo) {
        OffsetDateTime now = OffsetDateTime.now();
        if (role == Role.TEACHING_ASSISTANT) {
            // TAs: regular exercises are always visible (tutor uses the student view).
            // Exam exercises are only visible after the exam ends and only if the exercise is
            // assessable by TAs (not quiz, and programming only with manual/semi-automatic assessment).
            Filter regularExercises = Filter.property(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE).eq(false);

            Filter notProgrammingAndNotQuiz = Filter.and(Filter.property(SearchableEntitySchema.Properties.EXERCISE_TYPE).eq(ExerciseType.PROGRAMMING.getValue()).not(),
                    Filter.property(SearchableEntitySchema.Properties.EXERCISE_TYPE).eq(ExerciseType.QUIZ.getValue()).not());
            Filter programmingWithManualAssessment = Filter.and(Filter.property(SearchableEntitySchema.Properties.EXERCISE_TYPE).eq(ExerciseType.PROGRAMMING.getValue()),
                    Filter.or(Filter.property(SearchableEntitySchema.Properties.ASSESSMENT_TYPE).eq(AssessmentType.SEMI_AUTOMATIC.name()),
                            Filter.property(SearchableEntitySchema.Properties.ASSESSMENT_TYPE).eq(AssessmentType.MANUAL.name()),
                            Filter.property(SearchableEntitySchema.Properties.ASSESSMENT_TYPE).eq(AssessmentType.AUTOMATIC_ATHENA.name())));
            Filter assessableExamExercise = Filter.and(Filter.property(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE).eq(true),
                    Filter.property(SearchableEntitySchema.Properties.EXAM_END_DATE).lte(now), Filter.or(notProgrammingAndNotQuiz, programmingWithManualAssessment));

            return Filter.or(regularExercises, assessableExamExercise);
        }
        // Students: released regular exercises + only assigned exam exercises after exam start
        Filter releasedRegularExercises = Filter.and(Filter.property(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE).eq(false),
                Filter.or(Filter.property(SearchableEntitySchema.Properties.RELEASE_DATE).lte(now), Filter.property(SearchableEntitySchema.Properties.RELEASE_DATE).isNull()));

        if (studentExamInfo != null && !studentExamInfo.assignedExamExerciseIds().isEmpty()) {
            // Only show exam exercises that are assigned to the student's individual exam
            Filter assignedExamExercises = Filter.and(Filter.property(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE).eq(true),
                    Filter.property(SearchableEntitySchema.Properties.EXAM_START_DATE).lte(now),
                    Filter.property(SearchableEntitySchema.Properties.ENTITY_ID).containsAny(studentExamInfo.assignedExamExerciseIds().toArray(new Long[0])));
            return Filter.or(releasedRegularExercises, assignedExamExercises);
        }
        if (studentExamInfo != null) {
            // Student has no assigned exam exercises (not registered for any exam)
            return releasedRegularExercises;
        }
        // Fallback: studentExamInfo not available, use original behavior
        Filter startedExamExercises = Filter.and(Filter.property(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE).eq(true),
                Filter.property(SearchableEntitySchema.Properties.EXAM_START_DATE).lte(now));
        return Filter.or(releasedRegularExercises, startedExamExercises);
    }

    /**
     * Builds the lecture type disjunct. All users with course access can see lectures in their courses
     * (no additional visibility constraints).
     *
     * @param roleSets the per-course role classification for the current user
     * @return a filter matching lectures the user may access, or {@code null} if no courses qualify
     */
    private Filter buildLectureDisjunct(CourseRoleSets roleSets) {
        if (roleSets.allAccessibleCourseIds().isEmpty()) {
            return null;
        }
        return Filter.and(typeEquals(SearchableEntitySchema.TypeValues.LECTURE), courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.allAccessibleCourseIds()));
    }

    /**
     * Builds the lecture unit type disjunct. Staff members (editors, instructors, TAs) see all lecture
     * units in their courses; students only see lecture units whose release date has passed or is unset.
     *
     * @param roleSets the per-course role classification for the current user
     * @return a filter matching lecture units the user may access, or {@code null} if no courses qualify
     */
    private Filter buildLectureUnitDisjunct(CourseRoleSets roleSets) {
        OffsetDateTime now = OffsetDateTime.now();
        List<Filter> subBranches = new ArrayList<>();
        if (!roleSets.staffCourseIds().isEmpty()) {
            subBranches.add(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.staffCourseIds()));
        }
        if (!roleSets.studentCourseIds().isEmpty()) {
            subBranches.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.studentCourseIds()),
                    Filter.or(Filter.property(SearchableEntitySchema.Properties.RELEASE_DATE).lte(now), Filter.property(SearchableEntitySchema.Properties.RELEASE_DATE).isNull())));
        }
        Filter combined = combineOr(subBranches);
        return combined == null ? null : Filter.and(typeEquals(SearchableEntitySchema.TypeValues.LECTURE_UNIT), combined);
    }

    /**
     * Builds the exam exercise disjunct. This is used when the exam type filter is active but the exercise
     * type filter is not, to include exercises belonging to exams in the results. Applies the same
     * role-based visibility rules as {@link #buildExerciseDisjunct(CourseRoleSets, StudentExamInfo)} but restricts
     * results to exam exercises only ({@code is_exam_exercise = true}).
     *
     * @param roleSets the per-course role classification for the current user
     * @return a filter matching exam exercises the user may access, or {@code null} if no courses qualify
     */
    private Filter buildExamExerciseDisjunct(CourseRoleSets roleSets, StudentExamInfo studentExamInfo) {
        List<Filter> subBranches = new ArrayList<>();
        if (!roleSets.editorCourseIds().isEmpty()) {
            subBranches.add(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.editorCourseIds()));
        }
        if (!roleSets.taCourseIds().isEmpty()) {
            subBranches.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.taCourseIds()), exerciseAccessFilter(Role.TEACHING_ASSISTANT, null)));
        }
        if (!roleSets.studentCourseIds().isEmpty()) {
            subBranches.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.studentCourseIds()), exerciseAccessFilter(Role.STUDENT, studentExamInfo)));
        }
        Filter combined = combineOr(subBranches);
        if (combined == null) {
            return null;
        }
        return Filter.and(typeEquals(SearchableEntitySchema.TypeValues.EXERCISE), Filter.property(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE).eq(true), combined);
    }

    /**
     * Builds the exam type disjunct. Editors and instructors see all exams in their courses;
     * teaching assistants and students only see exams whose visible date has passed.
     *
     * @param roleSets the per-course role classification for the current user
     * @return a filter matching exams the user may access, or {@code null} if no courses qualify
     */
    private Filter buildExamDisjunct(CourseRoleSets roleSets, StudentExamInfo studentExamInfo) {
        OffsetDateTime now = OffsetDateTime.now();
        List<Filter> subBranches = new ArrayList<>();
        if (!roleSets.editorCourseIds().isEmpty()) {
            subBranches.add(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.editorCourseIds()));
        }
        // TAs: all exams with visible_date <= now
        if (!roleSets.taCourseIds().isEmpty()) {
            subBranches.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.taCourseIds()),
                    Filter.property(SearchableEntitySchema.Properties.VISIBLE_DATE).lte(now)));
        }
        // Students: test exams (visible to all) + registered regular exams, both gated by visible_date
        if (!roleSets.studentCourseIds().isEmpty()) {
            Filter studentExamFilter = buildStudentExamFilter(roleSets.studentCourseIds(), studentExamInfo, now);
            if (studentExamFilter != null) {
                subBranches.add(studentExamFilter);
            }
        }
        Filter combined = combineOr(subBranches);
        return combined == null ? null : Filter.and(typeEquals(SearchableEntitySchema.TypeValues.EXAM), combined);
    }

    /**
     * Builds the exam filter for students. Test exams are visible to all students (after visible_date),
     * while regular exams are only visible if the student has a StudentExam registration.
     */
    private static Filter buildStudentExamFilter(List<Long> studentCourseIds, StudentExamInfo studentExamInfo, OffsetDateTime now) {
        Filter courseFilter = courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, studentCourseIds);
        Filter visibleFilter = Filter.property(SearchableEntitySchema.Properties.VISIBLE_DATE).lte(now);

        if (studentExamInfo == null) {
            // Fallback: studentExamInfo not available, show all visible exams (original behavior)
            return Filter.and(courseFilter, visibleFilter);
        }

        List<Filter> branches = new ArrayList<>();
        // Test exams: always visible to students in their courses
        branches.add(Filter.and(courseFilter, visibleFilter, Filter.property(SearchableEntitySchema.Properties.TEST_EXAM).eq(true)));
        // Regular exams: only if registered
        if (!studentExamInfo.registeredExamIds().isEmpty()) {
            branches.add(Filter.and(courseFilter, visibleFilter,
                    Filter.property(SearchableEntitySchema.Properties.ENTITY_ID).containsAny(studentExamInfo.registeredExamIds().toArray(new Long[0]))));
        }
        return combineOr(branches);
    }

    /**
     * Builds the FAQ type disjunct. Staff members (editors, instructors, TAs) see all FAQs in their
     * courses; students only see FAQs with state {@code ACCEPTED}.
     *
     * @param roleSets the per-course role classification for the current user
     * @return a filter matching FAQs the user may access, or {@code null} if no courses qualify
     */
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

    /**
     * Builds the channel type disjunct. All users with course access can see channels that are either
     * course-wide or public within their courses.
     *
     * @param roleSets the per-course role classification for the current user
     * @return a filter matching channels the user may access, or {@code null} if no courses qualify
     */
    private Filter buildChannelDisjunct(CourseRoleSets roleSets) {
        if (roleSets.allAccessibleCourseIds().isEmpty()) {
            return null;
        }
        Filter courseScope = courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.allAccessibleCourseIds());
        Filter visibility = Filter.or(Filter.property(SearchableEntitySchema.Properties.CHANNEL_IS_COURSE_WIDE).eq(true),
                Filter.property(SearchableEntitySchema.Properties.CHANNEL_IS_PUBLIC).eq(true));
        return Filter.and(typeEquals(SearchableEntitySchema.TypeValues.CHANNEL), courseScope, visibility);
    }

    /**
     * Builds the course type disjunct. Users see courses they have access to (no additional
     * visibility constraints beyond course membership).
     *
     * @param roleSets the per-course role classification for the current user
     * @return a filter matching courses the user may access, or {@code null} if no courses qualify
     */
    private Filter buildCourseDisjunct(CourseRoleSets roleSets) {
        if (roleSets.allAccessibleCourseIds().isEmpty()) {
            return null;
        }
        return Filter.and(typeEquals(SearchableEntitySchema.TypeValues.COURSE), courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.allAccessibleCourseIds()));
    }

    /**
     * Builds the post type disjunct. Posts are only indexed for public channels, so course membership
     * is sufficient for access (no additional channel-level visibility check needed).
     *
     * @param roleSets the per-course role classification for the current user
     * @return a filter matching posts the user may access, or {@code null} if no courses qualify
     */
    private Filter buildPostDisjunct(CourseRoleSets roleSets) {
        if (roleSets.allAccessibleCourseIds().isEmpty()) {
            return null;
        }
        // Posts are only indexed for public channels, so course membership is sufficient for access
        return Filter.and(typeEquals(SearchableEntitySchema.TypeValues.POST), courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.allAccessibleCourseIds()));
    }

    /**
     * Builds the answer post type disjunct. Answer posts are only indexed for public channels, so
     * course membership is sufficient for access (no additional channel-level visibility check needed).
     *
     * @param roleSets the per-course role classification for the current user
     * @return a filter matching answer posts the user may access, or {@code null} if no courses qualify
     */
    private Filter buildAnswerPostDisjunct(CourseRoleSets roleSets) {
        if (roleSets.allAccessibleCourseIds().isEmpty()) {
            return null;
        }
        // Answer posts are only indexed for public channels, so course membership is sufficient for access
        return Filter.and(typeEquals(SearchableEntitySchema.TypeValues.ANSWER_POST), courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.allAccessibleCourseIds()));
    }

    // -- Shared helpers --

    private static Filter buildTypeDiscriminatorFilter(Set<String> types) {
        List<Filter> typeFilters = new ArrayList<>(types.size());
        for (String type : types) {
            typeFilters.add(typeEquals(type));
        }

        boolean isExamRequestedButExercisesAreNotIncludedYet = types.contains(SearchableEntitySchema.TypeValues.EXAM)
                && !types.contains(SearchableEntitySchema.TypeValues.EXERCISE);
        if (isExamRequestedButExercisesAreNotIncludedYet) {
            typeFilters.add(Filter.and(typeEquals(SearchableEntitySchema.TypeValues.EXERCISE), Filter.property(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE).eq(true)));
        }
        if (typeFilters.size() == 1) {
            return typeFilters.getFirst();
        }
        return Filter.or(typeFilters.toArray(new Filter[0]));
    }

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
