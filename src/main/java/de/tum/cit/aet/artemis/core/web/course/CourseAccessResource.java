package de.tum.cit.aet.artemis.core.web.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.assessment.dto.UserNameAndLoginDTO;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.core.dto.UserDTO;
import de.tum.cit.aet.artemis.core.dto.UserPublicInfoDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.course.CourseAccessService;
import de.tum.cit.aet.artemis.core.service.course.CourseSearchService;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing access to courses and searching members in courses.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/core/")
@Lazy
public class CourseAccessResource {

    private static final Logger log = LoggerFactory.getLogger(CourseAccessResource.class);

    private final UserRepository userRepository;

    private final CourseAccessService courseAccessService;

    private final AuthorizationCheckService authCheckService;

    private final CourseRepository courseRepository;

    private final CourseSearchService courseSearchService;

    public CourseAccessResource(CourseAccessService courseAccessService, CourseRepository courseRepository, AuthorizationCheckService authCheckService,
            UserRepository userRepository, CourseSearchService courseSearchService) {
        this.courseAccessService = courseAccessService;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.courseSearchService = courseSearchService;
    }

    /**
     * POST /courses/{courseId}/enroll : Enroll in an existing course. This method enrolls the current user for the given course id in case the course has already started
     * and not finished yet. The user is added to the course student group in the Authentication System and the course student group is added to the user's groups in the Artemis
     * database.
     *
     * @param courseId to find the course
     * @return response entity for groups of user who has been enrolled in the course
     */
    @PostMapping("courses/{courseId}/enroll")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<String>> enrollInCourse(@PathVariable Long courseId) {
        User user = userRepository.getUserWithGroupsAndAuthoritiesAndOrganizations();
        Course course = courseRepository.findWithEagerOrganizationsAndCompetenciesAndPrerequisitesAndLearningPathsElseThrow(courseId);
        log.debug("REST request to enroll {} in Course {}", user.getName(), course.getTitle());
        courseAccessService.enrollUserForCourseOrThrow(user, course);
        return ResponseEntity.ok(user.getGroups());
    }

    /**
     * POST /courses/{courseId}/unenroll : Unenroll from an existing course. This method unenrolls the current user for the given course id in case the student is currently
     * enrolled.
     * The user is removed from the course student group in the Authentication System and the course student group is removed from the user's groups in the Artemis
     * database.
     *
     * @param courseId to find the course
     * @return response entity for groups of user who has been unenrolled from the course
     */
    @PostMapping("courses/{courseId}/unenroll")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<String>> unenrollFromCourse(@PathVariable Long courseId) {
        Course course = courseRepository.findWithEagerOrganizationsElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthoritiesAndOrganizations();
        log.debug("REST request to unenroll {} for Course {}", user.getName(), course.getTitle());
        courseAccessService.unenrollUserForCourseOrThrow(user, course);
        return ResponseEntity.ok(user.getGroups());
    }

    /**
     * GET /courses/{courseId}/for-enrollment : get a course by id if the course allows enrollment and is currently active.
     *
     * @param courseId the id of the course to retrieve
     * @return the active course
     */
    @GetMapping("courses/{courseId}/for-enrollment")
    @EnforceAtLeastStudent
    public ResponseEntity<Course> getCourseForEnrollment(@PathVariable long courseId) {
        log.debug("REST request to get a currently active course for enrollment");
        User user = userRepository.getUserWithGroupsAndAuthoritiesAndOrganizations();

        Course course = courseRepository.findSingleWithOrganizationsAndPrerequisitesElseThrow(courseId);
        authCheckService.checkUserAllowedToEnrollInCourseElseThrow(user, course);

        return ResponseEntity.ok(course);
    }

