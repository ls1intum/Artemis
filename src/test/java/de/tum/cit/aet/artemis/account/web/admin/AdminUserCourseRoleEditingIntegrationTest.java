package de.tum.cit.aet.artemis.account.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.dto.UserCourseRoleDTO;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseForRoleAssignmentDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Covers what the admin user edit screen relies on: searching the courses to choose from and adding or removing a course role of a user through the course
 * membership endpoints, which keep the global authorities of the user in sync.
 */
class AdminUserCourseRoleEditingIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "admincourseroleediting";

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
        Course course = courseUtilService.addEmptyCourse();
        course.setTitle(title);
        course.setShortName(shortName);
        return courseRepository.save(course);
    }

    private List<CourseForRoleAssignmentDTO> searchCourses(String searchTerm, int size) throws Exception {
        String json = mockMvc.perform(get("/api/admin/courses/for-role-assignment").param("searchTerm", searchTerm).param("size", String.valueOf(size))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    private List<UserCourseRoleDTO> courseRolesOf(User user) throws Exception {
        String json = mockMvc.perform(get("/api/account/admin/users/" + user.getLogin() + "/course-roles")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    private boolean hasInstructorAuthority(User user) {
        return userTestRepository.findByIdWithCourseRolesAndAuthoritiesAndOrganizationsElseThrow(user.getId()).getAuthorities().stream().map(Authority::getName)
                .anyMatch(Role.INSTRUCTOR.getAuthority()::equals);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void searchCourses_matchesTitleAndShortNameIgnoringCaseOrderedByTitle() throws Exception {
        // All titles are lower case so that the expected order does not depend on the collation of the database.
        Course bravo = addCourse("zqsearch bravo", "zzbravo");
        Course alpha = addCourse("zqsearch alpha", "zzalpha");
        Course byShortName = addCourse("zulu unrelated", "ZQSEARCHSHORT");

        assertThat(searchCourses("ZQSEARCH", 10)).extracting(CourseForRoleAssignmentDTO::id).containsExactly(alpha.getId(), bravo.getId(), byShortName.getId());
        assertThat(searchCourses("zqsearch ALPHA", 10)).containsExactly(new CourseForRoleAssignmentDTO(alpha.getId(), "zqsearch alpha", "zzalpha", alpha.getSemester()));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void searchCourses_limitsTheNumberOfResults() throws Exception {
        addCourse("zqlimit one", "zzlimit1");
        addCourse("zqlimit two", "zzlimit2");

        assertThat(searchCourses("zqlimit", 1)).hasSize(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void searchCourses_invalidSize_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/admin/courses/for-role-assignment").param("searchTerm", "x").param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/courses/for-role-assignment").param("searchTerm", "x").param("size", "51")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void searchCourses_nonAdmin_forbidden() throws Exception {
        userUtilService.addInstructor(TEST_PREFIX + "instructor1");

        mockMvc.perform(get("/api/admin/courses/for-role-assignment").param("searchTerm", "x")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAddsAndRemovesCourseRoles_updatesCourseRolesAndAuthorities() throws Exception {
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "member");
        Course course = addCourse(TEST_PREFIX + " Membership", "zzmember");
        assertThat(courseRolesOf(user)).isEmpty();

        request.postWithoutLocation("/api/course/courses/" + course.getId() + "/students/" + user.getLogin(), null, HttpStatus.OK, null);
        request.postWithoutLocation("/api/course/courses/" + course.getId() + "/instructors/" + user.getLogin(), null, HttpStatus.OK, null);
        // Adding the same role twice must stay a single entry.
        request.postWithoutLocation("/api/course/courses/" + course.getId() + "/instructors/" + user.getLogin(), null, HttpStatus.OK, null);

        assertThat(courseRolesOf(user)).extracting(UserCourseRoleDTO::role).containsExactlyInAnyOrder(CourseRole.STUDENT, CourseRole.INSTRUCTOR);
        assertThat(hasInstructorAuthority(user)).isTrue();

        request.delete("/api/course/courses/" + course.getId() + "/instructors/" + user.getLogin(), HttpStatus.OK);

        assertThat(courseRolesOf(user)).extracting(UserCourseRoleDTO::role).containsExactly(CourseRole.STUDENT);
        assertThat(hasInstructorAuthority(user)).isFalse();
    }
}
