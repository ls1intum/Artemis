package de.tum.cit.aet.artemis.course.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.course.service.CourseServiceUtil.removeUserVariables;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.user.UserService;
import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.atlas.api.LearningPathApi;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.dto.CourseRoleMemberDTO;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.core.dto.UserForRegistrationDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.EnrollmentService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.localvc.service.RepositoryVcsAccessTokenService;

/**
 * Service for managing course access, including enrollment and unenrollment of users.
 * Membership is tracked via the {@code user_course_role} table.
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

    private final UserRepository userRepository;

    private final UserCourseRoleRepository userCourseRoleRepository;

    private final Optional<LearnerProfileApi> learnerProfileApi;

    private final AuditEventRepository auditEventRepository;

    private final Optional<LearningPathApi> learningPathApi;

    private final RepositoryVcsAccessTokenService repositoryVcsAccessTokenService;

    public CourseAccessService(AuthorizationCheckService authCheckService, EnrollmentService enrollmentService, CourseRepository courseRepository, UserService userService,
            UserRepository userRepository, UserCourseRoleRepository userCourseRoleRepository, Optional<LearnerProfileApi> learnerProfileApi,
            AuditEventRepository auditEventRepository, Optional<LearningPathApi> learningPathApi, RepositoryVcsAccessTokenService repositoryVcsAccessTokenService) {
        this.authCheckService = authCheckService;
        this.enrollmentService = enrollmentService;
        this.courseRepository = courseRepository;
        this.userService = userService;
        this.userRepository = userRepository;
        this.userCourseRoleRepository = userCourseRoleRepository;
        this.learnerProfileApi = learnerProfileApi;
        this.auditEventRepository = auditEventRepository;
        this.learningPathApi = learningPathApi;
        this.repositoryVcsAccessTokenService = repositoryVcsAccessTokenService;
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
        List<User> foundUsers = new ArrayList<>();
        for (var studentDto : studentDTOs) {
            var optionalStudent = userService.findUser(studentDto.registrationNumber(), studentDto.login(), studentDto.email());
            if (optionalStudent.isEmpty()) {
                notFoundStudentsDTOs.add(studentDto);
            }
            else {
                foundUsers.add(optionalStudent.get());
            }
        }

        // Batch-enroll all found users in a single round trip instead of one existsBy query + insert per user.
        userService.addUsersToCourse(foundUsers, course, courseRole);

        if (courseRole == CourseRole.STUDENT && course.getLearningPathsEnabled()) {
            final Course finalCourse = course;
            foundUsers.forEach(user -> {
                learnerProfileApi.ifPresent(api -> api.createCourseLearnerProfile(finalCourse, user));
                learningPathApi.ifPresent(api -> api.generateLearningPathForUser(finalCourse, user));
            });
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
        Set<User> usersInGroup = userCourseRoleRepository.findUsersByCourse_IdAndRole(course.getId(), role);
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
        // Pre-provision repository-scoped VCS access tokens for the staff member across all base repositories of the course's programming exercises. Run asynchronously so adding a
        // staff member to a course with many programming exercises does not block the request; the clone-dialog lazy fallback covers the brief window before the tokens exist.
        if (isStaffRole(role)) {
            repositoryVcsAccessTokenService.ensureTokensForStaffUserInCourseAsync(user, course);
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
        // Remove the staff member's repository-scoped VCS access tokens for this course, unless they still hold another staff role in it. Run asynchronously so the request does
        // not block; a token that lingers briefly is harmless because authorization is re-checked on every git operation.
        if (isStaffRole(role)) {
            repositoryVcsAccessTokenService.deleteForUserInCourseIfNoLongerStaffAsync(user, course);
        }
    }

    private boolean isStaffRole(CourseRole role) {
        return role == CourseRole.TEACHING_ASSISTANT || role == CourseRole.EDITOR || role == CourseRole.INSTRUCTOR;
    }

    /**
     * Returns a page of users in the given course that have the given role, matching the search term and sort from {@code search}.
     *
     * @param courseId the id of the course
     * @param role     the course role to query for
     * @param search   pagination, search term, and sort info
     * @return page of matching users
     */
    @NonNull
    public Page<CourseRoleMemberDTO> getPagedUsersInCourseRole(long courseId, CourseRole role, SearchTermPageableSearchDTO<String> search) {
        Page<User> page = userRepository.searchUsersInCourseRole(search, courseId, role);
        List<CourseRoleMemberDTO> members = page.getContent().stream().map(user -> {
            user.setVisibleRegistrationNumber(user.getRegistrationNumber());
            return new CourseRoleMemberDTO(user);
        }).toList();
        return new PageImpl<>(members, page.getPageable(), page.getTotalElements());
    }

    /**
     * Searches all Artemis users by login, full name, email, or registration number,
     * and marks each result as already registered in the given course role.
     *
     * @param courseId   the course to check existing registrations against
     * @param role       the course role to check
     * @param searchTerm the text entered by the instructor
     * @param page       zero-based page index
     * @param size       number of results per page
     * @return a page of {@link UserForRegistrationDTO} with {@code isRegistered} set appropriately
     */
    public Page<UserForRegistrationDTO> searchUsersForCourseRole(long courseId, CourseRole role, String searchTerm, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<User> users = userRepository.searchAllByLoginOrNameOrEmailOrRegistrationNumber(pageable, searchTerm);
        List<Long> userIds = users.getContent().stream().map(User::getId).toList();
        Set<Long> registeredIds = userIds.isEmpty() ? Set.of() : userCourseRoleRepository.findUserIdsByCourseIdAndRoleAndUserIdIn(courseId, role, userIds);
        List<UserForRegistrationDTO> dtos = users.getContent().stream().map(
                u -> new UserForRegistrationDTO(u.getId(), u.getLogin(), u.getName(), u.getEmail(), u.getRegistrationNumber(), u.getImageUrl(), registeredIds.contains(u.getId())))
                .toList();
        return new PageImpl<>(dtos, pageable, users.getTotalElements());
    }
}
