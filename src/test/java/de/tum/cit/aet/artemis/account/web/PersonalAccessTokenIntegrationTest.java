package de.tum.cit.aet.artemis.account.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PERSONAL_ACCESS_TOKEN_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import de.tum.cit.aet.artemis.account.domain.PersonalAccessToken;
import de.tum.cit.aet.artemis.account.dto.CreatePersonalAccessTokenDTO;
import de.tum.cit.aet.artemis.account.dto.CreatedPersonalAccessTokenDTO;
import de.tum.cit.aet.artemis.account.dto.PersonalAccessTokenDTO;
import de.tum.cit.aet.artemis.account.repository.PersonalAccessTokenRepository;
import de.tum.cit.aet.artemis.account.service.UserActivityService;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil.SavedToken;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class PersonalAccessTokenIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "patoken";

    private static final String TOKENS_URL = "/api/account/personal-access-tokens";

    private static final String ADMIN_TOKENS_URL = "/api/account/admin/users/%s/personal-access-tokens";

    @Autowired
    private PersonalAccessTokenRepository personalAccessTokenRepository;

    @Autowired
    private UserActivityService userActivityService;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private UserCourseRoleRepository userCourseRoleRepository;

    private Course course;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 1, 1);
        // teaching roles are held in a course: the instructor and the editor of this one may create tokens, the student may not
        course = courseUtilService.createCourse();
        userCourseRoleRepository.save(new UserCourseRole(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), course, CourseRole.INSTRUCTOR));
        userCourseRoleRepository.save(new UserCourseRole(userUtilService.getUserByLogin(TEST_PREFIX + "editor1"), course, CourseRole.EDITOR));
        userCourseRoleRepository.save(new UserCourseRole(userUtilService.getUserByLogin(TEST_PREFIX + "student1"), course, CourseRole.STUDENT));
    }

    @AfterEach
    void tearDown() {
        userCourseRoleRepository.deleteAll();
        courseRepository.deleteById(course.getId());
        personalAccessTokenRepository.deleteAll();
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void createdTokenCanBeListedUsedAndRevoked() throws Exception {
        var created = request.postWithResponseBody(TOKENS_URL, new CreatePersonalAccessTokenDTO("my agent", List.of("competencies:read"), 30), CreatedPersonalAccessTokenDTO.class,
                HttpStatus.CREATED);

        assertThat(created.secret()).startsWith(PERSONAL_ACCESS_TOKEN_PREFIX);
        assertThat(created.token().tokenPrefix()).isEqualTo(created.secret().substring(0, PERSONAL_ACCESS_TOKEN_PREFIX.length() + 4));
        assertThat(personalAccessTokenRepository.findAll()).allSatisfy(stored -> assertThat(stored.getTokenHash()).isNotEqualTo(created.secret()));

        List<PersonalAccessTokenDTO> listed = request.getList(TOKENS_URL, HttpStatus.OK, PersonalAccessTokenDTO.class);
        assertThat(listed).extracting(PersonalAccessTokenDTO::name).containsExactly("my agent");

        // The secret works as a bearer token, when no other authentication is present.
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
        request.performMvcRequest(get("/api/core/test/testScopedRead").header(HttpHeaders.AUTHORIZATION, "Bearer " + created.secret())).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void revokedTokenDisappearsFromListAndCannotBeRevokedTwice() throws Exception {
        var created = request.postWithResponseBody(TOKENS_URL, new CreatePersonalAccessTokenDTO("temporary", List.of("courses:read"), null), CreatedPersonalAccessTokenDTO.class,
                HttpStatus.CREATED);

        request.delete(TOKENS_URL + "/" + created.token().id(), HttpStatus.NO_CONTENT);

        assertThat(request.getList(TOKENS_URL, HttpStatus.OK, PersonalAccessTokenDTO.class)).isEmpty();
        request.delete(TOKENS_URL + "/" + created.token().id(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void creationRejectsUnknownScopeAndTooLongLifetime() throws Exception {
        request.postWithoutResponseBody(TOKENS_URL, new CreatePersonalAccessTokenDTO("bad scope", List.of("grades:read"), 30), HttpStatus.BAD_REQUEST);
        request.postWithoutResponseBody(TOKENS_URL, new CreatePersonalAccessTokenDTO("too long", List.of("courses:read"), 91), HttpStatus.BAD_REQUEST);
        request.postWithoutResponseBody(TOKENS_URL, new CreatePersonalAccessTokenDTO("no scopes", List.of(), 30), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void administratorsCannotCreateTokens() throws Exception {
        request.postWithoutResponseBody(TOKENS_URL, new CreatePersonalAccessTokenDTO("admin token", List.of("courses:read"), 30), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentsCannotCreateTokens() throws Exception {
        request.postWithoutResponseBody(TOKENS_URL, new CreatePersonalAccessTokenDTO("student token", List.of("courses:read"), 30), HttpStatus.FORBIDDEN);
        assertThat(personalAccessTokenRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void editorsCanCreateTokens() throws Exception {
        request.postWithResponseBody(TOKENS_URL, new CreatePersonalAccessTokenDTO("editor token", List.of("courses:read"), 30), CreatedPersonalAccessTokenDTO.class,
                HttpStatus.CREATED);
    }

    @Test
    void aSessionThatStartedBeforeTheCredentialsChangedCannotCreateATokenButANewOneCan() throws Exception {
        String login = TEST_PREFIX + "instructor1";
        userActivityService.recordCredentialsChanged(userUtilService.getUserByLogin(login).getId(), Instant.now().minus(Duration.ofHours(1)));

        // the stolen session was established two hours ago, before the password reset an hour ago
        request.performMvcRequest(createTokenWithSession(login, Instant.now().minus(Duration.ofHours(2)))).andExpect(status().isForbidden());
        assertThat(personalAccessTokenRepository.findAll()).isEmpty();

        // the session of the owner who signed in after the reset
        request.performMvcRequest(createTokenWithSession(login, Instant.now())).andExpect(status().isCreated());
        assertThat(personalAccessTokenRepository.findAll()).hasSize(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void administratorsCanListAndRevokeAllTokensOfAUser() throws Exception {
        String login = TEST_PREFIX + "instructor1";
        saveToken(login, Set.of(AgentScope.COURSES_READ), Duration.ofDays(1));
        var second = saveToken(login, Set.of(AgentScope.COMPETENCIES_READ), Duration.ofDays(1));
        var other = saveToken(TEST_PREFIX + "student1", Set.of(AgentScope.COURSES_READ), Duration.ofDays(1));

        assertThat(request.getList(ADMIN_TOKENS_URL.formatted(login), HttpStatus.OK, PersonalAccessTokenDTO.class)).hasSize(2);

        request.delete(ADMIN_TOKENS_URL.formatted(login), HttpStatus.NO_CONTENT);

        assertThat(request.getList(ADMIN_TOKENS_URL.formatted(login), HttpStatus.OK, PersonalAccessTokenDTO.class)).isEmpty();
        assertThat(personalAccessTokenRepository.findById(other.token().getId()).orElseThrow().getRevokedDate()).isNull();
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
        performWithToken("/api/core/test/testScopedRead", second.secret()).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void listingOrRevokingTheTokensOfAnUnknownUserIsNotFound() throws Exception {
        request.getList(ADMIN_TOKENS_URL.formatted("nobody-by-that-login"), HttpStatus.NOT_FOUND, PersonalAccessTokenDTO.class);
        request.delete(ADMIN_TOKENS_URL.formatted("nobody-by-that-login"), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void onlyAdministratorsCanListOrRevokeTheTokensOfAUser() throws Exception {
        String login = TEST_PREFIX + "student1";
        saveToken(login, Set.of(AgentScope.COURSES_READ), Duration.ofDays(1));

        request.getList(ADMIN_TOKENS_URL.formatted(login), HttpStatus.FORBIDDEN, PersonalAccessTokenDTO.class);
        request.delete(ADMIN_TOKENS_URL.formatted(login), HttpStatus.FORBIDDEN);
        assertThat(personalAccessTokenRepository.findAll()).allSatisfy(token -> assertThat(token.getRevokedDate()).isNull());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void userCannotRevokeAnotherUsersToken() throws Exception {
        PersonalAccessToken studentToken = saveToken(TEST_PREFIX + "student1", Set.of(AgentScope.COURSES_READ), Duration.ofDays(1)).token();

        request.delete(TOKENS_URL + "/" + studentToken.getId(), HttpStatus.NOT_FOUND);
    }

    @Test
    void tokenWithRequiredScopeReachesScopedEndpoint() throws Exception {
        String secret = saveToken(TEST_PREFIX + "student1", Set.of(AgentScope.COMPETENCIES_READ), Duration.ofDays(1)).secret();

        performWithToken("/api/core/test/testScopedRead", secret).andExpect(status().isOk());
    }

    @Test
    void tokenWithoutRequiredScopeIsForbidden() throws Exception {
        String secret = saveToken(TEST_PREFIX + "student1", Set.of(AgentScope.COURSES_READ), Duration.ofDays(1)).secret();

        performWithToken("/api/core/test/testScopedRead", secret).andExpect(status().isForbidden());
    }

    @Test
    void tokenCannotReachEndpointsThatDidNotOptIn() throws Exception {
        String secret = saveToken(TEST_PREFIX + "instructor1", Set.of(AgentScope.values()), Duration.ofDays(1)).secret();

        performWithToken("/api/core/test/testUnscoped", secret).andExpect(status().isForbidden());
        // In particular a token can never be used to manage tokens.
        performWithToken(TOKENS_URL, secret).andExpect(status().isForbidden());
    }

    @Test
    void scopeDoesNotWidenTheRoleOfTheOwner() throws Exception {
        String studentSecret = saveToken(TEST_PREFIX + "student1", Set.of(AgentScope.COMPETENCIES_WRITE), Duration.ofDays(1)).secret();
        String instructorSecret = saveToken(TEST_PREFIX + "instructor1", Set.of(AgentScope.COMPETENCIES_WRITE), Duration.ofDays(1)).secret();

        var course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        String url = "/api/core/test/courses/" + course.getId() + "/testScopedInstructorWrite";

        performWithToken(url, studentSecret).andExpect(status().isForbidden());
        performWithToken(url, instructorSecret).andExpect(status().isOk());
    }

    @Test
    void unknownRevokedAndExpiredTokensAreUnauthorized() throws Exception {
        performWithToken("/api/core/test/testScopedRead", PERSONAL_ACCESS_TOKEN_PREFIX + "doesnotexist").andExpect(status().isUnauthorized());

        var expired = saveToken(TEST_PREFIX + "student1", Set.of(AgentScope.COMPETENCIES_READ), Duration.ofSeconds(-1));
        performWithToken("/api/core/test/testScopedRead", expired.secret()).andExpect(status().isUnauthorized());

        var revoked = saveToken(TEST_PREFIX + "student1", Set.of(AgentScope.COMPETENCIES_READ), Duration.ofDays(1));
        assertThat(personalAccessTokenRepository.revoke(revoked.token().getId(), revoked.token().getUserId(), Instant.now())).isEqualTo(1);
        performWithToken("/api/core/test/testScopedRead", revoked.secret()).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenCreatedBeforeTheCredentialsChangedIsUnauthorized() throws Exception {
        var saved = saveToken(TEST_PREFIX + "student1", Set.of(AgentScope.COMPETENCIES_READ), Duration.ofDays(1));
        performWithToken("/api/core/test/testScopedRead", saved.secret()).andExpect(status().isOk());

        userActivityService.recordCredentialsChanged(saved.token().getUserId(), Instant.now());

        performWithToken("/api/core/test/testScopedRead", saved.secret()).andExpect(status().isUnauthorized());
        var replacement = saveToken(TEST_PREFIX + "student1", Set.of(AgentScope.COMPETENCIES_READ), Duration.ofDays(1));
        performWithToken("/api/core/test/testScopedRead", replacement.secret()).andExpect(status().isOk());
    }

    @Test
    void theSchemeOfTheAuthorizationHeaderIsCaseInsensitive() throws Exception {
        var saved = saveToken(TEST_PREFIX + "student1", Set.of(AgentScope.COMPETENCIES_READ), Duration.ofDays(1));

        request.performMvcRequest(get("/api/core/test/testScopedRead").header(HttpHeaders.AUTHORIZATION, "bearer " + saved.secret())).andExpect(status().isOk());
    }

    @Test
    void tokensAuthenticateWhenArtemisHasAServletContextPath() throws Exception {
        var saved = saveToken(TEST_PREFIX + "student1", Set.of(AgentScope.COMPETENCIES_READ), Duration.ofDays(1));
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();

        request.performMvcRequest(get("/artemis/api/core/test/testScopedRead").contextPath("/artemis").header(HttpHeaders.AUTHORIZATION, saved.bearer()))
                .andExpect(status().isOk());
        assertThat(personalAccessTokenRepository.findById(saved.token().getId()).orElseThrow().getLastUsedDate()).isNotNull();
    }

    @Test
    void aTokenIsNotAuthenticatedOnAPublicEndpoint() throws Exception {
        var saved = saveToken(TEST_PREFIX + "student1", Set.of(AgentScope.values()), Duration.ofDays(1));

        // The request is left to the other filters, so a token cannot start a session through an endpoint such as the LTI launch.
        int status = request.performMvcRequest(get("/api/core/public/privacy-statement?language=en").header(HttpHeaders.AUTHORIZATION, saved.bearer())).andReturn().getResponse()
                .getStatus();
        assertThat(status).as("a public endpoint is not answered as an unauthorized token request").isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(personalAccessTokenRepository.findById(saved.token().getId()).orElseThrow().getLastUsedDate()).isNull();
    }

    @Test
    void aTokenIsNotAuthenticatedOnAPublicEndpointWrittenWithAnEscape() throws Exception {
        var saved = saveToken(TEST_PREFIX + "student1", Set.of(AgentScope.values()), Duration.ofDays(1));

        // The filters that match the decoded path see the same public endpoint, so the escape must not get a token past the gate.
        request.performMvcRequest(get(URI.create("/api/core/%70ublic/privacy-statement?language=en")).header(HttpHeaders.AUTHORIZATION, saved.bearer()));
        request.performMvcRequest(get(URI.create("/api/core/%69nternal/anything")).header(HttpHeaders.AUTHORIZATION, saved.bearer()));
        assertThat(personalAccessTokenRepository.findById(saved.token().getId()).orElseThrow().getLastUsedDate()).isNull();
    }

    @Test
    void administratorTokenIsUnauthorized() throws Exception {
        String secret = saveToken("admin", Set.of(AgentScope.COMPETENCIES_READ), Duration.ofDays(1)).secret();

        performWithToken("/api/core/test/testScopedRead", secret).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenTogetherWithCookieIsBadRequest() throws Exception {
        String secret = saveToken(TEST_PREFIX + "student1", Set.of(AgentScope.COMPETENCIES_READ), Duration.ofDays(1)).secret();

        request.performMvcRequest(get("/api/core/test/testScopedRead").header(HttpHeaders.AUTHORIZATION, "Bearer " + secret).cookie(new Cookie(Constants.JWT_COOKIE_NAME, "x")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void usingATokenRecordsWhenItWasLastUsed() throws Exception {
        var saved = saveToken(TEST_PREFIX + "student1", Set.of(AgentScope.COMPETENCIES_READ), Duration.ofDays(1));
        assertThat(saved.token().getLastUsedDate()).isNull();

        performWithToken("/api/core/test/testScopedRead", saved.secret()).andExpect(status().isOk());

        assertThat(personalAccessTokenRepository.findById(saved.token().getId()).orElseThrow().getLastUsedDate()).isNotNull();
    }

    private ResultActions performWithToken(String url, String secret) throws Exception {
        return request.performMvcRequest(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + secret));
    }

    /** A request to create a token that carries a session cookie whose session was established at the given time. */
    private MockHttpServletRequestBuilder createTokenWithSession(String login, Instant sessionStartedAt) {
        var authentication = new UsernamePasswordAuthenticationToken(login, "password", List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
        Date issuedAt = Date.from(sessionStartedAt);
        String jwt = tokenProvider.createToken(authentication, issuedAt, Date.from(Instant.now().plus(Duration.ofHours(1))), null, false);
        return post(TOKENS_URL).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"agent\",\"scopes\":[\"courses:read\"],\"expiresInDays\":30}")
                .cookie(new Cookie(Constants.JWT_COOKIE_NAME, jwt));
    }

    private SavedToken saveToken(String login, Set<AgentScope> scopes, Duration lifetime) {
        return PersonalAccessTokenTestUtil.saveToken(personalAccessTokenRepository, userUtilService.getUserByLogin(login).getId(), scopes, lifetime);
    }
}
