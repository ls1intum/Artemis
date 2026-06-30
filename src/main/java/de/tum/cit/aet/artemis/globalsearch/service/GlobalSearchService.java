package de.tum.cit.aet.artemis.globalsearch.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.api.StudentExamApi;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * Service encapsulating filter-building and Weaviate search logic for the global search feature.
 * Extracted from {@code GlobalSearchResource} so both the navigational search endpoint and the
 * Iris answer pipeline can share the same access-control rules without duplication.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class GlobalSearchService {

    private static final Logger log = LoggerFactory.getLogger(GlobalSearchService.class);

    /** Entity types handled by the Iris answer pipeline (lectures are handled by a separate retriever). */
    public static final Set<String> IRIS_ENTITY_TYPES = Set.of(SearchableEntitySchema.TypeValues.EXERCISE, SearchableEntitySchema.TypeValues.FAQ,
            SearchableEntitySchema.TypeValues.EXAM, SearchableEntitySchema.TypeValues.CHANNEL);

    private final SearchableEntityWeaviateService weaviateService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseRepository exerciseRepository;

    private final ChannelRepository channelRepository;

    private final Optional<StudentExamApi> studentExamApi;

    public GlobalSearchService(SearchableEntityWeaviateService weaviateService, CourseRepository courseRepository, AuthorizationCheckService authCheckService,
            ExerciseRepository exerciseRepository, ChannelRepository channelRepository, Optional<StudentExamApi> studentExamApi) {
        this.weaviateService = weaviateService;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
        this.exerciseRepository = exerciseRepository;
        this.channelRepository = channelRepository;
        this.studentExamApi = studentExamApi;
    }

    /**
     * Compound filter build result.
     *
     * @param filter                the Weaviate filter ({@code null} for admins — no filter applied)
     * @param hasAccess             {@code false} if the user has no accessible courses (caller short-circuits with empty list)
     * @param accessibleCoursesById courses fetched during filter building, reusable for post-processing
     * @param staffCourseIds        course IDs where the user is staff (editor or TA)
     * @param editorCourseIds       course IDs where the user is editor or instructor
     */
    public record FilterBuildResult(@Nullable Filter filter, boolean hasAccess, @Nullable Map<Long, Course> accessibleCoursesById, @Nullable Set<Long> staffCourseIds,
            @Nullable Set<Long> editorCourseIds) {
    }

    /**
     * Per-course role classification, computed once per request and reused across type disjuncts.
     */
    public record CourseRoleSets(List<Long> editorCourseIds, List<Long> taCourseIds, List<Long> studentCourseIds, List<Long> staffCourseIds, List<Long> allAccessibleCourseIds) {
    }

    /**
     * Per-student exam registration data, pre-fetched once per request.
     */
    public record StudentExamInfo(Set<Long> registeredExamIds, Set<Long> assignedExamExerciseIds) {
    }

    // ---- Public API ----

    /**
     * Builds the compound per-type access filter for the current request.
     *
     * @param user           the authenticated user
     * @param courseId       optional course scope ({@code null} = all accessible courses)
     * @param requestedTypes the entity types to include
     * @return a {@link FilterBuildResult} with the filter and access metadata
     */
    public FilterBuildResult buildFilter(User user, @Nullable Long courseId, Set<String> requestedTypes) {
        // lecture_unit is always co-requested with lecture to match production behaviour;
        // release-date filtering for non-admin users is applied per-path via buildLectureUnitDisjunct
        Set<String> effectiveTypes = new LinkedHashSet<>(requestedTypes);
        if (effectiveTypes.contains(SearchableEntitySchema.TypeValues.LECTURE)) {
            effectiveTypes.add(SearchableEntitySchema.TypeValues.LECTURE_UNIT);
        }

        boolean isAdmin = authCheckService.isAdmin(user);
        boolean needsCommFiltering = effectiveTypes.contains(SearchableEntitySchema.TypeValues.CHANNEL) || effectiveTypes.contains(SearchableEntitySchema.TypeValues.POST)
                || effectiveTypes.contains(SearchableEntitySchema.TypeValues.ANSWER_POST);

        log.info("[filter] user={} isAdmin={} courseId={} types={}", user.getLogin(), isAdmin, courseId, effectiveTypes);

        if (isAdmin && courseId == null && !needsCommFiltering) {
            log.info("[filter] user={} is admin with no courseId — skipping access filter", user.getLogin());
            return new FilterBuildResult(buildTypeDiscriminatorFilter(effectiveTypes), true, null, null, null);
        }

        List<Course> accessibleCourses;
        if (isAdmin && courseId == null) {
            accessibleCourses = courseRepository.findAll();
        }
        else if (courseId != null) {
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

        List<Course> commEnabledCourses = accessibleCourses.stream().filter(c -> c.getCourseInformationSharingConfiguration().isAnyCommunicationEnabled()).toList();
        CourseRoleSets roleSets = groupCoursesByRole(user, accessibleCourses);
        CourseRoleSets roleSetsComm = groupCoursesByRole(user, commEnabledCourses);
        StudentExamInfo studentExamInfo = fetchStudentExamInfo(user.getId(), roleSets.studentCourseIds());

        Map<Long, Course> accessibleCoursesById = new HashMap<>();
        for (Course course : accessibleCourses) {
            accessibleCoursesById.put(course.getId(), course);
        }
        java.util.HashSet<Long> staffCourseIds = new java.util.HashSet<>(roleSets.staffCourseIds());
        java.util.HashSet<Long> editorCourseIds = new java.util.HashSet<>(roleSets.editorCourseIds());

        List<Filter> disjuncts = new ArrayList<>();
        if (effectiveTypes.contains(SearchableEntitySchema.TypeValues.EXERCISE)) {
            addIfNotNull(disjuncts, isAdmin && courseId == null ? typeEquals(SearchableEntitySchema.TypeValues.EXERCISE) : buildExerciseDisjunct(roleSets, studentExamInfo));
        }
        if (effectiveTypes.contains(SearchableEntitySchema.TypeValues.LECTURE)) {
            addIfNotNull(disjuncts, isAdmin && courseId == null ? typeEquals(SearchableEntitySchema.TypeValues.LECTURE) : buildLectureDisjunct(roleSets));
        }
        if (effectiveTypes.contains(SearchableEntitySchema.TypeValues.LECTURE_UNIT)) {
            addIfNotNull(disjuncts, isAdmin && courseId == null ? typeEquals(SearchableEntitySchema.TypeValues.LECTURE_UNIT) : buildLectureUnitDisjunct(roleSets));
        }
        if (effectiveTypes.contains(SearchableEntitySchema.TypeValues.EXAM)) {
            addIfNotNull(disjuncts, isAdmin && courseId == null ? typeEquals(SearchableEntitySchema.TypeValues.EXAM) : buildExamDisjunct(roleSets, studentExamInfo));
            if (!effectiveTypes.contains(SearchableEntitySchema.TypeValues.EXERCISE)) {
                addIfNotNull(disjuncts, buildExamExerciseDisjunct(roleSets, studentExamInfo));
            }
        }
        if (effectiveTypes.contains(SearchableEntitySchema.TypeValues.FAQ)) {
            addIfNotNull(disjuncts, isAdmin && courseId == null ? typeEquals(SearchableEntitySchema.TypeValues.FAQ) : buildFaqDisjunct(roleSets));
        }
        if (effectiveTypes.contains(SearchableEntitySchema.TypeValues.CHANNEL)) {
            addIfNotNull(disjuncts, buildChannelDisjunct(roleSetsComm));
        }
        if (effectiveTypes.contains(SearchableEntitySchema.TypeValues.COURSE)) {
            addIfNotNull(disjuncts, isAdmin && courseId == null ? typeEquals(SearchableEntitySchema.TypeValues.COURSE) : buildCourseDisjunct(roleSets));
        }
        if (effectiveTypes.contains(SearchableEntitySchema.TypeValues.POST)) {
            addIfNotNull(disjuncts, buildPostDisjunct(roleSetsComm));
        }
        if (effectiveTypes.contains(SearchableEntitySchema.TypeValues.ANSWER_POST)) {
            addIfNotNull(disjuncts, buildAnswerPostDisjunct(roleSetsComm));
        }

        if (disjuncts.isEmpty()) {
            return new FilterBuildResult(null, false, null, null, null);
        }
        Filter combined = disjuncts.size() == 1 ? disjuncts.getFirst() : Filter.or(disjuncts.toArray(new Filter[0]));
        return new FilterBuildResult(combined, true, accessibleCoursesById, staffCourseIds, editorCourseIds);
    }

    /**
     * Pre-fetches searchable entities for the Iris answer pipeline.
     * Searches exercise, FAQ, exam, and channel types using the user's full access context.
     * Returns raw Weaviate property maps; the caller maps them to {@code PyrisSearchableEntityDTO}.
     *
     * @param query the search query
     * @param user  the authenticated user
     * @param limit maximum number of results to return
     * @return raw Weaviate property maps, or an empty list if the user has no access or Weaviate is unavailable
     */
    public List<Map<String, Object>> searchEntitiesForIrisPipeline(String query, User user, int limit) {
        FilterBuildResult result = buildFilter(user, null, IRIS_ENTITY_TYPES);
        if (!result.hasAccess()) {
            return List.of();
        }
        return weaviateService.searchSearchableEntities(query, result.filter(), limit);
    }

    // ---- Role / exam helpers ----

    public CourseRoleSets groupCoursesByRole(User user, List<Course> courses) {
        List<Long> editorIds = new ArrayList<>();
        List<Long> taIds = new ArrayList<>();
        List<Long> studentIds = new ArrayList<>();
        for (Course course : courses) {
            Role role = getUserRoleInCourse(user, course);
            switch (role) {
                case EDITOR, INSTRUCTOR -> editorIds.add(course.getId());
                case TEACHING_ASSISTANT -> taIds.add(course.getId());
                default -> studentIds.add(course.getId());
            }
        }
        List<Long> staffIds = new ArrayList<>(editorIds.size() + taIds.size());
        staffIds.addAll(editorIds);
        staffIds.addAll(taIds);
        List<Long> allIds = courses.stream().map(Course::getId).toList();
        return new CourseRoleSets(editorIds, taIds, studentIds, staffIds, allIds);
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

    @Nullable
    public StudentExamInfo fetchStudentExamInfo(long userId, List<Long> studentCourseIds) {
        if (studentCourseIds.isEmpty() || studentExamApi.isEmpty()) {
            return null;
        }
        StudentExamApi api = studentExamApi.get();
        Set<Long> registeredExamIds = api.findRegisteredNonTestExamIdsByUserIdAndCourseIds(userId, studentCourseIds);
        Set<Long> assignedExerciseIds = api.findAssignedExamExerciseIdsByUserIdAndCourseIds(userId, studentCourseIds);
        return new StudentExamInfo(registeredExamIds, assignedExerciseIds);
    }

    // ---- Type disjuncts ----

    @Nullable
    public Filter buildExerciseDisjunct(CourseRoleSets roleSets, @Nullable StudentExamInfo studentExamInfo) {
        List<Filter> sub = new ArrayList<>();
        if (!roleSets.editorCourseIds().isEmpty()) {
            sub.add(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.editorCourseIds()));
        }
        if (!roleSets.taCourseIds().isEmpty()) {
            sub.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.taCourseIds()), exerciseAccessFilter(Role.TEACHING_ASSISTANT, null)));
        }
        if (!roleSets.studentCourseIds().isEmpty()) {
            sub.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.studentCourseIds()), exerciseAccessFilter(Role.STUDENT, studentExamInfo)));
        }
        Filter combined = combineOr(sub);
        return combined == null ? null : Filter.and(typeEquals(SearchableEntitySchema.TypeValues.EXERCISE), combined);
    }

    @Nullable
    public Filter buildExamExerciseDisjunct(CourseRoleSets roleSets, @Nullable StudentExamInfo studentExamInfo) {
        List<Filter> sub = new ArrayList<>();
        if (!roleSets.editorCourseIds().isEmpty()) {
            sub.add(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.editorCourseIds()));
        }
        if (!roleSets.taCourseIds().isEmpty()) {
            sub.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.taCourseIds()), exerciseAccessFilter(Role.TEACHING_ASSISTANT, null)));
        }
        if (!roleSets.studentCourseIds().isEmpty()) {
            sub.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.studentCourseIds()), exerciseAccessFilter(Role.STUDENT, studentExamInfo)));
        }
        Filter combined = combineOr(sub);
        if (combined == null) {
            return null;
        }
        return Filter.and(typeEquals(SearchableEntitySchema.TypeValues.EXERCISE), Filter.property(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE).eq(true), combined);
    }

    @Nullable
    public Filter buildLectureDisjunct(CourseRoleSets roleSets) {
        if (roleSets.allAccessibleCourseIds().isEmpty()) {
            return null;
        }
        List<Filter> sub = new ArrayList<>();
        if (!roleSets.staffCourseIds().isEmpty()) {
            sub.add(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.staffCourseIds()));
        }
        if (!roleSets.studentCourseIds().isEmpty()) {
            // Lectures never carry a release_date, so adding isNull() is semantically a no-op for real
            // lecture rows. It is required as a defence against Weaviate's word tokenizer splitting
            // "lecture_unit" → ["lecture","unit"], which makes the type="lecture" equality filter
            // accidentally match lecture_unit documents. Without this guard an unreleased lecture_unit
            // (release_date != null) would pass through this branch and bypass the date check.
            sub.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.studentCourseIds()),
                    Filter.property(SearchableEntitySchema.Properties.RELEASE_DATE).isNull()));
        }
        Filter combined = combineOr(sub);
        return combined == null ? null : Filter.and(typeEquals(SearchableEntitySchema.TypeValues.LECTURE), combined);
    }

    @Nullable
    public Filter buildLectureUnitDisjunct(CourseRoleSets roleSets) {
        OffsetDateTime now = OffsetDateTime.now();
        log.info("[filter] lecture_unit staffCourseIds={} studentCourseIds={} filterTime={}", roleSets.staffCourseIds(), roleSets.studentCourseIds(), now);
        List<Filter> sub = new ArrayList<>();
        if (!roleSets.staffCourseIds().isEmpty()) {
            sub.add(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.staffCourseIds()));
        }
        if (!roleSets.studentCourseIds().isEmpty()) {
            sub.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.studentCourseIds()),
                    Filter.or(Filter.property(SearchableEntitySchema.Properties.RELEASE_DATE).lte(now), Filter.property(SearchableEntitySchema.Properties.RELEASE_DATE).isNull())));
        }
        Filter combined = combineOr(sub);
        return combined == null ? null : Filter.and(typeEquals(SearchableEntitySchema.TypeValues.LECTURE_UNIT), combined);
    }

    @Nullable
    public Filter buildExamDisjunct(CourseRoleSets roleSets, @Nullable StudentExamInfo studentExamInfo) {
        OffsetDateTime now = OffsetDateTime.now();
        List<Filter> sub = new ArrayList<>();
        if (!roleSets.editorCourseIds().isEmpty()) {
            sub.add(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.editorCourseIds()));
        }
        if (!roleSets.taCourseIds().isEmpty()) {
            sub.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.taCourseIds()),
                    Filter.property(SearchableEntitySchema.Properties.VISIBLE_DATE).lte(now)));
        }
        if (!roleSets.studentCourseIds().isEmpty()) {
            Filter studentFilter = buildStudentExamFilter(roleSets.studentCourseIds(), studentExamInfo, now);
            addIfNotNull(sub, studentFilter);
        }
        Filter combined = combineOr(sub);
        return combined == null ? null : Filter.and(typeEquals(SearchableEntitySchema.TypeValues.EXAM), combined);
    }

    @Nullable
    public Filter buildFaqDisjunct(CourseRoleSets roleSets) {
        List<Filter> sub = new ArrayList<>();
        if (!roleSets.staffCourseIds().isEmpty()) {
            sub.add(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.staffCourseIds()));
        }
        if (!roleSets.studentCourseIds().isEmpty()) {
            sub.add(Filter.and(courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.studentCourseIds()),
                    Filter.property(SearchableEntitySchema.Properties.FAQ_STATE).eq("ACCEPTED")));
        }
        Filter combined = combineOr(sub);
        return combined == null ? null : Filter.and(typeEquals(SearchableEntitySchema.TypeValues.FAQ), combined);
    }

    @Nullable
    public Filter buildChannelDisjunct(CourseRoleSets roleSets) {
        if (roleSets.allAccessibleCourseIds().isEmpty()) {
            return null;
        }
        Filter courseScope = courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.allAccessibleCourseIds());
        Filter visibility = Filter.or(Filter.property(SearchableEntitySchema.Properties.CHANNEL_IS_COURSE_WIDE).eq(true),
                Filter.property(SearchableEntitySchema.Properties.CHANNEL_IS_PUBLIC).eq(true));
        return Filter.and(typeEquals(SearchableEntitySchema.TypeValues.CHANNEL), courseScope, visibility);
    }

    @Nullable
    public Filter buildCourseDisjunct(CourseRoleSets roleSets) {
        if (roleSets.allAccessibleCourseIds().isEmpty()) {
            return null;
        }
        return Filter.and(typeEquals(SearchableEntitySchema.TypeValues.COURSE), courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.allAccessibleCourseIds()));
    }

    @Nullable
    public Filter buildPostDisjunct(CourseRoleSets roleSets) {
        if (roleSets.allAccessibleCourseIds().isEmpty()) {
            return null;
        }
        return Filter.and(typeEquals(SearchableEntitySchema.TypeValues.POST), courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.allAccessibleCourseIds()));
    }

    @Nullable
    public Filter buildAnswerPostDisjunct(CourseRoleSets roleSets) {
        if (roleSets.allAccessibleCourseIds().isEmpty()) {
            return null;
        }
        return Filter.and(typeEquals(SearchableEntitySchema.TypeValues.ANSWER_POST), courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.allAccessibleCourseIds()));
    }

    // ---- Static helpers ----

    public static Filter exerciseAccessFilter(Role role, @Nullable StudentExamInfo studentExamInfo) {
        OffsetDateTime now = OffsetDateTime.now();
        if (role == Role.TEACHING_ASSISTANT) {
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
        Filter releasedRegular = Filter.and(Filter.property(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE).eq(false),
                Filter.or(Filter.property(SearchableEntitySchema.Properties.RELEASE_DATE).lte(now), Filter.property(SearchableEntitySchema.Properties.RELEASE_DATE).isNull()));
        if (studentExamInfo != null && !studentExamInfo.assignedExamExerciseIds().isEmpty()) {
            Filter assignedExam = Filter.and(Filter.property(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE).eq(true),
                    Filter.property(SearchableEntitySchema.Properties.EXAM_START_DATE).lte(now),
                    Filter.property(SearchableEntitySchema.Properties.ENTITY_ID).containsAny(studentExamInfo.assignedExamExerciseIds().toArray(new Long[0])));
            return Filter.or(releasedRegular, assignedExam);
        }
        if (studentExamInfo != null) {
            return releasedRegular;
        }
        Filter startedExam = Filter.and(Filter.property(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE).eq(true),
                Filter.property(SearchableEntitySchema.Properties.EXAM_START_DATE).lte(now));
        return Filter.or(releasedRegular, startedExam);
    }

    @Nullable
    private static Filter buildStudentExamFilter(List<Long> studentCourseIds, @Nullable StudentExamInfo studentExamInfo, OffsetDateTime now) {
        Filter courseFilter = courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, studentCourseIds);
        Filter visibleFilter = Filter.property(SearchableEntitySchema.Properties.VISIBLE_DATE).lte(now);
        if (studentExamInfo == null) {
            return Filter.and(courseFilter, visibleFilter);
        }
        List<Filter> branches = new ArrayList<>();
        branches.add(Filter.and(courseFilter, visibleFilter, Filter.property(SearchableEntitySchema.Properties.TEST_EXAM).eq(true)));
        if (!studentExamInfo.registeredExamIds().isEmpty()) {
            branches.add(Filter.and(courseFilter, visibleFilter,
                    Filter.property(SearchableEntitySchema.Properties.ENTITY_ID).containsAny(studentExamInfo.registeredExamIds().toArray(new Long[0]))));
        }
        return combineOr(branches);
    }

    public static Filter buildTypeDiscriminatorFilter(Set<String> types) {
        List<Filter> typeFilters = new ArrayList<>(types.size());
        for (String type : types) {
            typeFilters.add(typeEquals(type));
        }
        boolean examButNotExercise = types.contains(SearchableEntitySchema.TypeValues.EXAM) && !types.contains(SearchableEntitySchema.TypeValues.EXERCISE);
        if (examButNotExercise) {
            typeFilters.add(Filter.and(typeEquals(SearchableEntitySchema.TypeValues.EXERCISE), Filter.property(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE).eq(true)));
        }
        return typeFilters.size() == 1 ? typeFilters.getFirst() : Filter.or(typeFilters.toArray(new Filter[0]));
    }

    public static Filter typeEquals(String type) {
        return Filter.property(SearchableEntitySchema.Properties.TYPE).eq(type);
    }

    public static Filter courseIdIn(String property, List<Long> courseIds) {
        if (courseIds.size() == 1) {
            return Filter.property(property).eq(courseIds.getFirst());
        }
        return Filter.property(property).containsAny(courseIds.toArray(new Long[0]));
    }

    @Nullable
    public static Filter combineOr(Collection<Filter> filters) {
        List<Filter> nonNull = new ArrayList<>();
        for (Filter f : filters) {
            if (f != null) {
                nonNull.add(f);
            }
        }
        if (nonNull.isEmpty()) {
            return null;
        }
        return nonNull.size() == 1 ? nonNull.getFirst() : Filter.or(nonNull.toArray(new Filter[0]));
    }

    private static void addIfNotNull(List<Filter> list, @Nullable Filter f) {
        if (f != null) {
            list.add(f);
        }
    }
}
