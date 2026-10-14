package de.tum.cit.aet.artemis.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.UserActivityService;
import de.tum.cit.aet.artemis.account.service.user.PasswordService;
import de.tum.cit.aet.artemis.account.service.user.UserCreationService;
import de.tum.cit.aet.artemis.account.service.user.UserService;
import de.tum.cit.aet.artemis.account.util.UserFactory;
import de.tum.cit.aet.artemis.core.dto.CredentialRevocationChoiceDTO;
import de.tum.cit.aet.artemis.core.dto.PasswordResetKeyDTO;
import de.tum.cit.aet.artemis.core.dto.UserDTO;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.util.SessionTestUtil;
import de.tum.cit.aet.artemis.localvc.service.UserVcsAccessTokenService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Regression tests for issue 13407: a session that was established before the credentials of its account changed must not be able to
 * create a new credential, because each of them outlives the password reset that was meant to end an intrusion.
 * <p>
 * The session start is what the filter reads from the token's {@code iat}; a mock user carries none, so the tests replace the
 * authentication by one that does, see {@link SessionTestUtil}.
 */
class SessionFreshnessIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String LOGIN = "sessionfreshness";

    @Autowired
    private UserActivityService userActivityService;

    @Autowired
    private UserVcsAccessTokenService userVcsAccessTokenService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserCreationService userCreationService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private TokenProvider tokenProvider;

    private User user;

    @BeforeEach
    void setUp() {
        user = userUtilService.createAndSaveUser(LOGIN, passwordService.hashPassword(UserFactory.USER_PASSWORD));
    }

    @AfterEach
    void tearDown() {
        // The moment the credentials changed is kept per account and survives a test, so each test starts from an account that never changed them.
        userTestRepository.deleteAll(userTestRepository.searchAllByLoginOrName(Pageable.unpaged(), LOGIN));
    }

    private String createUserVcsAccessTokenPath() {
        return "/api/account/user-vcs-access-token?expiryDate=" + ZonedDateTime.now().plusMonths(1);
    }

    @Test
    @WithMockUser(username = LOGIN)
    void aSessionThatStartedBeforeTheCredentialsChangedCannotCreateAnAccessToken() throws Exception {
        userActivityService.recordCredentialsChanged(user.getId(), Instant.now());
        SessionTestUtil.actAsSessionStartedAt(LOGIN, Instant.now().minus(Duration.ofHours(1)));

        request.putWithResponseBody(createUserVcsAccessTokenPath(), null, UserDTO.class, HttpStatus.UNAUTHORIZED);

        assertThat(userVcsAccessTokenService.findToken(user.getId())).isNull();
    }

    @Test
    @WithMockUser(username = LOGIN)
    void aSessionThatStartedAfterTheCredentialsChangedCanCreateAnAccessToken() throws Exception {
        userActivityService.recordCredentialsChanged(user.getId(), Instant.now().minus(Duration.ofHours(1)));
        SessionTestUtil.actAsSessionStartedAt(LOGIN, Instant.now().minus(Duration.ofMinutes(5)));

        UserDTO created = request.putWithResponseBody(createUserVcsAccessTokenPath(), null, UserDTO.class, HttpStatus.OK);

        assertThat(userVcsAccessTokenService.findToken(user.getId())).isEqualTo(created.getVcsAccessToken());
    }

    @Test
    @WithMockUser(username = LOGIN)
    void aSessionOfAnAccountThatNeverChangedItsCredentialsIsNotRefused() throws Exception {
        SessionTestUtil.actAsSessionStartedAt(LOGIN, Instant.now().minus(Duration.ofDays(20)));

        request.putWithResponseBody(createUserVcsAccessTokenPath(), null, UserDTO.class, HttpStatus.OK);
    }

    /**
     * The scenario of the issue from end to end: the owner resets the password to end an intrusion and revokes every credential. The
     * session the intruder is still using must not bring a revoked credential back.
     */
    @Test
    @WithMockUser(username = LOGIN)
    void aRevokedCredentialCannotBeCreatedAgainByTheSessionThatPredatesTheReset() throws Exception {
        userVcsAccessTokenService.store(user.getId(), "vcpat-" + "a".repeat(44), ZonedDateTime.now().plusMonths(1));
        Instant sessionStartedBeforeTheReset = Instant.now().minus(Duration.ofHours(2));
        PasswordResetKeyDTO resetKey = userService.prepareUserForPasswordReset(user).orElseThrow();

        userService.completePasswordReset("a-new-password-12", resetKey.id(), resetKey.secret(), new CredentialRevocationChoiceDTO(true, true, true)).orElseThrow();
        assertThat(userVcsAccessTokenService.findToken(user.getId())).isNull();

        SessionTestUtil.actAsSessionStartedAt(LOGIN, sessionStartedBeforeTheReset);
        request.putWithResponseBody(createUserVcsAccessTokenPath(), null, UserDTO.class, HttpStatus.UNAUTHORIZED);

        assertThat(userVcsAccessTokenService.findToken(user.getId())).isNull();
    }

    @Test
    @WithMockUser(username = LOGIN)
    void aDeactivatedAccountCannotCreateCredentialsEvenWithAnOtherwiseCurrentSession() throws Exception {
        userCreationService.deactivateUser(user);
        SessionTestUtil.actAsSessionStartedAt(LOGIN, Instant.now().plus(Duration.ofMinutes(5)));

        request.putWithResponseBody(createUserVcsAccessTokenPath(), null, UserDTO.class, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockUser(username = LOGIN)
    void aSessionThatStartedBeforeTheCredentialsChangedCannotCreateAParticipationAccessToken() throws Exception {
        userActivityService.recordCredentialsChanged(user.getId(), Instant.now());
        SessionTestUtil.actAsSessionStartedAt(LOGIN, Instant.now().minus(Duration.ofHours(1)));

        request.putWithResponseBody("/api/account/participation-vcs-access-token?participationId=1", null, String.class, HttpStatus.UNAUTHORIZED);
    }

    /**
     * The account response is read on every page load, so an outdated session is not refused there. It must not carry the secret of a
     * personal token that was created after the change, which the session could otherwise copy and use after it expired.
     */
    @Test
    @WithMockUser(username = LOGIN)
    void anOutdatedSessionDoesNotReadAReplacementPersonalTokenFromTheAccount() throws Exception {
        Instant sessionStartedBeforeTheReset = Instant.now().minus(Duration.ofHours(2));
        PasswordResetKeyDTO resetKey = userService.prepareUserForPasswordReset(user).orElseThrow();
        userService.completePasswordReset("a-new-password-12", resetKey.id(), resetKey.secret(), new CredentialRevocationChoiceDTO(true, true, true)).orElseThrow();

        SessionTestUtil.actAsSessionStartedAt(LOGIN, Instant.now().plus(Duration.ofMinutes(5)));
        UserDTO created = request.putWithResponseBody(createUserVcsAccessTokenPath(), null, UserDTO.class, HttpStatus.OK);
        assertThat(created.getVcsAccessToken()).isNotNull();

        SessionTestUtil.actAsSessionStartedAt(LOGIN, sessionStartedBeforeTheReset);
        UserDTO account = request.get("/api/core/public/account", HttpStatus.OK, UserDTO.class);

        assertThat(account.getLogin()).isEqualTo(LOGIN);
        assertThat(account.getVcsAccessToken()).isNull();
        assertThat(account.getVcsAccessTokenExpiryDate()).isNull();
    }

    @Test
    @WithMockUser(username = LOGIN)
    void aCurrentSessionReadsItsPersonalTokenFromTheAccount() throws Exception {
        userActivityService.recordCredentialsChanged(user.getId(), Instant.now().minus(Duration.ofHours(1)));
        userVcsAccessTokenService.store(user.getId(), "vcpat-" + "b".repeat(44), ZonedDateTime.now().plusMonths(1));
        SessionTestUtil.actAsSessionStartedAt(LOGIN, Instant.now().minus(Duration.ofMinutes(5)));

        UserDTO account = request.get("/api/core/public/account", HttpStatus.OK, UserDTO.class);

        assertThat(account.getVcsAccessToken()).isEqualTo("vcpat-" + "b".repeat(44));
        assertThat(account.getVcsAccessTokenExpiryDate()).isNotNull();
    }

    /**
     * The participation token is minted when a participation is started, so a session could otherwise get a credential that outlives it
     * just by reading it back.
     */
    @Test
    @WithMockUser(username = LOGIN)
    void aSessionThatStartedBeforeTheCredentialsChangedCannotReadAParticipationAccessToken() throws Exception {
        userActivityService.recordCredentialsChanged(user.getId(), Instant.now());
        SessionTestUtil.actAsSessionStartedAt(LOGIN, Instant.now().minus(Duration.ofHours(1)));

        request.get("/api/account/participation-vcs-access-token?participationId=1", HttpStatus.UNAUTHORIZED, String.class);
    }

    /**
     * A tool token starts a session of its own with {@code iat} set to now. Minting one from an outdated session would carry that
     * session past the change, and a tool token is accepted by the endpoints that hand out repository access.
     * <p>
     * The session is the cookie the request carries, so that is what is given an old {@code iat}: the filter reads it from the token.
     */
    @Test
    @WithMockUser(username = LOGIN)
    void anOutdatedSessionCannotBeConvertedIntoAToolToken() throws Exception {
        userActivityService.recordCredentialsChanged(user.getId(), Instant.now());
        Cookie cookie = sessionCookieIssuedAt(Instant.now().minus(Duration.ofHours(1)));

        request.performMvcRequest(post("/api/account/tool-token").cookie(cookie).params(toolParams())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = LOGIN)
    void aCurrentSessionCanBeConvertedIntoAToolToken() throws Exception {
        userActivityService.recordCredentialsChanged(user.getId(), Instant.now().minus(Duration.ofHours(1)));
        Cookie cookie = sessionCookieIssuedAt(Instant.now().minus(Duration.ofMinutes(5)));

        request.performMvcRequest(post("/api/account/tool-token").cookie(cookie).params(toolParams())).andExpect(status().isOk());
    }

    private Cookie sessionCookieIssuedAt(Instant issuedAt) {
        var authentication = new UsernamePasswordAuthenticationToken(LOGIN, "token", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        String token = tokenProvider.createToken(authentication, Date.from(issuedAt), new Date(System.currentTimeMillis() + Duration.ofHours(12).toMillis()), null, null);
        return new Cookie("jwt", token);
    }

    private static LinkedMultiValueMap<String, String> toolParams() {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("tool", ToolTokenType.SCORPIO.toString());
        return params;
    }
}
