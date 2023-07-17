package de.tum.in.www1.artemis.service;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * Service used to check whether user is authorized to perform actions on the entity.
 */
@Service
public class AuthorizationCheckService {

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final ExamDateService examDateService;

    // TODO: we should move this into some kind of EnrollmentService
    @Deprecated(forRemoval = true)
    @Value("${artemis.user-management.course-registration.allowed-username-pattern:#{null}}")
    private Pattern allowedCourseRegistrationUsernamePattern;

    @Value("${artemis.user-management.course-enrollment.allowed-username-pattern:#{null}}")
    private Pattern allowedCourseEnrollmentUsernamePattern;

    public AuthorizationCheckService(UserRepository userRepository, CourseRepository courseRepository, ExamDateService examDateService) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.examDateService = examDateService;

        if (allowedCourseEnrollmentUsernamePattern == null) {
            allowedCourseEnrollmentUsernamePattern = allowedCourseRegistrationUsernamePattern;
        }
    }

    /**
     * Checks if the currently logged-in user is at least an editor in the course of the given exercise.
     * The course is identified from either {@link Exercise#course(Course)} or {@link Exam#getCourse()}
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @return true if the currently logged-in user is at least an editor (also if the user is instructor or admin), false otherwise
     */
    public boolean isAtLeastEditorForExercise(@NotNull Exercise exercise) {
        return isAtLeastEditorInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), null);
    }

    /**
     * Checks if the currently logged-in user is at least an editor in the course of the given exercise.
     * The course is identified from either exercise. Course or exercise.exerciseGroup.exam.course
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @param user     the user whose permissions should be checked
     * @return true if the currently logged-in user is at least an editor, false otherwise
     */
    public boolean isAtLeastEditorForExercise(@NotNull Exercise exercise, @Nullable User user) {
        return isAtLeastEditorInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user);
    }

    /**
     * Checks if the passed user is at least an editor in the given course.
     * Throws an AccessForbiddenException if the user has no access which returns a 403
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     */
    private void checkIsAtLeastEditorInCourseElseThrow(@NotNull Course course, @Nullable User user) {
        if (!isAtLeastEditorInCourse(course, user)) {
            throw new AccessForbiddenException("Course", course.getId());
        }
    }

    /**
     * Checks if the passed user is at least an editor in the given course.
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true if the passed user is at least an editor in the course, false otherwise
     */
    public boolean isAtLeastEditorInCourse(@NotNull Course course, @Nullable User user) {
        user = loadUserIfNeeded(user);
        return isEditorInCourse(course, user) || isInstructorInCourse(course, user) || isAdmin(user);
    }

    /**
     * Given any type of exercise, the method returns if the current user is at least TA for the course the exercise belongs to. If exercise is not present, it will return false,
     * because the optional will be empty, and therefore `isPresent()` will return false This is due how `filter` works: If a value is present, apply the provided mapping function
     * to it, and if the result is non-null, return an Optional describing the result. Otherwise, return an empty Optional.
     * https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html#filter-java.util.function.Predicate
     *
     * @param exercise the exercise that needs to be checked
     * @param <T>      The type of the concrete exercise, because Exercise is an abstract class
     * @return true if the user is at least a teaching assistant (also if the user is instructor or admin) in the course of the given exercise
     */
    public <T extends Exercise> boolean isAtLeastTeachingAssistantForExercise(Optional<T> exercise) {
        return exercise.filter(this::isAtLeastTeachingAssistantForExercise).isPresent();
    }

    /**
     * Checks if the currently logged-in user is at least a teaching assistant in the course of the given exercise.
     * The course is identified from either {@link Exercise#course(Course)} or {@link Exam#getCourse()}
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @return true if the currently logged-in user is at least a teaching assistant (also if the user is instructor or admin), false otherwise
     */
    public boolean isAtLeastTeachingAssistantForExercise(@NotNull Exercise exercise) {
        return isAtLeastTeachingAssistantInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), null);
    }

    /**
     * Checks if the passed user is at least a teaching assistant in the course of the given exercise.
     * The course is identified from {@link Exercise#getCourseViaExerciseGroupOrCourseMember()}
     *
     * @param exercise the exercise that needs to be checked
     * @param user     the user whose permissions should be checked
     * @return true if the passed user is at least a teaching assistant (also if the user is instructor or admin), false otherwise
     */
    public boolean isAtLeastTeachingAssistantForExercise(@NotNull Exercise exercise, @Nullable User user) {
        user = loadUserIfNeeded(user);
        return isAtLeastTeachingAssistantInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user);
    }

    /**
     * checks if the currently logged-in user is at least a student in the course of the given exercise.
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @return true if the currently logged-in user is at least a student (also if the user is teaching assistant, instructor or admin), false otherwise
     */
    public boolean isAtLeastStudentForExercise(@NotNull Exercise exercise) {
        return isAtLeastStudentForExercise(exercise, null);
    }

    /**
     * checks if the currently logged-in user is at least a student in the course of the given exercise.
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @param user     the user whose permissions should be checked
     * @return true if the currently logged-in user is at least a student (also if the user is teaching assistant, instructor or admin), false otherwise
     */
    public boolean isAtLeastStudentForExercise(@NotNull Exercise exercise, @Nullable User user) {
        user = loadUserIfNeeded(user);
        return isStudentInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user) || isAtLeastTeachingAssistantForExercise(exercise, user);
    }

    /**
     * Checks if the passed user is at least a teaching assistant in the given course.
     * Throws an AccessForbiddenException if the user has no access which returns a 403
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     */
    private void checkIsAtLeastTeachingAssistantInCourseElseThrow(@NotNull Course course, @Nullable User user) {
        if (!isAtLeastTeachingAssistantInCourse(course, user)) {
            throw new AccessForbiddenException("Course", course.getId());
        }
    }

    /**
     * Checks if the passed user is at least a teaching assistant in the given course.
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true if the passed user is at least a teaching assistant in the course (also if the user is instructor or admin), false otherwise
     */
    public boolean isAtLeastTeachingAssistantInCourse(@NotNull Course course, @Nullable User user) {
        user = loadUserIfNeeded(user);
        return isTeachingAssistantInCourse(course, user) || isEditorInCourse(course, user) || isInstructorInCourse(course, user) || isAdmin(user);
    }

    /**
     * Checks if the passed user is at least a student in the given course.
     * Throws an AccessForbiddenException if the user has no access which returns a 403
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     */
    private void checkIsAtLeastStudentInCourseElseThrow(@NotNull Course course, @Nullable User user) {
        if (!isAtLeastStudentInCourse(course, user)) {
            throw new AccessForbiddenException("Course", course.getId());
        }
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
     * checks if the passed user is at least a teaching assistant in the given course
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true if the passed user is at least a teaching assistant in the course (also if the user is instructor or admin), false otherwise
     */
    public boolean isAtLeastStudentInCourse(@NotNull Course course, @Nullable User user) {
        user = loadUserIfNeeded(user);
        return isStudentInCourse(course, user) || isTeachingAssistantInCourse(course, user) || isEditorInCourse(course, user) || isInstructorInCourse(course, user)
                || isAdmin(user);
    }

    /**
     * Checks if the currently logged-in user is at least an instructor in the course of the given exercise.
     * The course is identified from either exercise. Course or exercise.exerciseGroup.exam.course
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @param user     the user whose permissions should be checked
     * @return true if the currently logged-in user is at least an instructor (or admin), false otherwise
     */
    public boolean isAtLeastInstructorForExercise(@NotNull Exercise exercise, @Nullable User user) {
        return isAtLeastInstructorInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user);
    }

    /**
     * checks if the currently logged-in user is at least an instructor in the course of the given exercise.
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @return true if the currently logged-in user is at least an instructor (or admin), false otherwise
     */
    public boolean isAtLeastInstructorForExercise(@NotNull Exercise exercise) {
        return isAtLeastInstructorForExercise(exercise, null);
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
     * Checks if the passed user is at least instructor in the given course.
     * Throws an AccessForbiddenException if the user has no access which returns a 403
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     */
    private void checkIsAtLeastInstructorInCourseElseThrow(@NotNull Course course, @Nullable User user) {
        if (!isAtLeastInstructorInCourse(course, user)) {
            throw new AccessForbiddenException("Course", course.getId());
        }
    }

    /**
     * checks if the passed user is at least instructor in the given course
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true if the passed user is at least instructor in the course (also if the user is admin), false otherwise
     */
    public boolean isAtLeastInstructorInCourse(@NotNull Course course, @Nullable User user) {
        user = loadUserIfNeeded(user);
        return user.getGroups().contains(course.getInstructorGroupName()) || isAdmin(user);
    }

    /**
     * checks if the passed user is instructor in the given course
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true, if user is instructor of this course, otherwise false
     */
    public boolean isInstructorInCourse(@NotNull Course course, @Nullable User user) {
        user = loadUserIfNeeded(user);
        return user.getGroups().contains(course.getInstructorGroupName());
    }

    /**
     * checks if the passed user is editor in the given course
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true, if user is an editor of this course, otherwise false
     */
    public boolean isEditorInCourse(@NotNull Course course, @Nullable User user) {
        user = loadUserIfNeeded(user);
        return user.getGroups().contains(course.getEditorGroupName());
    }

    /**
     * checks if the currently logged-in user is teaching assistant of this course
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true, if user is teaching assistant of this course, otherwise false
     */
    public boolean isTeachingAssistantInCourse(@NotNull Course course, @Nullable User user) {
        user = loadUserIfNeeded(user);
        return user.getGroups().contains(course.getTeachingAssistantGroupName());
    }

    /**
     * checks if the currently logged-in user is only a student of this course. This means the user is NOT a tutor, NOT an editor, NOT an instructor and NOT an ADMIN
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true, if user is only student of this course, otherwise false
     */
    public boolean isOnlyStudentInCourse(@NotNull Course course, @Nullable User user) {
        user = loadUserIfNeeded(user);
        return user.getGroups().contains(course.getStudentGroupName()) && !isAtLeastTeachingAssistantInCourse(course, user);
    }

    /**
     * checks if the currently logged-in user is student in the given course
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true, if user is student of this course, otherwise false
     */
    public boolean isStudentInCourse(@NotNull Course course, @Nullable User user) {
        user = loadUserIfNeeded(user);
        return user.getGroups().contains(course.getStudentGroupName());
    }

    /**
     * checks if the currently logged-in user is owner of the given participation
     *
     * @param participation the participation that needs to be checked
     * @return true, if user is student is owner of this participation, otherwise false
     */
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
    public boolean isOwnerOfParticipation(@NotNull StudentParticipation participation, @Nullable User user) {
        user = loadUserIfNeeded(user);
        if (participation.getParticipant() == null) {
            return false;
        }
        else {
            return participation.isOwnedBy(user);
        }
    }

    /**
     * checks if the currently logged-in user is owner of the given team
     *
     * @param team the team that needs to be checked
     * @param user the user whose permissions should be checked
     * @return true if user is owner of this team, otherwise false
     */
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
    public boolean isAllowedToSeeLectureUnit(@NotNull LectureUnit lectureUnit, @Nullable User user) {
        user = loadUserIfNeeded(user);
        if (isAdmin(user)) {
            return true;
        }
        Course course = lectureUnit.getLecture().getCourse();
        return isAtLeastTeachingAssistantInCourse(course, user) || (isStudentInCourse(course, user) && lectureUnit.isVisibleToStudents());
    }

    /**
     * NOTE: this method should only be used in a REST Call context, when the SecurityContext is correctly setup.
     * Preferably use the method isAdmin(user) below
     * <p>
     * Checks if the currently logged-in user is an admin user
     *
     * @return true, if user is admin, otherwise false
     */
    public boolean isAdmin() {
        return SecurityUtils.isCurrentUserInRole(Role.ADMIN.getAuthority());
    }

    /**
     * Checks if the passed user is an admin user
     *
     * @param user the user with authorities. If the user is null, the currently logged-in user will be used.
     * @return true, if user is admin, otherwise false
     */
    public boolean isAdmin(@Nullable User user) {
        if (user == null) {
            return isAdmin();
        }
        return user.getAuthorities().contains(Authority.ADMIN_AUTHORITY);
    }

    /**
     * Checks if the passed user is an admin user. Throws an AccessForbiddenException in case the user is not an admin
     *
     * @param user the user with authorities. If the user is null, the currently logged-in user will be used.
     **/
    public void checkIsAdminElseThrow(@Nullable User user) {
        if (!isAdmin(user)) {
            throw new AccessForbiddenException();
        }
    }

    /**
     * Checks if the currently logged-in user is allowed to retrieve the given result.
     * The user is allowed to retrieve the result if (s)he is an instructor of the course, or (s)he is at least a student in the corresponding course, the
     * submission is his/her submission, the assessment due date of the corresponding exercise is in the past (or not set) and the result is finished.
     *
     * @param exercise      the corresponding exercise
     * @param participation the participation the result belongs to
     * @param result        the result that should be sent to the client
     * @return true if the user is allowed to retrieve the given result, false otherwise
     */
    public boolean isUserAllowedToGetResult(Exercise exercise, StudentParticipation participation, Result result) {
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

}
