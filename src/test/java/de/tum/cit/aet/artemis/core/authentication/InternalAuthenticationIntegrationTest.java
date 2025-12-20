package de.tum.cit.aet.artemis.core.authentication;

import static de.tum.cit.aet.artemis.core.user.util.UserFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.vm.LoginVM;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.repository.AuthorityRepository;
import de.tum.cit.aet.artemis.core.security.ArtemisInternalAuthenticationProvider;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;

class InternalAuthenticationIntegrationTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "internalauth";

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private JWTCookieService jwtCookieService;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ArtemisInternalAuthenticationProvider artemisInternalAuthenticationProvider;

    private User student;

    private static final String USERNAME = TEST_PREFIX + "student1";

    @BeforeEach
    void setUp() {
        jenkinsRequestMockProvider.enableMockingOfRequests();

        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        courseUtilService.addOnlineCourseConfigurationToCourse(course);

        final var userAuthority = new Authority(Role.STUDENT.getAuthority());
        final var instructorAuthority = new Authority(Role.INSTRUCTOR.getAuthority());
        final var adminAuthority = new Authority(Role.ADMIN.getAuthority());
        final var taAuthority = new Authority(Role.TEACHING_ASSISTANT.getAuthority());
        authorityRepository.saveAll(List.of(userAuthority, instructorAuthority, adminAuthority, taAuthority));

        student = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(USERNAME).orElseThrow();
        final var encodedPassword = passwordService.hashPassword(USER_PASSWORD);
        student.setPassword(encodedPassword);
        student.setInternal(true);
        userTestRepository.save(student);
    }

    @Test
    @WithMockUser(username = "ab12cde")
    void registerForCourse_internalAuth_success() throws Exception {
        final var student = userUtilService.createAndSaveUser("ab12cde");

        final var pastTimestamp = ZonedDateTime.now().minusDays(5);
        final var futureTimestamp = ZonedDateTime.now().plusDays(5);
        var course1 = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1.setEnrollmentEnabled(true);
        course1 = courseRepository.save(course1);
        Set<String> updatedGroups = request.postSetWithResponseBody("/api/core/courses/" + course1.getId() + "/enroll", null, String.class, HttpStatus.OK);
        assertThat(updatedGroups).as("User is registered for course").contains(course1.getStudentGroupName());
    }

    @Test
    @WithAnonymousUser
    void testJWTAuthentication() throws Exception {
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(USERNAME);
        loginVM.setPassword(USER_PASSWORD);
        loginVM.setRememberMe(true);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/core/public/authenticate", loginVM, HttpStatus.OK, httpHeaders);
        AuthenticationIntegrationTestHelper.authenticationCookieAssertions(response.getCookie("jwt"), false);

        var responseBody = new ObjectMapper().readValue(response.getContentAsString(), new TypeReference<Map<String, Object>>() {
        });
        assertThat(tokenProvider.validateTokenForAuthority(responseBody.get("access_token").toString(), null)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testScorpioTokenGeneration() throws Exception {
        ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(true);

        Cookie cookie = new Cookie(responseCookie.getName(), responseCookie.getValue());
        cookie.setMaxAge((int) responseCookie.getMaxAge().toMillis());

        var initialLifetime = tokenProvider.getExpirationDate(cookie.getValue()).getTime() - System.currentTimeMillis();

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("tool", ToolTokenType.SCORPIO.toString());

        var responseBody = request.performMvcRequest(post("/api/core/tool-token").cookie(cookie).params(params)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        AuthenticationIntegrationTestHelper.toolTokenAssertions(tokenProvider, responseBody, initialLifetime, ToolTokenType.SCORPIO);
    }

    @Test
    @WithAnonymousUser
    void testJWTAuthenticationLogoutAnonymous() throws Exception {

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/core/public/logout", HttpStatus.OK, httpHeaders);
        AuthenticationIntegrationTestHelper.authenticationCookieAssertions(response.getCookie("jwt"), true);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testJWTAuthenticationLogout() throws Exception {

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/core/public/logout", HttpStatus.OK, httpHeaders);
        AuthenticationIntegrationTestHelper.authenticationCookieAssertions(response.getCookie("jwt"), true);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateUserWithRemovedGroups_internalAuth_successful() throws Exception {
        final var oldGroups = student.getGroups();
        final var newGroups = Set.of("foo", "bar");
        student.setGroups(newGroups);
        final var managedUserVM = new ManagedUserVM(student);
        managedUserVM.setPassword("12345678");

        final var response = request.putWithResponseBody("/api/core/admin/users", managedUserVM, User.class, HttpStatus.OK);
        final var updatedUserIndDB = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(student.getLogin()).orElseThrow();

        assertThat(passwordService.checkPasswordMatch(managedUserVM.getPassword(), updatedUserIndDB.getPassword())).isTrue();

        // Skip passwords for comparison
        updatedUserIndDB.setPassword(null);
        student.setPassword(null);

        assertThat(response).isNotNull();
        assertThat(student).as("Returned user is equal to sent update").isEqualTo(response);
        assertThat(student).as("Updated user in DB is equal to sent update").isEqualTo(updatedUserIndDB);
    }

    @Test
    void testAuthenticateWithEmail() {
        // Authenticate using email instead of username
        var authentication = new UsernamePasswordAuthenticationToken(student.getEmail(), USER_PASSWORD);
        var result = artemisInternalAuthenticationProvider.authenticate(authentication);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(student.getLogin());
        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    void testAuthenticateWithNonExistentUser() {
        var authentication = new UsernamePasswordAuthenticationToken("nonexistent@example.com", USER_PASSWORD);
        var result = artemisInternalAuthenticationProvider.authenticate(authentication);

        // Should return null for non-existent internal user (allows fallback to LDAP)
        assertThat(result).isNull();
    }

    @Test
    void testAuthenticateWithInactiveUser() {
        student.setActivated(false);
        userTestRepository.save(student);

        var authentication = new UsernamePasswordAuthenticationToken(USERNAME, USER_PASSWORD);

        try {
            artemisInternalAuthenticationProvider.authenticate(authentication);
            assertThat(false).as("Should have thrown UserNotActivatedException").isTrue();
        }
        catch (Exception e) {
            assertThat(e.getMessage()).contains("was not activated");
        }
    }

    @Test
    void testAuthenticateWithWrongPassword() {
        var authentication = new UsernamePasswordAuthenticationToken(USERNAME, "wrongPassword");

        try {
            artemisInternalAuthenticationProvider.authenticate(authentication);
            assertThat(false).as("Should have thrown AuthenticationServiceException").isTrue();
        }
        catch (Exception e) {
            assertThat(e.getMessage()).contains("Invalid password");
        }
    }

    @Test
    void testGetUsernameForEmail() {
        var username = artemisInternalAuthenticationProvider.getUsernameForEmail(student.getEmail());
        assertThat(username).isPresent();
        assertThat(username.get()).isEqualTo(student.getLogin());
    }

    @Test
    void testGetUsernameForNonExistentEmail() {
        var username = artemisInternalAuthenticationProvider.getUsernameForEmail("nonexistent@example.com");
        assertThat(username).isEmpty();
    }

    @Test
    void testSupportsUsernamePasswordAuthenticationToken() {
        assertThat(artemisInternalAuthenticationProvider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
        assertThat(artemisInternalAuthenticationProvider.supports(Object.class)).isFalse();
    }
}
