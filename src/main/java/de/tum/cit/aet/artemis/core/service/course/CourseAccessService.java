package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.service.course.CourseServiceUtil.removeUserVariables;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.atlas.api.LearningPathApi;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.EnrollmentService;
import de.tum.cit.aet.artemis.core.service.user.UserService;

/**
 * Service for managing course access, including enrollment and unenrollment of users.
 * Membership is tracked via the {@code user_course_role} table (authoritative) with a dual-write
 * to the legacy {@code user_groups} table until Phase 9 removes it.
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseAccessService {

    private static final Logger log = LoggerFactory.getLogger(CourseAccessService.class);

    private final AuthorizationCheckService authCheckService;

    private final EnrollmentService enrollmentService;

    private final CourseRepository courseRepository;

    private final UserService userService;

    private final UserCourseRoleRepository userCourseRoleRepository;

    private final Optional<LearnerProfileApi> learnerProfileApi;

    private final AuditEventRepository auditEventRepository;

    private final Optional<LearningPathApi> learningPathApi;

    public CourseAccessService(AuthorizationCheckService authCheckService, EnrollmentService enrollmentService, CourseRepository courseRepository, UserService userService,
            UserCourseRoleRepository userCourseRoleRepository, Optional<LearnerProfileApi> learnerProfileApi, AuditEventRepository auditEventRepository,
            Optional<LearningPathApi> learningPathApi) {
        this.authCheckService = authCheckService;
        this.enrollmentService = enrollmentService;
        this.courseRepository = courseRepository;
        this.userService = userService;
        this.userCourseRoleRepository = userCourseRoleRepository;
        this.learnerProfileApi = learnerProfileApi;
        this.auditEventRepository = auditEventRepository;
        this.learningPathApi = learningPathApi;
    }

    /**
     * Gets all courses that the specified user can enroll in.
     *
     * @param user the user entity
     * @return unmodifiable set of courses the student can enroll in
     */
    public Set<Course> findAllEnrollableForUser(User user) {
        return courseRepository.findAllEnrollmentActiveWithOrganizationsAndPrerequisites(ZonedDateTime.now()).stream()
                .filter(course -> !authCheckService.isStudentInCourse(course, user)).collect(Collectors.toSet());
    }

    /**
     * Unenroll a user from a course by removing their STUDENT role.
     *
     * @param user   The user that should get removed from the course
     * @param course The course from which the user should be removed from
     */
    public void unenrollUserForCourseOrThrow(User user, Course course) {
        enrollmentService.checkUserAllowedToUnenrollFromCourseElseThrow(course);
        userService.removeUserFromCourse(user, course, CourseRole.STUDENT);
        learnerProfileApi.ifPresent(api -> api.deleteCourseLearnerProfile(course, user));
        final var auditEvent = new AuditEvent(user.getLogin(), Constants.UNENROLL_FROM_COURSE, "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} has successfully unenrolled from course {}", user.getLogin(), course.getTitle());
    }

    /**
     * Enrolls a user in a course by granting them the STUDENT role.
     *
     * @param user   The user that should get added to the course
     * @param course The course to which the user should get added to
     */
    public void enrollUserForCourseOrThrow(User user, Course course) {
        enrollmentService.checkUserAllowedToEnrollInCourseElseThrow(user, course);
        userService.addUserToCourse(user, course, CourseRole.STUDENT);
        if (course.getLearningPathsEnabled()) {
            learnerProfileApi.ifPresent(api -> api.createCourseLearnerProfile(course, user));
            learningPathApi.ifPresent(api -> api.generateLearningPathForUser(course, user));
        }
        final var auditEvent = new AuditEvent(user.getLogin(), Constants.ENROLL_IN_COURSE, "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} has successfully enrolled in course {}", user.getLogin(), course.getTitle());
    }

    /**
     * Add multiple users to the course with the role derived from the given role string.
     * The passed list of UserDTOs must include at least one unique user identifier (i.e. registration number OR email OR login).
     *
     * @param courseId       the id of the course
     * @param studentDTOs    the list of users (with at least one unique identifier)
     * @param courseRoleSlug the role path segment from the REST URL ('students', 'tutors', 'editors', 'instructors'), converted to {@link CourseRole} internally
     * @return the list of users who could not be registered because they were not found in the Artemis database
     */
    public List<StudentDTO> registerUsersForCourse(Long courseId, List<StudentDTO> studentDTOs, String courseRoleSlug) {
        var course = courseRepository.findByIdElseThrow(courseId);
        if (course.getLearningPathsEnabled()) {
            course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(course.getId());
        }
        CourseRole courseRole = CourseRole.fromRole(Role.fromString(courseRoleSlug));
        List<StudentDTO> notFoundStudentsDTOs = new ArrayList<>();
        for (var studentDto : studentDTOs) {
            var optionalStudent = userService.findUserAndAddToCourse(studentDto.registrationNumber(), studentDto.login(), studentDto.email(), course, courseRole);
            if (optionalStudent.isEmpty()) {
                notFoundStudentsDTOs.add(studentDto);
            }
            else if (courseRole == CourseRole.STUDENT && course.getLearningPathsEnabled()) {
                final Course finalCourse = course;
                learnerProfileApi.ifPresent(api -> api.createCourseLearnerProfile(finalCourse, optionalStudent.get()));
                learningPathApi.ifPresent(api -> api.generateLearningPathForUser(finalCourse, optionalStudent.get()));
            }
        }

        return notFoundStudentsDTOs;
    }

    /**
     * Returns all users in a course that have the given role.
     *
     * @param course the course
     * @param role   the course role to query for
     * @return response containing the set of users with that role
     */
    @NonNull
    public ResponseEntity<Set<User>> getUsersWithRole(Course course, CourseRole role) {
        var courseRoles = userCourseRoleRepository.findByCourse_IdAndRole(course.getId(), role);
        Set<User> usersInGroup = courseRoles.stream().map(UserCourseRole::getUser).collect(Collectors.toSet());
        usersInGroup.forEach(user -> user.setVisibleRegistrationNumber(user.getRegistrationNumber()));
        removeUserVariables(usersInGroup);
        return ResponseEntity.ok().body(usersInGroup);
    }

    /**
     * Adds a user to the course with the given role and handles learning path creation for students.
     *
     * @param user   user to be added
     * @param course the course
     * @param role   the role to grant the user
     */
    public void addUserToCourse(User user, Course course, CourseRole role) {
        userService.addUserToCourse(user, course, role);
        if (role == CourseRole.STUDENT && course.getLearningPathsEnabled()) {
            Course courseWithCompetencies = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(course.getId());
            learnerProfileApi.ifPresent(api -> api.createCourseLearnerProfile(course, user));
            learningPathApi.ifPresent(api -> api.generateLearningPathForUser(courseWithCompetencies, user));
        }
    }

    /**
     * Removes a user from the course role.
     *
     * @param user   user to be removed
     * @param course the course
     * @param role   the role to revoke
     */
    public void removeUserFromCourse(User user, Course course, CourseRole role) {
        userService.removeUserFromCourse(user, course, role);
    }

}
