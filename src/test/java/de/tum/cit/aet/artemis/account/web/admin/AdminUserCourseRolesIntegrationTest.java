package de.tum.cit.aet.artemis.account.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.dto.UserCourseRoleDTO;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class AdminUserCourseRolesIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "admincourseroles";

    private static final String DEFAULT_SEMESTER = "SS25";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private CourseUtilService courseUtilService;

    @BeforeEach
    void setUpAuthenticatedAdministrators() {
        // Admin endpoints validate the current account state in addition to the authorities in the mock security context.
        userUtilService.addAdmin("");
    }

    private Course addCourse(String title, String shortName) {
        return addCourse(title, shortName, DEFAULT_SEMESTER);
    }

    private Course addCourse(String title, String shortName, String semester) {
        Course course = courseUtilService.addEmptyCourse();
        course.setTitle(title);
        course.setShortName(shortName);
        course.setSemester(semester);
        return courseRepository.save(course);
    }

    private List<UserCourseRoleDTO> getCourseRoles(String login) throws Exception {
        String json = mockMvc.perform(get("/api/account/admin/users/" + login + "/course-roles")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getCourseRoles_returnsOneEntryPerCourseAndRole() throws Exception {
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "multi");
        Course courseB = addCourse("Bravo course", TEST_PREFIX + "b", "WS25");
        Course courseA = addCourse("Alpha course", TEST_PREFIX + "a");
        userUtilService.enrollUserInCourse(user, courseB, CourseRole.STUDENT);
        userUtilService.enrollUserInCourse(user, courseA, CourseRole.TEACHING_ASSISTANT);
        userUtilService.enrollUserInCourse(user, courseA, CourseRole.EDITOR);

        List<UserCourseRoleDTO> roles = getCourseRoles(user.getLogin());

        // The order is not part of the contract, the client sorts for display.
        assertThat(roles).containsExactlyInAnyOrder(new UserCourseRoleDTO(courseA.getId(), "Alpha course", TEST_PREFIX + "a", DEFAULT_SEMESTER, CourseRole.EDITOR),
                new UserCourseRoleDTO(courseA.getId(), "Alpha course", TEST_PREFIX + "a", DEFAULT_SEMESTER, CourseRole.TEACHING_ASSISTANT),
                new UserCourseRoleDTO(courseB.getId(), "Bravo course", TEST_PREFIX + "b", "WS25", CourseRole.STUDENT));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getCourseRoles_coursesWithEqualTitles_areToldApartByShortNameAndSemester() throws Exception {
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "equaltitles");
        Course winter = addCourse("Algorithms", TEST_PREFIX + "algo25", "WS25");
        Course summer = addCourse("Algorithms", TEST_PREFIX + "algo26", "SS26");
        userUtilService.enrollUserInCourse(user, winter, CourseRole.STUDENT);
        userUtilService.enrollUserInCourse(user, summer, CourseRole.STUDENT);

        assertThat(getCourseRoles(user.getLogin())).containsExactlyInAnyOrder(
                new UserCourseRoleDTO(winter.getId(), "Algorithms", TEST_PREFIX + "algo25", "WS25", CourseRole.STUDENT),
                new UserCourseRoleDTO(summer.getId(), "Algorithms", TEST_PREFIX + "algo26", "SS26", CourseRole.STUDENT));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getCourseRoles_loginWithDot_isResolved() throws Exception {
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "first.last");
        Course course = addCourse("Dotted course", TEST_PREFIX + "dot");
        userUtilService.enrollUserInCourse(user, course, CourseRole.INSTRUCTOR);

        assertThat(user.getLogin()).contains(".");
        assertThat(getCourseRoles(user.getLogin()))
                .containsExactly(new UserCourseRoleDTO(course.getId(), "Dotted course", TEST_PREFIX + "dot", DEFAULT_SEMESTER, CourseRole.INSTRUCTOR));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getCourseRoles_doesNotIncludeRolesOfOtherUsers() throws Exception {
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "own");
        User other = userUtilService.createAndSaveUser(TEST_PREFIX + "other");
        Course course = addCourse("Shared course", TEST_PREFIX + "s");
        userUtilService.enrollUserInCourse(user, course, CourseRole.INSTRUCTOR);
        userUtilService.enrollUserInCourse(other, course, CourseRole.STUDENT);

        assertThat(getCourseRoles(user.getLogin()))
                .containsExactly(new UserCourseRoleDTO(course.getId(), "Shared course", TEST_PREFIX + "s", DEFAULT_SEMESTER, CourseRole.INSTRUCTOR));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getCourseRoles_userWithoutCourses_returnsEmptyList() throws Exception {
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "none");

        assertThat(getCourseRoles(user.getLogin())).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getCourseRoles_unknownUser_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/account/admin/users/" + TEST_PREFIX + "unknown/course-roles")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "nonadmin", roles = "INSTRUCTOR")
    void getCourseRoles_nonAdmin_forbidden() throws Exception {
        userUtilService.addInstructor(TEST_PREFIX + "nonadmin");
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "target");

        mockMvc.perform(get("/api/account/admin/users/" + user.getLogin() + "/course-roles")).andExpect(status().isForbidden());
    }
}