    /**
     * GET /courses/for-enrollment : get all courses that the current user can enroll in.
     * Decided by the start and end date and if the enrollmentEnabled flag is set correctly
     *
     * @return the ResponseEntity with status 200 (OK) and with body the list of courses which are active
     */
    @GetMapping("courses/for-enrollment")
    @EnforceAtLeastStudent
    public ResponseEntity<List<Course>> getCoursesForEnrollment() {
        log.debug("REST request to get all currently active courses that are not online courses");
        User user = userRepository.getUserWithGroupsAndAuthoritiesAndOrganizations();
        final var courses = courseAccessService.findAllEnrollableForUser(user).stream().filter(course -> authCheckService.isUserAllowedToSelfEnrollInCourse(user, course)).toList();
        return ResponseEntity.ok(courses);
    }

    /**
     * GET /courses/:courseId/students : Returns all users that belong to the student group of the course
     *
     * @param courseId the id of the course
     * @return list of users with status 200 (OK)
     */
    @GetMapping("courses/{courseId}/students")
    @EnforceAtLeastInstructor
    public ResponseEntity<Set<User>> getStudentsInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all students in course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        return courseAccessService.getAllUsersInGroup(course, course.getStudentGroupName());
    }

    /**
     * GET /courses/:courseId/students/search : Search all users by login or name that belong to the student group of the course
     *
     * @param courseId    the id of the course
     * @param loginOrName the login or name by which to search users
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("courses/{courseId}/students/search")
    @EnforceAtLeastTutor
    public ResponseEntity<List<UserDTO>> searchStudentsInCourse(@PathVariable Long courseId, @RequestParam("loginOrName") String loginOrName) {
        log.debug("REST request to search for students in course : {} with login or name : {}", courseId, loginOrName);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        // restrict result size by only allowing reasonable searches
        if (loginOrName.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'loginOrName' must be three characters or longer.");
        }
        final Page<UserDTO> page = userRepository.searchAllUsersByLoginOrNameInGroupAndConvertToDTO(PageRequest.of(0, 25), loginOrName, course.getStudentGroupName());
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET /courses/:courseId/users/search : Search all users by login or name that belong to the specified groups of the course
     *
     * @param courseId    the id of the course
     * @param loginOrName the login or name by which to search users
     * @param roles       the roles which should be searched in
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("courses/{courseId}/users/search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<UserPublicInfoDTO>> searchUsersInCourse(@PathVariable Long courseId, @RequestParam("loginOrName") String loginOrName,
            @RequestParam("roles") List<String> roles) {
        log.debug("REST request to search users in course : {} with login or name : {}", courseId, loginOrName);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        var requestedRoles = roles.stream().map(Role::fromString).collect(Collectors.toSet());
        // restrict result size by only allowing reasonable searches if student role is selected
        if (loginOrName.length() < 3 && requestedRoles.contains(Role.STUDENT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'loginOrName' must be three characters or longer if you search for students.");
        }
        final var relevantCourseGroupNames = getRelevantCourseGroupNames(requestedRoles, course);
        User searchingUser = userRepository.getUser();
        var originalPage = userRepository.searchAllWithGroupsByLoginOrNameInGroupsNotUserId(PageRequest.of(0, 25), loginOrName, relevantCourseGroupNames, searchingUser.getId());

        var resultDTOs = new ArrayList<UserPublicInfoDTO>();
        for (var user : originalPage) {
            var dto = new UserPublicInfoDTO(user);
            UserPublicInfoDTO.assignRoleProperties(course, user, dto);
            if (!resultDTOs.contains(dto)) {
                resultDTOs.add(dto);
            }
        }
        var dtoPage = new PageImpl<>(resultDTOs, originalPage.getPageable(), originalPage.getTotalElements());
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), dtoPage);
        return new ResponseEntity<>(dtoPage.getContent(), headers, HttpStatus.OK);
    }

    private static HashSet<String> getRelevantCourseGroupNames(Set<Role> requestedRoles, Course course) {
        var groups = new HashSet<String>();
        if (requestedRoles.contains(Role.STUDENT)) {
            groups.add(course.getStudentGroupName());
        }
        if (requestedRoles.contains(Role.TEACHING_ASSISTANT)) {
            groups.add(course.getTeachingAssistantGroupName());
            // searching for tutors also searches for editors
            groups.add(course.getEditorGroupName());
        }
        if (requestedRoles.contains(Role.INSTRUCTOR)) {
            groups.add(course.getInstructorGroupName());
        }
        return groups;
    }

    /**
     * GET /courses/:courseId/tutors : Returns all users that belong to the tutor group of the course
     *
     * @param courseId the id of the course
     * @return list of users with status 200 (OK)
     */
    @GetMapping("courses/{courseId}/tutors")
    @EnforceAtLeastInstructor
    public ResponseEntity<Set<User>> getTutorsInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all tutors in course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        return courseAccessService.getAllUsersInGroup(course, course.getTeachingAssistantGroupName());
    }

    /**
     * GET /courses/:courseId/editors : Returns all users that belong to the editor group of the course
     *
     * @param courseId the id of the course
     * @return list of users with status 200 (OK)
     */
    @GetMapping("courses/{courseId}/editors")
    @EnforceAtLeastInstructor
    public ResponseEntity<Set<User>> getEditorsInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all editors in course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        return courseAccessService.getAllUsersInGroup(course, course.getEditorGroupName());
    }

    /**
     * GET /courses/:courseId/instructors : Returns all users that belong to the instructor group of the course
     *
     * @param courseId the id of the course
     * @return list of users with status 200 (OK)
     */
    @GetMapping("courses/{courseId}/instructors")
    @EnforceAtLeastInstructor
    public ResponseEntity<Set<User>> getInstructorsInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all instructors in course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        return courseAccessService.getAllUsersInGroup(course, course.getInstructorGroupName());
    }

    /**
     * GET /courses/:courseId/search-other-users : search users for a given course within all groups.
     *
     * @param courseId   the id of the course for which to search users
     * @param nameOfUser the name by which to search users
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("courses/{courseId}/search-other-users")
    @EnforceAtLeastStudent
    public ResponseEntity<List<User>> searchOtherUsersInCourse(@PathVariable long courseId, @RequestParam("nameOfUser") String nameOfUser) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);

        // restrict result size by only allowing reasonable searches
        if (nameOfUser.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'name' must be three characters or longer.");
        }

        return ResponseEntity.ok().body(courseSearchService.searchOtherUsersNameInCourse(course, nameOfUser));
    }

    /**
     * GET /courses/:courseId/members/search: Searches for members of a course
     *
     * @param courseId    id of the course
     * @param loginOrName the search term to search login and names by
     * @return the ResponseEntity with status 200 (OK) and with body containing the list of found members matching the criteria
     */
    @GetMapping("courses/{courseId}/members/search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<UserNameAndLoginDTO>> searchMembersOfCourse(@PathVariable Long courseId, @RequestParam("loginOrName") String loginOrName) {
        log.debug("REST request to get members with login or name : {} in course: {}", loginOrName, courseId);

        var course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);

        var searchTerm = loginOrName != null ? loginOrName.toLowerCase().trim() : "";
        List<UserNameAndLoginDTO> searchResults = userRepository.searchAllWithGroupsByLoginOrNameInCourseAndReturnList(Pageable.ofSize(10), searchTerm, course.getId()).stream()
                .map(UserNameAndLoginDTO::of).toList();

        return ResponseEntity.ok().body(searchResults);
    }

    /**
     * Post /courses/:courseId/students/:studentLogin : Add the given user to the students of the course so that the student can access the course
     *
     * @param courseId     the id of the course
     * @param studentLogin the login of the user who should get student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping("courses/{courseId}/students/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> addStudentToCourse(@PathVariable Long courseId, @PathVariable String studentLogin) {
        log.debug("REST request to add {} as student to course : {}", studentLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return addUserToCourseGroup(studentLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getStudentGroupName());
    }

    /**
     * Post /courses/:courseId/tutors/:tutorLogin : Add the given user to the tutors of the course so that the student can access the course administration
     *
     * @param courseId   the id of the course
     * @param tutorLogin the login of the user who should get tutor access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping("courses/{courseId}/tutors/{tutorLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> addTutorToCourse(@PathVariable Long courseId, @PathVariable String tutorLogin) {
        log.debug("REST request to add {} as tutors to course : {}", tutorLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return addUserToCourseGroup(tutorLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getTeachingAssistantGroupName());
    }

    /**
     * Post /courses/:courseId/editors/:editorLogin : Add the given user to the editors of the course so that the student can access the course administration
     *
     * @param courseId    the id of the course
     * @param editorLogin the login of the user who should get editor access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping("courses/{courseId}/editors/{editorLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> addEditorToCourse(@PathVariable Long courseId, @PathVariable String editorLogin) {
        log.debug("REST request to add {} as editors to course : {}", editorLogin, courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        courseAccessService.checkIfEditorGroupsNeedsToBeCreated(course);
        return addUserToCourseGroup(editorLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getEditorGroupName());
    }

    /**
     * Post /courses/:courseId/instructors/:instructorLogin : Add the given user to the instructors of the course so that the student can access the course administration
     *
     * @param courseId        the id of the course
     * @param instructorLogin the login of the user who should get instructors access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping("courses/{courseId}/instructors/{instructorLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> addInstructorToCourse(@PathVariable Long courseId, @PathVariable String instructorLogin) {
        log.debug("REST request to add {} as instructors to course : {}", instructorLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return addUserToCourseGroup(instructorLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getInstructorGroupName());
    }

    /**
     * adds the userLogin to the group (student, tutors or instructors) of the given course
     *
     * @param userLogin         the user login of the student, tutor or instructor who should be added to the group
     * @param instructorOrAdmin the user who initiates this request who must be an instructor of the given course or an admin
     * @param course            the course which is only passes to check if the instructorOrAdmin is an instructor of the course
     * @param group             the group to which the userLogin should be added
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found) or with status 403 (Forbidden)
     */
    @NonNull
    private ResponseEntity<Void> addUserToCourseGroup(String userLogin, User instructorOrAdmin, Course course, String group) {
        if (authCheckService.isAtLeastInstructorInCourse(course, instructorOrAdmin)) {
            Optional<User> userToAddToGroup = userRepository.findOneWithGroupsAndAuthoritiesByLogin(userLogin);
            if (userToAddToGroup.isEmpty()) {
                throw new EntityNotFoundException("User with login " + userLogin + " does not exist");
            }
            User user = userToAddToGroup.get();
            courseAccessService.addUserToGroup(user, group, course);
            return ResponseEntity.ok().body(null);
        }
        else {
            throw new AccessForbiddenException();
        }
    }

    /**
     * DELETE /courses/:courseId/students/:studentLogin : Remove the given user from the students of the course so that the student cannot access the course anymore
     *
     * @param courseId     the id of the course
     * @param studentLogin the login of the user who should lose student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping("courses/{courseId}/students/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> removeStudentFromCourse(@PathVariable Long courseId, @PathVariable String studentLogin) {
        log.debug("REST request to remove {} as student from course : {}", studentLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return removeUserFromCourseGroup(studentLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getStudentGroupName());
    }

    /**
     * DELETE /courses/:courseId/tutors/:tutorsLogin : Remove the given user from the tutors of the course so that the tutors cannot access the course administration anymore
     *
     * @param courseId   the id of the course
     * @param tutorLogin the login of the user who should lose student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping("courses/{courseId}/tutors/{tutorLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> removeTutorFromCourse(@PathVariable Long courseId, @PathVariable String tutorLogin) {
        log.debug("REST request to remove {} as tutor from course : {}", tutorLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return removeUserFromCourseGroup(tutorLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getTeachingAssistantGroupName());
    }

    /**
     * DELETE /courses/:courseId/editors/:editorsLogin : Remove the given user from the editors of the course so that the editors cannot access the course administration anymore
     *
     * @param courseId    the id of the course
     * @param editorLogin the login of the user who should lose student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping("courses/{courseId}/editors/{editorLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> removeEditorFromCourse(@PathVariable Long courseId, @PathVariable String editorLogin) {
        log.debug("REST request to remove {} as editor from course : {}", editorLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return removeUserFromCourseGroup(editorLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getEditorGroupName());
    }

    /**
     * DELETE /courses/:courseId/instructors/:instructorLogin : Remove the given user from the instructors of the course so that the instructor cannot access the course
     * administration anymore
     *
     * @param courseId        the id of the course
     * @param instructorLogin the login of the user who should lose student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping("courses/{courseId}/instructors/{instructorLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> removeInstructorFromCourse(@PathVariable Long courseId, @PathVariable String instructorLogin) {
        log.debug("REST request to remove {} as instructor from course : {}", instructorLogin, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        return removeUserFromCourseGroup(instructorLogin, userRepository.getUserWithGroupsAndAuthorities(), course, course.getInstructorGroupName());
    }

    /**
     * removes the userLogin from the group (student, tutors or instructors) of the given course
     *
     * @param userLogin         the user login of the student, tutor or instructor who should be removed from the group
     * @param instructorOrAdmin the user who initiates this request who must be an instructor of the given course or an admin
     * @param course            the course which is only passes to check if the instructorOrAdmin is an instructor of the course
     * @param group             the group from which the userLogin should be removed
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found) or with status 403 (Forbidden)
     */
    @NonNull
    private ResponseEntity<Void> removeUserFromCourseGroup(String userLogin, User instructorOrAdmin, Course course, String group) {
        if (!authCheckService.isAtLeastInstructorInCourse(course, instructorOrAdmin)) {
            throw new AccessForbiddenException();
        }
        Optional<User> userToRemoveFromGroup = userRepository.findOneWithGroupsAndAuthoritiesByLogin(userLogin);
        if (userToRemoveFromGroup.isEmpty()) {
            throw new EntityNotFoundException("User with login " + userLogin + " does not exist");
        }
        courseAccessService.removeUserFromGroup(userToRemoveFromGroup.get(), group);
        return ResponseEntity.ok().body(null);
    }

    /**
     * POST /courses/:courseId/:courseGroup : Add multiple users to the user group of the course so that they can access the course
     * The passed list of UserDTOs must include at least one unique user identifier (i.e. registration number OR email OR login)
     * <p>
     * This method first tries to find the student in the internal Artemis user database (because the user is probably already using Artemis).
     * In case the user cannot be found, it additionally searches the connected LDAP in case it is configured.
     *
     * @param courseId    the id of the course
     * @param studentDtos the list of students (with at one unique user identifier) who should get access to the course
     * @param courseGroup the group, the user has to be added to, either 'students', 'tutors', 'instructors' or 'editors'
     * @return the list of students who could not be registered for the course, because they could NOT be found in the Artemis database and could NOT be found in the connected LDAP
     */
    @PostMapping("courses/{courseId}/{courseGroup}")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<StudentDTO>> addUsersToCourseGroup(@PathVariable Long courseId, @PathVariable String courseGroup, @RequestBody List<StudentDTO> studentDtos) {
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, courseRepository.findByIdElseThrow(courseId), null);
        log.debug("REST request to add {} as {} to course {}", studentDtos, courseGroup, courseId);
        List<StudentDTO> notFoundStudentsDtos = courseAccessService.registerUsersForCourseGroup(courseId, studentDtos, courseGroup);
        return ResponseEntity.ok().body(notFoundStudentsDtos);
    }
}
