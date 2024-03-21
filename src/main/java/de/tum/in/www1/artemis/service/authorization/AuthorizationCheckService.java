package de.tum.in.www1.artemis.service.authorization;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.AuthorizationRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ExerciseDateService;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * Service used to check whether user is authorized to perform actions on the entity.
 */
@Profile(PROFILE_CORE)
@Service
public class AuthorizationCheckService
        implements StudentAuthorizationCheck, TeachingAssistantAuthorizationCheck, EditorAuthorizationCheck, InstructorAuthorizationCheck, AdminAuthorizationCheck {

    private final AuthorizationRepository authorizationRepository;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final ExamDateService examDateService;

    // TODO: we should move this into some kind of EnrollmentService
    @Deprecated(forRemoval = true) // will be removed in 7.0.0
    @Value("${artemis.user-management.course-registration.allowed-username-pattern:#{null}}")
    private Pattern allowedCourseRegistrationUsernamePattern;

    @Value("${artemis.user-management.course-enrollment.allowed-username-pattern:#{null}}")
    private Pattern allowedCourseEnrollmentUsernamePattern;

    private final TeamRepository teamRepository;

    public AuthorizationCheckService(AuthorizationRepository authorizationRepository, UserRepository userRepository, CourseRepository courseRepository,
            ExamDateService examDateService, TeamRepository teamRepository) {
        this.authorizationRepository = authorizationRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.examDateService = examDateService;

        if (allowedCourseEnrollmentUsernamePattern == null) {
            allowedCourseEnrollmentUsernamePattern = allowedCourseRegistrationUsernamePattern;
        }
        this.teamRepository = teamRepository;
    }

    @Override
    public AuthorizationRepository getAuthorizationRepository() {
        return authorizationRepository;
    }

    /**
     * An enum that represents the different reasons why a user is not allowed to self enroll in a course,
     * or ALLOWED if the user is allowed to self enroll in the course.
     */
    private enum EnrollmentAuthorization {
        ALLOWED, USERNAME_PATTERN, ENROLLMENT_STATUS, ENROLLMENT_PERIOD, ONLINE, ORGANIZATIONS
    }

    /**
     * Checks if the user is allowed to self enroll in the given course.
     * Returns `EnrollmentAuthorization.ALLOWED` if the user is allowed to self enroll in the course,
     * or the reason why the user is not allowed to self enroll in the course otherwise.
     * See also: {@link #checkUserAllowedToEnrollInCourseElseThrow(User, Course)}
     *
     * @param user   The user that wants to self enroll
     * @param course The course to which the user wants to self enroll
     * @return `EnrollmentAuthorization.ALLOWED` if the user is allowed to self enroll in the course,
     *         or the reason why the user is not allowed to self enroll in the course otherwise
     */
    @CheckReturnValue
    public EnrollmentAuthorization getUserEnrollmentAuthorizationForCourse(User user, Course course) {
        if (allowedCourseEnrollmentUsernamePattern != null && !allowedCourseEnrollmentUsernamePattern.matcher(user.getLogin()).matches()) {
            return EnrollmentAuthorization.USERNAME_PATTERN;
        }
        if (!Boolean.TRUE.equals(course.isEnrollmentEnabled())) {
            return EnrollmentAuthorization.ENROLLMENT_STATUS;
        }
        if (!Boolean.TRUE.equals(course.enrollmentIsActive())) {
            return EnrollmentAuthorization.ENROLLMENT_PERIOD;
        }
        Set<Organization> courseOrganizations = course.getOrganizations();
        if (courseOrganizations != null && !courseOrganizations.isEmpty() && !courseRepository.checkIfUserIsMemberOfCourseOrganizations(user, course)) {
            return EnrollmentAuthorization.ORGANIZATIONS;
        }
        if (course.isOnlineCourse()) {
            return EnrollmentAuthorization.ONLINE;
        }
        return EnrollmentAuthorization.ALLOWED;
    }

    /**
     * Checks if the user is allowed to self enroll in the given course.
     * See also: {@link #checkUserAllowedToEnrollInCourseElseThrow(User, Course)}
     *
     * @param user   The user that wants to self enroll
     * @param course The course to which the user wants to self enroll
     * @return boolean, true if the user is allowed to self enroll in the course, false otherwise
     */
    @CheckReturnValue
    public boolean isUserAllowedToSelfEnrollInCourse(User user, Course course) {
        return EnrollmentAuthorization.ALLOWED.equals(getUserEnrollmentAuthorizationForCourse(user, course));
    }

    /**
     * Checks if the user is allowed to self enroll in the given course.
     * Throws an AccessForbiddenException if the user is not allowed to self enroll in the course.
     * See also: {@link #getUserEnrollmentAuthorizationForCourse(User, Course)}
     *
     * @param user   The user that wants to self enroll
     * @param course The course to which the user wants to self enroll
     */
    public void checkUserAllowedToEnrollInCourseElseThrow(User user, Course course) throws AccessForbiddenException {
        EnrollmentAuthorization auth = getUserEnrollmentAuthorizationForCourse(user, course);
        switch (auth) {
            case USERNAME_PATTERN -> throw new AccessForbiddenException("Enrollment with this username is not allowed.");
            case ENROLLMENT_STATUS -> throw new AccessForbiddenException("The course does not allow enrollment.");
            case ENROLLMENT_PERIOD -> throw new AccessForbiddenException("The course does currently not allow enrollment.");
            case ORGANIZATIONS -> throw new AccessForbiddenException("User is not member of any organization of this course.");
            case ONLINE -> throw new AccessForbiddenException("Online courses cannot be enrolled in.");
        }
    }

    /**
     * An enum that represents the different reasons why a user is not allowed to unenroll from a course,
     * or ALLOWED if the user is allowed to unenroll from the course.
     */
    private enum UnenrollmentAuthorization {
        ALLOWED, UNENROLLMENT_STATUS, UNENROLLMENT_PERIOD, ONLINE
    }

    /**
     * Checks if the user is allowed to unenroll from the given course.
     * Returns `UnenrollmentAuthorization.ALLOWED` if the user is allowed to unenroll from the course,
     * or the reason why the user is not allowed to unenroll from the course otherwise.
     * See also: {@link #checkUserAllowedToUnenrollFromCourseElseThrow(User, Course)}
     *
     * @param user   The user that wants to unenroll
     * @param course The course from which the user wants to unenroll
     * @return `UnenrollmentAuthorization.ALLOWED` if the user is allowed to self unenroll from the course,
     *         or the reason why the user is not allowed to self unenroll from the course otherwise
     */
    @CheckReturnValue
    public UnenrollmentAuthorization getUserUnenrollmentAuthorizationForCourse(User user, Course course) {
        if (!course.isUnenrollmentEnabled()) {
            return UnenrollmentAuthorization.UNENROLLMENT_STATUS;
        }
        if (!course.unenrollmentIsActive()) {
            return UnenrollmentAuthorization.UNENROLLMENT_PERIOD;
        }
        if (course.isOnlineCourse()) {
            return UnenrollmentAuthorization.ONLINE;
        }
        return UnenrollmentAuthorization.ALLOWED;
    }

    /**
     * Checks if the user is allowed to unenroll from the given course.
     * Throws an AccessForbiddenException if the user is not allowed to unenroll from the course.
     * See also: {@link #getUserUnenrollmentAuthorizationForCourse(User, Course)}
     *
     * @param user   The user that wants to unenroll
     * @param course The course from which the user wants to unenroll
     */
    public void checkUserAllowedToUnenrollFromCourseElseThrow(User user, Course course) throws AccessForbiddenException {
        UnenrollmentAuthorization auth = getUserUnenrollmentAuthorizationForCourse(user, course);
        switch (auth) {
            case UNENROLLMENT_STATUS, UNENROLLMENT_PERIOD -> throw new AccessForbiddenException("The course does currently not allow unenrollment.");
            case ONLINE -> throw new AccessForbiddenException("Online courses cannot be unenrolled from.");
        }
    }

    /**
     * Convenience method: Checks if the passed user has at least the given role for the given lecture.
     * Throws an AccessForbiddenException if the user has no access which returns a 403
     *
     * @param role    the role that should be checked
     * @param lecture belongs to a course that will be checked for permission rights
     * @param user    the user whose permissions should be checked
     */
    public void checkHasAtLeastRoleForLectureElseThrow(@NotNull Role role, @NotNull Lecture lecture, @Nullable User user) {
        checkHasAtLeastRoleInCourseElseThrow(role, lecture.getCourse(), user);
    }

    /**
     * Convenience method: Checks if the passed user has at least the given role for the given exercise.
     * Throws an AccessForbiddenException if the user has no access which returns a 403
     *
     * @param role     the role that should be checked
     * @param exercise belongs to a course that will be checked for permission rights
     * @param user     the user whose permissions should be checked
     */
    public void checkHasAtLeastRoleForExerciseElseThrow(@NotNull Role role, @NotNull Exercise exercise, @Nullable User user) {
        checkHasAtLeastRoleInCourseElseThrow(role, exercise.getCourseViaExerciseGroupOrCourseMember(), user);
    }

    /**
     * Checks if the passed user has at least the given role in the given course.
     * Throws an AccessForbiddenException if the user has no access which returns a 403
     *
     * @param role   the role that should be checked
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     */
    public void checkHasAtLeastRoleInCourseElseThrow(@NotNull Role role, @NotNull Course course, @Nullable User user) {
        // Note: the consumer is necessary to get an exhaustive check for the switch expression here, also see https://stackoverflow.com/questions/66204407
        Consumer<User> consumer = switch (role) {
            case ADMIN -> this::checkIsAdminElseThrow;
            case INSTRUCTOR -> userOrNull -> checkIsAtLeastInstructorInCourseElseThrow(course, userOrNull);
            case EDITOR -> userOrNull -> checkIsAtLeastEditorInCourseElseThrow(course, userOrNull);
            case TEACHING_ASSISTANT -> userOrNull -> checkIsAtLeastTeachingAssistantInCourseElseThrow(course, userOrNull);
            case STUDENT -> userOrNull -> checkIsAtLeastStudentInCourseElseThrow(course, userOrNull);
            // anonymous users never have access to courses, so we have to throw an exception
            case ANONYMOUS -> throw new IllegalArgumentException("The role anonymous does not make sense in this context");
        };
        consumer.accept(user);
    }

    /**
     * checks if the currently logged-in user is owner of the given participation
     *
     * @param participation the participation that needs to be checked
     * @return true, if user is student is owner of this participation, otherwise false
     */
    @CheckReturnValue
    public boolean isOwnerOfParticipation(@NotNull StudentParticipation participation) {
        if (participation.getParticipant() == null) {
            return false;
        }
        else {
            return participation.isOwnedBy(SecurityUtils.getCurrentUserLogin().orElseThrow());
        }
    }

    /**
     * checks if the currently logged-in user is owner of the given participation
     *
     * @param participation the participation that needs to be checked
     * @throws AccessForbiddenException if active user isn't owner of participation
     */
    public void isOwnerOfParticipationElseThrow(@NotNull StudentParticipation participation) throws AccessForbiddenException {
        if (!isOwnerOfParticipation(participation)) {
            throw new AccessForbiddenException("participation", participation.getId());
        }
    }

    /**
     * checks if the currently logged-in user is owner of the given participation
     *
     * @param participation the participation that needs to be checked
     * @param user          the user whose permissions should be checked
     * @return true, if user is student is owner of this participation, otherwise false
     */
    @CheckReturnValue
    public boolean isOwnerOfParticipation(@NotNull StudentParticipation participation, @Nullable User user) {
        user = loadUserIfNeeded(user);
        if (participation.getParticipant() == null) {
            return false;
        }

        if (participation.getParticipant() instanceof Team team && !Hibernate.isInitialized(team.getStudents())) {
            participation.setParticipant(teamRepository.findWithStudentsByIdElseThrow(team.getId()));
        }

        return participation.isOwnedBy(user);
    }

    /**
     * checks if the currently logged-in user is owner of the given team
     *
     * @param team the team that needs to be checked
     * @param user the user whose permissions should be checked
     * @return true if user is owner of this team, otherwise false
     */
    @CheckReturnValue
    public boolean isOwnerOfTeam(@NotNull Team team, @NotNull User user) {
        return user.equals(team.getOwner());
    }

    /**
     * checks if the currently logged-in user is student of the given team
     *
     * @param course        the course to which the team belongs to (acts as scope for team short name)
     * @param teamShortName the short name of the team that needs to be checked
     * @param user          the user whose permissions should be checked
     * @return true, if user is student is owner of this team, otherwise false
     */
    @CheckReturnValue
    public boolean isStudentInTeam(@NotNull Course course, String teamShortName, @NotNull User user) {
        return userRepository.findAllInTeam(course.getId(), teamShortName).contains(user);
    }

    /**
     * checks if the passed user is allowed to see the given exercise, i.e. if the passed user is at least a student in the course
     *
     * @param exercise the exercise that needs to be checked
     * @param user     the user whose permissions should be checked
     * @return true, if user is allowed to see this exercise, otherwise false
     */
    @CheckReturnValue
    public boolean isAllowedToSeeExercise(@NotNull Exercise exercise, @Nullable User user) {
        user = loadUserIfNeeded(user);
        if (isAdmin(user)) {
            return true;
        }
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        return isAtLeastTeachingAssistantInCourse(course, user) || (isStudentInCourse(course, user) && exercise.isVisibleToStudents());
    }

    /**
     * checks if the passed user is allowed to see the given lecture
     *
     * @param lecture the lecture that needs to be checked
     * @param user    the user whose permissions should be checked
     */
    public void checkIsAllowedToSeeLectureElseThrow(@NotNull Lecture lecture, @Nullable User user) {
        user = loadUserIfNeeded(user);
        if (isAdmin(user)) {
            return;
        }
        Course course = lecture.getCourse();
        if (isAtLeastTeachingAssistantInCourse(course, user) || (isStudentInCourse(course, user) && lecture.isVisibleToStudents())) {
            return;
        }

        throw new AccessForbiddenException();
    }

    /**
     * Determines if a user is allowed to see a lecture unit
     *
     * @param lectureUnit the lectureUnit for which to check permission
     * @param user        the user for which to check permission
     * @return true if the user is allowed, false otherwise
     */
    @CheckReturnValue
    public boolean isAllowedToSeeLectureUnit(@NotNull LectureUnit lectureUnit, @Nullable User user) {
        user = loadUserIfNeeded(user);
        if (isAdmin(user)) {
            return true;
        }
        Course course = lectureUnit.getLecture().getCourse();
        return isAtLeastTeachingAssistantInCourse(course, user) || (isStudentInCourse(course, user) && lectureUnit.isVisibleToStudents());
    }

    /**
     * Checks if the currently logged-in user is allowed to retrieve the given result.
     * The user is allowed to retrieve the result if (s)he is an instructor of the course, or (s)he is at least a student in the corresponding course, the
     * submission is his/her submission, the assessment due date of the corresponding exercise is in the past (or not set) and the result is finished.
     * Instructors are allowed to retrieve the results for test runs.
     *
     * @param exercise      the corresponding exercise
     * @param participation the participation the result belongs to
     * @param result        the result that should be sent to the client
     * @return true if the user is allowed to retrieve the given result, false otherwise
     */
    @CheckReturnValue
    public boolean isUserAllowedToGetResult(Exercise exercise, StudentParticipation participation, Result result) {
        if (isAtLeastInstructorForExercise(exercise) && participation.isTestRun()) {
            return true;
        }

        return isAtLeastStudentForExercise(exercise) && (isOwnerOfParticipation(participation) || isAtLeastInstructorForExercise(exercise))
                && ExerciseDateService.isAfterAssessmentDueDate(exercise) && result.getAssessor() != null && result.getCompletionDate() != null;
    }

    /**
     * Checks if the user is allowed to see the exam result. Returns true if
     * - the current user is at least teaching assistant in the course
     * - OR if the exercise is not part of an exam
     * - OR if the exam is a test exam
     * - OR if the exam has not ended (including individual working time extensions)
     * - OR if the exam has already ended and the results were published
     *
     * @param exercise             - Exercise that the result is requested for
     * @param studentParticipation - used to retrieve the individual exam working time
     * @param user                 - User that requests the result
     * @return true if user is allowed to see the result, false otherwise
     */
    @CheckReturnValue
    public boolean isAllowedToGetExamResult(Exercise exercise, StudentParticipation studentParticipation, User user) {
        if (this.isAtLeastTeachingAssistantInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user) || exercise.isCourseExercise()) {
            return true;
        }
        Exam exam = exercise.getExamViaExerciseGroupOrCourseMember();
        if (!examDateService.isExerciseWorkingPeriodOver(exercise, studentParticipation)) {
            // students can always see their results during the exam.
            return true;
        }
        if (exam.isTestExam()) {
            // results for test exams are always visible
            return true;
        }
        return exam.resultsPublished();
    }

    /**
     * Tutors of an exercise are allowed to assess the submissions, but only instructors are allowed to assess with a specific result
     *
     * @param exercise Exercise of the submission
     * @param user     User the requests the assessment
     * @param resultId of the result the teaching assistant wants to assess
     * @return true if caller is allowed to assess submissions
     */
    @CheckReturnValue
    public boolean isAllowedToAssessExercise(Exercise exercise, User user, Long resultId) {
        return this.isAtLeastTeachingAssistantForExercise(exercise, user) && (resultId == null || isAtLeastInstructorForExercise(exercise, user));
    }

    public void checkGivenExerciseIdSameForExerciseInRequestBodyElseThrow(Long exerciseId, Exercise exerciseInRequestBody) {
        if (!exerciseId.equals(exerciseInRequestBody.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
    }

    public void checkIsAllowedToAssessExerciseElseThrow(Exercise exercise, User user, Long resultId) {
        if (!isAllowedToAssessExercise(exercise, user, resultId)) {
            throw new AccessForbiddenException("You are not allowed to assess this exercise!");
        }
    }

    private User loadUserIfNeeded(@Nullable User user) {
        if (user == null) {
            user = userRepository.getUserWithGroupsAndAuthorities();
        }
        else if (user.getGroups() == null || !Hibernate.isInitialized(user.getGroups())) {
            user = userRepository.getUserWithGroupsAndAuthorities(user.getLogin());
        }

        return user;
    }

    /**
     * Checks if the current user has at least the given role in the given course.
     *
     * @param role     the role that should be checked
     * @param courseId the id of the course that needs to be checked
     * @return true if the user has at least the role in the course, false otherwise
     */
    @CheckReturnValue
    public boolean isAtLeastRoleInCourse(Role role, long courseId) {
        return switch (role) {
            case ADMIN -> isAdmin();
            case INSTRUCTOR -> isAtLeastInstructorInCourse(courseId);
            case EDITOR -> isAtLeastEditorInCourse(courseId);
            case TEACHING_ASSISTANT -> isAtLeastTeachingAssistantInCourse(courseId);
            case STUDENT -> isAtLeastStudentInCourse(courseId);
            case ANONYMOUS -> false;
        };
    }

    public void checkIsAtLeastRoleInCourseElseThrow(Role role, long courseId) {
        if (!isAtLeastRoleInCourse(role, courseId)) {
            throw new AccessForbiddenException("Course", courseId);
        }
    }

    /**
     * Checks if the current user has at least the given role in the given exercise.
     *
     * @param role       the role that should be checked
     * @param exerciseId the id of the exercise that needs to be checked
     * @return true if the user has at least the role in the exercise, false otherwise
     */
    @CheckReturnValue
    public boolean isAtLeastRoleForExercise(Role role, long exerciseId) {
        return switch (role) {
            case ADMIN -> isAdmin();
            case INSTRUCTOR -> isAtLeastInstructorForExercise(exerciseId);
            case EDITOR -> isAtLeastEditorForExercise(exerciseId);
            case TEACHING_ASSISTANT -> isAtLeastTeachingAssistantForExercise(exerciseId);
            case STUDENT -> isAtLeastStudentForExercise(exerciseId);
            case ANONYMOUS -> false;
        };
    }

    public void checkIsAtLeastRoleForExerciseElseThrow(Role role, long exerciseId) {
        if (!isAtLeastRoleForExercise(role, exerciseId)) {
            throw new AccessForbiddenException("Exercise", exerciseId);
        }
    }
}
