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
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.atlas.api.LearningPathApi;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.user.UserService;

/**
 * Service for managing course access, including enrollment and unenrollment of users.
 * This service provides methods to enroll and unenroll users in courses, as well as to manage user groups within courses.
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseAccessService {

    private static final Logger log = LoggerFactory.getLogger(CourseAccessService.class);

    private final AuthorizationCheckService authCheckService;

    private final CourseRepository courseRepository;

    private final UserService userService;

    private final Optional<LearnerProfileApi> learnerProfileApi;

    private final AuditEventRepository auditEventRepository;

    private final Optional<LearningPathApi> learningPathApi;

    private final UserRepository userRepository;

    public CourseAccessService(AuthorizationCheckService authCheckService, CourseRepository courseRepository, UserService userService,
            Optional<LearnerProfileApi> learnerProfileApi, AuditEventRepository auditEventRepository, Optional<LearningPathApi> learningPathApi, UserRepository userRepository) {
        this.authCheckService = authCheckService;
        this.courseRepository = courseRepository;
        this.userService = userService;
        this.learnerProfileApi = learnerProfileApi;
        this.auditEventRepository = auditEventRepository;
        this.learningPathApi = learningPathApi;
        this.userRepository = userRepository;
    }

    /**
     * Gets all courses that the specified user can enroll in.
     *
     * @param user the user entity
     * @return unmodifiable set of courses the student can enroll in
     */
    public Set<Course> findAllEnrollableForUser(User user) {
        return courseRepository.findAllEnrollmentActiveWithOrganizationsAndPrerequisites(ZonedDateTime.now()).stream()
                .filter(course -> !user.getGroups().contains(course.getStudentGroupName())).collect(Collectors.toSet());
    }

    /**
     * Unenroll a user from a course by removing them from the student group of the course
     *
     * @param user   The user that should get removed from the course
     * @param course The course from which the user should be removed from
     */
    public void unenrollUserForCourseOrThrow(User user, Course course) {
        authCheckService.checkUserAllowedToUnenrollFromCourseElseThrow(course);
        userService.removeUserFromGroup(user, course.getStudentGroupName());
        learnerProfileApi.ifPresent(api -> api.deleteCourseLearnerProfile(course, user));
        final var auditEvent = new AuditEvent(user.getLogin(), Constants.UNENROLL_FROM_COURSE, "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} has successfully unenrolled from course {}", user.getLogin(), course.getTitle());
    }

    /**
     * Enrolls a user in a course by adding them to the student group of the course
     *
     * @param user   The user that should get added to the course
     * @param course The course to which the user should get added to
     */
    public void enrollUserForCourseOrThrow(User user, Course course) {
        authCheckService.checkUserAllowedToEnrollInCourseElseThrow(user, course);
        userService.addUserToGroup(user, course.getStudentGroupName());
        if (course.getLearningPathsEnabled()) {
            learnerProfileApi.ifPresent(api -> api.createCourseLearnerProfile(course, user));
            learningPathApi.ifPresent(api -> api.generateLearningPathForUser(course, user));
        }
        final var auditEvent = new AuditEvent(user.getLogin(), Constants.ENROLL_IN_COURSE, "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} has successfully enrolled in course {}", user.getLogin(), course.getTitle());
    }

    /**
     * Add multiple users to the course so that they can access it
     * The passed list of UserDTOs must include at least one unique user identifier (i.e. registration number OR email OR login)
     * <p>
     * This method first tries to find the user in the internal Artemis user database (because the user is probably already using Artemis).
     * In case the user cannot be found, it additionally searches the connected LDAP in case it is configured.
     *
     * @param courseId    the id of the course
     * @param studentDTOs the list of students (with at least registration number)
     * @param courseGroup the group the students should be added to
     * @return the list of students who could not be enrolled in the course, because they could NOT be found in the Artemis database and could NOT be found in the TUM LDAP
     */
    public List<StudentDTO> registerUsersForCourseGroup(Long courseId, List<StudentDTO> studentDTOs, String courseGroup) {
        var course = courseRepository.findByIdElseThrow(courseId);
        if (course.getLearningPathsEnabled()) {
            course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(course.getId());
        }
        String courseGroupName = course.defineCourseGroupName(courseGroup);
        Role courseGroupRole = Role.fromString(courseGroup);
        List<StudentDTO> notFoundStudentsDTOs = new ArrayList<>();
        for (var studentDto : studentDTOs) {
            var optionalStudent = userService.findUserAndAddToCourse(studentDto.registrationNumber(), studentDto.login(), studentDto.email(), courseGroupName);
            if (optionalStudent.isEmpty()) {
                notFoundStudentsDTOs.add(studentDto);
            }
            else if (courseGroupRole == Role.STUDENT && course.getLearningPathsEnabled()) {
                final Course finalCourse = course;
                learnerProfileApi.ifPresent(api -> api.createCourseLearnerProfile(finalCourse, optionalStudent.get()));
                learningPathApi.ifPresent(api -> api.generateLearningPathForUser(finalCourse, optionalStudent.get()));
            }
        }

        return notFoundStudentsDTOs;
    }

    /**
     * Returns all users in a course that belong to the given group
     *
     * @param course    the course
     * @param groupName the name of the group
     * @return list of users
     */
    @NonNull
    public ResponseEntity<Set<User>> getAllUsersInGroup(Course course, String groupName) {
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        var usersInGroup = userRepository.findAllByDeletedIsFalseAndGroupsContains(groupName);
        usersInGroup.forEach(user -> {
            // explicitly set the registration number
            user.setVisibleRegistrationNumber(user.getRegistrationNumber());
        });
        removeUserVariables(usersInGroup);
        return ResponseEntity.ok().body(usersInGroup);
    }

    /**
     * adds a given user to a user group
     *
     * @param user   user to be added to a group
     * @param group  user-group where the user should be added
     * @param course the course in which the user should be added
     */
    public void addUserToGroup(User user, String group, Course course) {
        userService.addUserToGroup(user, group);
        if (group.equals(course.getStudentGroupName()) && course.getLearningPathsEnabled()) {
            Course courseWithCompetencies = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(course.getId());
            learnerProfileApi.ifPresent(api -> api.createCourseLearnerProfile(course, user));
            learningPathApi.ifPresent(api -> api.generateLearningPathForUser(courseWithCompetencies, user));
        }
    }

    /**
     * removes a given user to a user group
     *
     * @param user  user to be removed from a group
     * @param group user-group where the user should be removed
     */
    public void removeUserFromGroup(User user, String group) {
        userService.removeUserFromGroup(user, group);
    }

    /**
     * If the corresponding group (student, tutor, editor, instructor) is not defined, this method will set the default group.
     *
     * @param course the course (typically created on the client and not yet existing) for which the groups should be validated
     */
    public void setDefaultGroupsIfNotSet(Course course) {
        if (!StringUtils.hasText(course.getStudentGroupName())) {
            course.setStudentGroupName(course.getDefaultStudentGroupName());
        }

        if (!StringUtils.hasText(course.getTeachingAssistantGroupName())) {
            course.setTeachingAssistantGroupName(course.getDefaultTeachingAssistantGroupName());
        }

        if (!StringUtils.hasText(course.getEditorGroupName())) {
            course.setEditorGroupName(course.getDefaultEditorGroupName());
        }

        if (!StringUtils.hasText(course.getInstructorGroupName())) {
            course.setInstructorGroupName(course.getDefaultInstructorGroupName());
        }
    }

    /**
     * Special case for editors: checks if the default editor group needs to be created when old courses are edited
     *
     * @param course the course for which the default editor group will be created if it does not exist
     */
    public void checkIfEditorGroupsNeedsToBeCreated(Course course) {
        // Courses that have been created before Artemis version 4.11.9 do not have an editor group.
        // The editor group would be need to be set manually by instructors for the course and manually added to external user management.
        // To increase the usability the group is automatically generated when a user is added.
        if (!StringUtils.hasText(course.getEditorGroupName())) {
            course.setEditorGroupName(course.getDefaultEditorGroupName());
            courseRepository.save(course);
        }
    }
}
