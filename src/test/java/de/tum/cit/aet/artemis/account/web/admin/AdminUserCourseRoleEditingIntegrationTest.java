package de.tum.cit.aet.artemis.account.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

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
        return addCourse(title, shortName, ZonedDateTime.now().minusDays(10));
    }

    private Course addCourse(String title, String shortName, ZonedDateTime startDate) {
        Course course = courseUtilService.addEmptyCourse();
        course.setTitle(title);
        course.setShortName(shortName);
        course.setStartDate(startDate);
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
    void searchCourses_matchesTitleAndShortNameIgnoringCase() throws Exception {
        String token = TEST_PREFIX + "match";
        Course byTitle = addCourse(token + " Title Course", TEST_PREFIX + "matcht");
        Course byShortName = addCourse("Unrelated title", token.toUpperCase(Locale.ROOT) + "SHORT");
        addCourse("Something else", TEST_PREFIX + "other");

        assertThat(searchCourses(token.toUpperCase(Locale.ROOT), 10)).extracting(CourseForRoleAssignmentDTO::id).containsExactlyInAnyOrder(byTitle.getId(), byShortName.getId());
        assertThat(searchCourses(token + " title", 10))
                .containsExactly(new CourseForRoleAssignmentDTO(byTitle.getId(), byTitle.getTitle(), byTitle.getShortName(), byTitle.getSemester()));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void searchCourses_ordersExactShortNameFirstThenPrefixThenNewestStart() throws Exception {
        String token = TEST_PREFIX + "order";
        ZonedDateTime now = ZonedDateTime.now();
        Course olderSubstring = addCourse("Contains " + token + " older", TEST_PREFIX + "ranka", now.minusDays(30));
        Course newerSubstring = addCourse("Contains " + token + " newer", TEST_PREFIX + "rankb", now.minusDays(2));
        Course prefix = addCourse("Prefix course", token + "-prefix", now.minusDays(60));
        Course exact = addCourse("Exact course", token, now.minusDays(90));
        // Same start as the newer course: the higher id comes first. Their short names do not start with the term, so they only match by title.
        Course newerSubstringTie = addCourse("Contains " + token + " tie", TEST_PREFIX + "rankc", newerSubstring.getStartDate());

        assertThat(searchCourses(token, 10)).extracting(CourseForRoleAssignmentDTO::id).containsExactly(exact.getId(), prefix.getId(), newerSubstringTie.getId(),
                newerSubstring.getId(), olderSubstring.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void searchCourses_matchesWildcardsLiterally() throws Exception {
        String token = TEST_PREFIX + "wild";
        Course percent = addCourse(token + " 100% sure", TEST_PREFIX + "wilda");
        addCourse(token + " 1000 sure", TEST_PREFIX + "wildb");
        Course underscore = addCourse(token + "_x", TEST_PREFIX + "wildc");
        addCourse(token + "ax", TEST_PREFIX + "wildd");

        assertThat(searchCourses(token + " 100%", 10)).extracting(CourseForRoleAssignmentDTO::id).containsExactly(percent.getId());
        assertThat(searchCourses(token + "_", 10)).extracting(CourseForRoleAssignmentDTO::id).containsExactly(underscore.getId());
        // A lone wildcard must not match every course.
        assertThat(searchCourses("%", 10)).extracting(CourseForRoleAssignmentDTO::title).allMatch(title -> title.contains("%"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void searchCourses_blankTerm_findsNothing() throws Exception {
        addCourse(TEST_PREFIX + "blank course", TEST_PREFIX + "blank");

        assertThat(searchCourses("", 10)).isEmpty();
        assertThat(searchCourses("   ", 10)).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void searchCourses_tooLongTerm_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/admin/courses/for-role-assignment").param("searchTerm", "a".repeat(101))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/courses/for-role-assignment").param("searchTerm", "a".repeat(100))).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void searchCourses_limitsTheNumberOfResults() throws Exception {
        String token = TEST_PREFIX + "limit";
        addCourse(token + " one", TEST_PREFIX + "limit1");
        addCourse(token + " two", TEST_PREFIX + "limit2");

        assertThat(searchCourses(token, 1)).hasSize(1);
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
        Course course = addCourse(TEST_PREFIX + " Membership", TEST_PREFIX + "member");
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
