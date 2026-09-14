package de.tum.cit.aet.artemis.globalsearch.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.api.StudentExamApi;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * Builds the compound per-type Weaviate access filter for the unified {@code SearchableEntities}
 * collection. Extracted from {@code GlobalSearchResource} so that other modules (the Iris answer
 * path prefetching entity candidates) can apply EXACTLY the same access rules as the search
 * palette: role-dependent release gates, per-student exam registrations and assignments, FAQ
 * states, and channel visibility all live here and nowhere else.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityAccessFilterService {

    private final AuthorizationCheckService authCheckService;

    private final CourseRepository courseRepository;

    private final Optional<StudentExamApi> studentExamRepository;

    public SearchableEntityAccessFilterService(AuthorizationCheckService authCheckService, CourseRepository courseRepository, Optional<StudentExamApi> studentExamRepository) {
        this.authCheckService = authCheckService;
        this.courseRepository = courseRepository;
        this.studentExamRepository = studentExamRepository;
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
    public record FilterBuildResult(Filter filter, boolean hasAccess, Map<Long, Course> accessibleCoursesById, Set<Long> staffCourseIds, Set<Long> editorCourseIds) {
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
        if (studentCourseIds.isEmpty() || studentExamRepository.isEmpty()) {
            return null;
        }
        StudentExamApi api = studentExamRepository.get();
        Set<Long> registeredExamIds = api.findRegisteredNonTestExamIdsByUserIdAndCourseIds(userId, studentCourseIds);
        Set<Long> assignedExerciseIds = api.findAssignedExamExerciseIdsByUserIdAndCourseIds(userId, studentCourseIds);
        return new StudentExamInfo(registeredExamIds, assignedExerciseIds);
    }

    /**
     * Builds the compound per-type filter for the current request. Returns:
     * <ul>
     * <li>{@code hasAccess = false} if the user has no accessible courses (caller short-circuits with empty list)</li>
     * <li>{@code filter = null} for admins (Weaviate query runs with no filter)</li>
     * <li>an {@code OR}-of-{@code AND}s filter with one disjunct per requested type otherwise</li>
     * </ul>
     *
     * @param user             the requesting user (with course roles loaded)
     * @param courseIds        optional course ids to scope the search to; inaccessible or unknown ids are dropped
     * @param excludeCourseIds optional course ids whose results are hidden; applied after access resolution
     * @param requestedTypes   the entity types to include
     * @return the compound filter plus the per-request access context (accessible courses, staff and editor course ids)
     */
    public FilterBuildResult buildSearchableItemFilter(User user, List<Long> courseIds, List<Long> excludeCourseIds, Set<String> requestedTypes) {
        // Decide if the filters should be applied
        boolean isAdmin = authCheckService.isCurrentUserAdminAccessEnabled();
        boolean hasCourseFilter = courseIds != null && !courseIds.isEmpty();
        boolean hasExcludeFilter = excludeCourseIds != null && !excludeCourseIds.isEmpty();
        boolean needsCommFiltering = requestedTypes.contains(SearchableEntitySchema.TypeValues.CHANNEL) || requestedTypes.contains(SearchableEntitySchema.TypeValues.POST)
                || requestedTypes.contains(SearchableEntitySchema.TypeValues.ANSWER_POST);

        if (isAdmin && !hasCourseFilter && !needsCommFiltering) {
            // Admin, no include filter, no communication filtering: the cheap type-discriminator filter already
            // covers visibility. An exclude filter is applied as a single NOT clause rather than enumerating the
            // complement course set (which for an admin would be every course in the instance).
            Filter adminFilter = buildTypeDiscriminatorFilter(requestedTypes);
            if (hasExcludeFilter) {
                // Safe because every indexed row carries a non-null course_id: course rows use their own id as
                // course_id (CourseSearchableEntityDTO), and every other entity DTO writes the owning course_id.
                // So this NOT-clause never evaluates against an absent property (whose negation semantics are
                // version-dependent in Weaviate); a row is dropped iff its course_id is in the exclude set.
                adminFilter = Filter.and(adminFilter, courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, excludeCourseIds).not());
            }
            return new FilterBuildResult(adminFilter, true, null, null, null);
        }
        List<Course> accessibleCourses;
        if (isAdmin && !hasCourseFilter) {
            accessibleCourses = courseRepository.findAll();
        }
        else if (hasCourseFilter) {
            // Scope to the requested courses the user can actually access. Inaccessible or unknown ids are dropped
            // rather than rejected, so an OR across courses never 403s on a single stale id.
            Set<Long> requestedCourseIds = new HashSet<>(courseIds);
            if (isAdmin) {
                accessibleCourses = new ArrayList<>();
                courseRepository.findAllById(requestedCourseIds).forEach(accessibleCourses::add);
            }
            else {
                accessibleCourses = courseRepository.findAllAccessibleCoursesForUser(user.getId(), false).stream().filter(course -> requestedCourseIds.contains(course.getId()))
                        .toList();
            }
            if (accessibleCourses.isEmpty()) {
                return new FilterBuildResult(null, false, null, null, null);
            }
        }
        else {
            accessibleCourses = courseRepository.findAllAccessibleCoursesForUser(user.getId(), false);
            if (accessibleCourses.isEmpty()) {
                return new FilterBuildResult(null, false, null, null, null);
            }
        }
        // Drop excluded courses so their results are hidden. Runs after include/access resolution, so an
        // exclusion can only ever narrow the set the user may already see (never widen access).
        if (hasExcludeFilter) {
            Set<Long> excludedIds = new HashSet<>(excludeCourseIds);
            accessibleCourses = accessibleCourses.stream().filter(course -> !excludedIds.contains(course.getId())).toList();
            if (accessibleCourses.isEmpty()) {
                return new FilterBuildResult(null, false, null, null, null);
            }
        }
        List<Course> accessibleCoursesEnabledCommunication = accessibleCourses.stream()
                .filter(course -> course.getCourseInformationSharingConfiguration().isAnyCommunicationEnabled()).toList();

        CourseRoleSets roleSets = groupCoursesByRole(user, accessibleCourses);
        CourseRoleSets roleSetsEnabledCommunication = groupCoursesByRole(user, accessibleCoursesEnabledCommunication);
        StudentExamInfo studentExamInfo = fetchStudentExamInfo(user.getId(), roleSets.studentCourseIds());

        Map<Long, Course> accessibleCoursesById = new HashMap<>();
        for (Course course : accessibleCourses) {
            accessibleCoursesById.put(course.getId(), course);
        }
        Set<Long> staffCourseIds = new HashSet<>(roleSets.staffCourseIds());
        Set<Long> editorCourseIds = new HashSet<>(roleSets.editorCourseIds());

        List<Filter> disjuncts = new ArrayList<>();
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.EXERCISE)) {
            if (isAdmin && !hasCourseFilter) {
                disjuncts.add(typeEquals(SearchableEntitySchema.TypeValues.EXERCISE));
            }
            else {
                Filter disjunct = buildExerciseDisjunct(roleSets, studentExamInfo);
                if (disjunct != null) {
                    disjuncts.add(disjunct);
                }
            }

        }
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.LECTURE)) {
            if (isAdmin && !hasCourseFilter) {
                disjuncts.add(typeEquals(SearchableEntitySchema.TypeValues.LECTURE));
            }
            else {
                Filter disjunct = buildLectureDisjunct(roleSets);
                if (disjunct != null) {
                    disjuncts.add(disjunct);
                }
            }
        }
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.LECTURE_UNIT)) {
            if (isAdmin && !hasCourseFilter) {
                disjuncts.add(typeEquals(SearchableEntitySchema.TypeValues.LECTURE_UNIT));
            }
            else {
                Filter disjunct = buildLectureUnitDisjunct(roleSets);
                if (disjunct != null) {
                    disjuncts.add(disjunct);
                }
            }
        }
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.EXAM)) {
            if (isAdmin && !hasCourseFilter) {
                disjuncts.add(typeEquals(SearchableEntitySchema.TypeValues.EXAM));
            }
            else {
                Filter disjunct = buildExamDisjunct(roleSets, studentExamInfo);
                if (disjunct != null) {
                    disjuncts.add(disjunct);
                }
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
            if (isAdmin && !hasCourseFilter) {
                disjuncts.add(typeEquals(SearchableEntitySchema.TypeValues.FAQ));
            }
            else {
                Filter disjunct = buildFaqDisjunct(roleSets);
                if (disjunct != null) {
                    disjuncts.add(disjunct);
                }
            }
        }
        // Only search within courses where communication configuration is active
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.CHANNEL)) {
            Filter disjunct = buildChannelDisjunct(roleSetsEnabledCommunication);
            if (disjunct != null) {
                disjuncts.add(disjunct);
            }
        }
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.COURSE)) {
            if (isAdmin && !hasCourseFilter) {
                disjuncts.add(typeEquals(SearchableEntitySchema.TypeValues.COURSE));
            }
            else {
                Filter disjunct = buildCourseDisjunct(roleSets);
                if (disjunct != null) {
                    disjuncts.add(disjunct);
                }
            }
        }
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.POST)) {
            Filter disjunct = buildPostDisjunct(roleSetsEnabledCommunication);
            if (disjunct != null) {
                disjuncts.add(disjunct);
            }
        }
        if (requestedTypes.contains(SearchableEntitySchema.TypeValues.ANSWER_POST)) {
            Filter disjunct = buildAnswerPostDisjunct(roleSetsEnabledCommunication);
            if (disjunct != null) {
                disjuncts.add(disjunct);
            }
        }

        if (disjuncts.isEmpty()) {
            return new FilterBuildResult(null, false, null, null, null);
        }
        Filter combined = disjuncts.size() == 1 ? disjuncts.getFirst() : Filter.or(disjuncts.toArray(new Filter[0]));
        // Admin without an include filter builds unscoped type-discriminator disjuncts (e.g. typeEquals(exercise)), so
        // narrowing the accessible-course set above never reaches them. Apply the exclusion as a single course_id NOT
        // IN (...) clause here too, mirroring the fast path. Safe because every indexed row carries a non-null
        // course_id, so the negation never evaluates against an absent property.
        if (isAdmin && !hasCourseFilter && hasExcludeFilter) {
            combined = Filter.and(combined, courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, excludeCourseIds).not());
        }
        return new FilterBuildResult(combined, true, accessibleCoursesById, staffCourseIds, editorCourseIds);
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
     * <p>
     * The {@code type} discriminator uses Weaviate's default {@code word} tokenization, which indexes
     * {@code "lecture_unit"} as the tokens {@code ["lecture", "unit"]}. A {@code type Equal "lecture"}
     * filter therefore also matches {@code lecture_unit} rows, which would drag them into this branch
     * that has no release-date guard and leak unreleased lecture units. The explicit
     * {@code type NotEqual "lecture_unit"} clause removes only the unit rows (they carry both tokens),
     * while genuine {@code lecture} rows (token {@code ["lecture"]}) are kept; lecture units are gated
     * by {@link #buildLectureUnitDisjunct(CourseRoleSets)} instead.
     *
     * @param roleSets the per-course role classification for the current user
     * @return a filter matching lectures the user may access, or {@code null} if no courses qualify
     */
    private Filter buildLectureDisjunct(CourseRoleSets roleSets) {
        if (roleSets.allAccessibleCourseIds().isEmpty()) {
            return null;
        }
        return Filter.and(typeEquals(SearchableEntitySchema.TypeValues.LECTURE), typeEquals(SearchableEntitySchema.TypeValues.LECTURE_UNIT).not(),
                courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.allAccessibleCourseIds()));
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
     * <p>
     * Carries the same {@code type}-tokenization guard as {@link #buildLectureDisjunct(CourseRoleSets)}:
     * {@code "answer_post"} is indexed as the tokens {@code ["answer", "post"]}, so {@code type Equal "post"}
     * also matches answer-post rows and a caller asking for posts would get replies mixed in. Unlike the
     * lecture case this is a correctness rather than an access problem, because answer posts are gated by
     * the same course membership, but the filter must still mean what it says.
     *
     * @param roleSets the per-course role classification for the current user
     * @return a filter matching posts the user may access, or {@code null} if no courses qualify
     */
    private Filter buildPostDisjunct(CourseRoleSets roleSets) {
        if (roleSets.allAccessibleCourseIds().isEmpty()) {
            return null;
        }
        // Posts are only indexed for public channels, so course membership is sufficient for access
        return Filter.and(typeEquals(SearchableEntitySchema.TypeValues.POST), typeEquals(SearchableEntitySchema.TypeValues.ANSWER_POST).not(),
                courseIdIn(SearchableEntitySchema.Properties.COURSE_ID, roleSets.allAccessibleCourseIds()));
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
