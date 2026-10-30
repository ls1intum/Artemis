package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseRoleMemberDTO;
import de.tum.cit.aet.artemis.core.dto.UserForRegistrationDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseAccessResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courseaccessresource";

    private static final int NUMBER_OF_STUDENTS = 8;

    // Registration numbers are capped at 20 characters.
    private static final String REGISTRATION_NUMBER = "CARIT-REG-0001";

    private Course course;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 0, 1);
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
    }

    private String pagedUrl() {
        return "/api/course/courses/" + course.getId() + "/students/paged";
    }

    private String searchUrl() {
        return "/api/course/courses/" + course.getId() + "/students/users/search";
    }

    private static MultiValueMap<String, String> pagedParams(String page, String pageSize) {
        return pagedParams(page, pageSize, "");
    }

    private static MultiValueMap<String, String> pagedParams(String page, String pageSize, String searchTerm) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", page);
        params.add("pageSize", pageSize);
        params.add("sortingOrder", "ASCENDING");
        params.add("sortedColumn", "login");
        params.add("searchTerm", searchTerm);
        return params;
    }

    private static MultiValueMap<String, String> searchParams(String searchTerm) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("searchTerm", searchTerm);
        return params;
    }

    private List<CourseRoleMemberDTO> getPage(MultiValueMap<String, String> params) throws Exception {
        return request.getList(pagedUrl(), HttpStatus.OK, CourseRoleMemberDTO.class, params);
    }

    private List<UserForRegistrationDTO> search(String searchTerm) throws Exception {
        return request.getList(searchUrl(), HttpStatus.OK, UserForRegistrationDTO.class, searchParams(searchTerm));
    }

    private void setUserName(String login, String firstName, String lastName) {
        User user = userUtilService.getUserByLogin(login);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        userTestRepository.save(user);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPagedUsersInCourseRole_returnsRequestedPageSize() throws Exception {
        assertThat(getPage(pagedParams("0", "3"))).hasSize(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPagedUsersInCourseRole_returnsSecondPage() throws Exception {
        List<CourseRoleMemberDTO> firstPage = getPage(pagedParams("0", "5"));
        List<CourseRoleMemberDTO> secondPage = getPage(pagedParams("1", "5"));

        assertThat(firstPage).hasSize(5);
        assertThat(secondPage).hasSize(NUMBER_OF_STUDENTS - 5);
        assertThat(secondPage).extracting(CourseRoleMemberDTO::login).doesNotContainAnyElementsOf(firstPage.stream().map(CourseRoleMemberDTO::login).toList());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPagedUsersInCourseRole_setsTotalCountHeader() throws Exception {
        MvcResult result = request.performMvcRequest(get(pagedUrl()).params(pagedParams("0", "3"))).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getHeader("X-Total-Count")).isEqualTo(String.valueOf(NUMBER_OF_STUDENTS));
    }

    @ParameterizedTest
    @CsvSource({ "0, 0", "-1, 10", "0, 201", "100001, 10", "2147483647, 200" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPagedUsersInCourseRole_rejectsOutOfBoundsPagination(String page, String pageSize) throws Exception {
        request.getList(pagedUrl(), HttpStatus.BAD_REQUEST, CourseRoleMemberDTO.class, pagedParams(page, pageSize));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPagedUsersInCourseRole_searchByLogin_returnsMatchingUser() throws Exception {
        assertThat(getPage(pagedParams("0", "10", TEST_PREFIX + "student1"))).extracting(CourseRoleMemberDTO::login).containsExactly(TEST_PREFIX + "student1");
    }

    /**
     * Generated test users have firstName/lastName = login + "First"/"Last", so "First" only matches the name.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPagedUsersInCourseRole_searchByName_returnsMatchingUser() throws Exception {
        assertThat(getPage(pagedParams("0", "10", TEST_PREFIX + "student2First"))).extracting(CourseRoleMemberDTO::login).containsExactly(TEST_PREFIX + "student2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPagedUsersInCourseRole_searchByEmail_returnsMatchingUser() throws Exception {
        assertThat(getPage(pagedParams("0", "10", "@test.de"))).extracting(CourseRoleMemberDTO::login).contains(TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPagedUsersInCourseRole_searchByRegistrationNumber_returnsMatchingUser() throws Exception {
        userUtilService.setRegistrationNumberOfUserAndSave(TEST_PREFIX + "student1", REGISTRATION_NUMBER);
        assertThat(getPage(pagedParams("0", "10", REGISTRATION_NUMBER))).extracting(CourseRoleMemberDTO::login).containsExactly(TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPagedUsersInCourseRole_sortsDescendingByLogin() throws Exception {
        MultiValueMap<String, String> params = pagedParams("0", String.valueOf(NUMBER_OF_STUDENTS));
        params.set("sortingOrder", "DESCENDING");
        assertThat(getPage(params)).extracting(CourseRoleMemberDTO::login).containsExactly(TEST_PREFIX + "student8", TEST_PREFIX + "student7", TEST_PREFIX + "student6",
                TEST_PREFIX + "student5", TEST_PREFIX + "student4", TEST_PREFIX + "student3", TEST_PREFIX + "student2", TEST_PREFIX + "student1");
    }

    /**
     * Sorting by name orders by the concatenated display name. A (firstName, lastName) tuple sort would put "Ann"/"Zulu"
     * before "Ann Maria"/"Alpha", but the concatenated strings "Ann Maria Alpha" and "Ann Zulu" sort the other way around.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPagedUsersInCourseRole_sortsByConcatenatedNameNotTuple() throws Exception {
        setUserName(TEST_PREFIX + "student1", "Ann", "Zulu");
        setUserName(TEST_PREFIX + "student2", "Ann Maria", "Alpha");

        MultiValueMap<String, String> params = pagedParams("0", String.valueOf(NUMBER_OF_STUDENTS));
        params.set("sortedColumn", "name");
        List<String> logins = getPage(params).stream().map(CourseRoleMemberDTO::login).toList();

        assertThat(logins.indexOf(TEST_PREFIX + "student2")).as("Ann Maria Alpha should sort before Ann Zulu").isLessThan(logins.indexOf(TEST_PREFIX + "student1"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPagedUsersInCourseRole_returnsCorrectDtoFields() throws Exception {
        userUtilService.setRegistrationNumberOfUserAndSave(TEST_PREFIX + "student1", REGISTRATION_NUMBER);

        List<CourseRoleMemberDTO> result = getPage(pagedParams("0", "10", TEST_PREFIX + "student1"));

        assertThat(result).hasSize(1);
        CourseRoleMemberDTO dto = result.getFirst();
        assertThat(dto.login()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(dto.name()).isEqualTo(TEST_PREFIX + "student1First " + TEST_PREFIX + "student1Last");
        assertThat(dto.email()).isEqualTo(TEST_PREFIX + "student1@test.de");
        assertThat(dto.visibleRegistrationNumber()).isEqualTo(REGISTRATION_NUMBER);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getPagedUsersInCourseRole_unknownSlug_returnsBadRequest() throws Exception {
        request.getList("/api/course/courses/" + course.getId() + "/unknownRole/paged", HttpStatus.BAD_REQUEST, CourseRoleMemberDTO.class, pagedParams("0", "10"));
    }

    /**
     * An admin passes the course-role check unconditionally, so a missing course must still return 404 instead of an empty page.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getPagedUsersInCourseRole_nonExistentCourse_returnsNotFound() throws Exception {
        request.getList("/api/course/courses/" + Long.MAX_VALUE + "/students/paged", HttpStatus.NOT_FOUND, CourseRoleMemberDTO.class, pagedParams("0", "10"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getPagedUsersInCourseRole_forbiddenForTutor() throws Exception {
        request.getList(pagedUrl(), HttpStatus.FORBIDDEN, CourseRoleMemberDTO.class, pagedParams("0", "10"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getPagedUsersInCourseRole_forbiddenForStudent() throws Exception {
        request.getList(pagedUrl(), HttpStatus.FORBIDDEN, CourseRoleMemberDTO.class, pagedParams("0", "10"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void searchUsersForCourseRole_byLogin_returnsMatchingUsers() throws Exception {
        List<UserForRegistrationDTO> result = search(TEST_PREFIX + "student");
        assertThat(result).hasSize(NUMBER_OF_STUDENTS);
        assertThat(result).allMatch(user -> user.login().startsWith(TEST_PREFIX + "student"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void searchUsersForCourseRole_marksAlreadyEnrolledUser() throws Exception {
        List<UserForRegistrationDTO> result = search(TEST_PREFIX + "student1");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().login()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(result.getFirst().isRegistered()).isTrue();
    }

    /**
     * tutor1 is enrolled as a tutor, not as a student, so it is not registered for the students role.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void searchUsersForCourseRole_nonEnrolledUserNotFlagged() throws Exception {
        List<UserForRegistrationDTO> result = search(TEST_PREFIX + "tutor1");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().login()).isEqualTo(TEST_PREFIX + "tutor1");
        assertThat(result.getFirst().isRegistered()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void searchUsersForCourseRole_noResultsForUnknownTerm() throws Exception {
        assertThat(search("zzz_no_match_zzz")).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void searchUsersForCourseRole_searchByEmail_returnsMatchingUser() throws Exception {
        assertThat(search(TEST_PREFIX + "student1@test.de")).extracting(UserForRegistrationDTO::login).containsExactly(TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void searchUsersForCourseRole_searchByRegistrationNumber_returnsMatchingUser() throws Exception {
        userUtilService.setRegistrationNumberOfUserAndSave(TEST_PREFIX + "student1", REGISTRATION_NUMBER);
        assertThat(search(REGISTRATION_NUMBER)).extracting(UserForRegistrationDTO::login).containsExactly(TEST_PREFIX + "student1");
    }

    @ParameterizedTest
    @CsvSource({ "-1, 10", "100001, 10", "0, 0", "0, 201", "2147483647, 200" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void searchUsersForCourseRole_rejectsOutOfBoundsPagination(String page, String size) throws Exception {
        MultiValueMap<String, String> params = searchParams(TEST_PREFIX + "student");
        params.add("page", page);
        params.add("size", size);
        request.getList(searchUrl(), HttpStatus.BAD_REQUEST, UserForRegistrationDTO.class, params);
    }

    /**
     * An admin passes the course-role check unconditionally, so a missing course must still return 404 instead of global search results.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void searchUsersForCourseRole_nonExistentCourse_returnsNotFound() throws Exception {
        request.getList("/api/course/courses/" + Long.MAX_VALUE + "/students/users/search", HttpStatus.NOT_FOUND, UserForRegistrationDTO.class,
                searchParams(TEST_PREFIX + "student"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void searchUsersForCourseRole_forbiddenForTutor() throws Exception {
        request.getList(searchUrl(), HttpStatus.FORBIDDEN, UserForRegistrationDTO.class, searchParams(TEST_PREFIX + "student"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void searchUsersForCourseRole_forbiddenForStudent() throws Exception {
        request.getList(searchUrl(), HttpStatus.FORBIDDEN, UserForRegistrationDTO.class, searchParams(TEST_PREFIX + "student"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void addUsersToCourseRole_unknownSlug_returnsBadRequest() throws Exception {
        request.postWithoutLocation("/api/course/courses/" + course.getId() + "/unknownRole", List.of(), HttpStatus.BAD_REQUEST, null);
    }
}
