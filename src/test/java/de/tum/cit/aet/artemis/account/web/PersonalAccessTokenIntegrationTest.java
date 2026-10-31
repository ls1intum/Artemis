package de.tum.cit.aet.artemis.account.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PERSONAL_ACCESS_TOKEN_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

import de.tum.cit.aet.artemis.account.domain.PersonalAccessToken;
import de.tum.cit.aet.artemis.account.dto.CreatePersonalAccessTokenDTO;
import de.tum.cit.aet.artemis.account.dto.CreatedPersonalAccessTokenDTO;
import de.tum.cit.aet.artemis.account.dto.PersonalAccessTokenDTO;
import de.tum.cit.aet.artemis.account.repository.PersonalAccessTokenRepository;
import de.tum.cit.aet.artemis.account.service.UserActivityService;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil.SavedToken;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class PersonalAccessTokenIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "patoken";

    private static final String TOKENS_URL = "/api/account/personal-access-tokens";

    @Autowired
    private PersonalAccessTokenRepository personalAccessTokenRepository;

    @Autowired
    private UserActivityService userActivityService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
    }

    @AfterEach
    void tearDown() {
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

    private SavedToken saveToken(String login, Set<AgentScope> scopes, Duration lifetime) {
        return PersonalAccessTokenTestUtil.saveToken(personalAccessTokenRepository, userUtilService.getUserByLogin(login).getId(), scopes, lifetime);
    }
}
