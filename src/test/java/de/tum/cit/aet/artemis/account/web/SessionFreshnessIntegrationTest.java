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
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.core.util.SessionTestUtil;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.localvc.service.UserVcsAccessTokenService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ParticipationVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
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
    private ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

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

    private static final String CREATE_PARTICIPATION_ACCESS_TOKEN_PATH = "/api/account/participation-vcs-access-token?participationId=1";

    /**
     * The scenario of the issue from end to end: the owner resets the password to end an intrusion and revokes every credential. The
     * session the intruder is still using must not bring a revoked credential back. The personal token cannot be created any more, so
     * the credential that is tried is the one of a participation.
     */
    @Test
    @WithMockUser(username = LOGIN)
    void aRevokedCredentialCannotBeCreatedAgainByTheSessionThatPredatesTheReset() throws Exception {
        userUtilService.setUserVcsAccessTokenAndExpiryDateAndSave(user, "vcpat-" + "a".repeat(44), ZonedDateTime.now().plusMonths(1));
        Instant sessionStartedBeforeTheReset = Instant.now().minus(Duration.ofHours(2));
        PasswordResetKeyDTO resetKey = userService.prepareUserForPasswordReset(user).orElseThrow();

        userService.completePasswordReset("a-new-password-12", resetKey.id(), resetKey.secret(), new CredentialRevocationChoiceDTO(true, true, true)).orElseThrow();
        assertThat(userVcsAccessTokenService.findToken(user.getId())).isNull();

        SessionTestUtil.actAsSessionStartedAt(LOGIN, sessionStartedBeforeTheReset);
        request.putWithResponseBody(CREATE_PARTICIPATION_ACCESS_TOKEN_PATH, null, String.class, HttpStatus.UNAUTHORIZED);

        assertThat(userVcsAccessTokenService.findToken(user.getId())).isNull();
    }

    @Test
    @WithMockUser(username = LOGIN)
    void aDeactivatedAccountCannotCreateCredentialsEvenWithAnOtherwiseCurrentSession() throws Exception {
        userCreationService.deactivateUser(user);
        SessionTestUtil.actAsSessionStartedAt(LOGIN, Instant.now().plus(Duration.ofMinutes(5)));

        request.putWithResponseBody(CREATE_PARTICIPATION_ACCESS_TOKEN_PATH, null, String.class, HttpStatus.UNAUTHORIZED);
    }

    private static final String PARTICIPANT_PREFIX = "sfparticipant";

    private static final String PARTICIPANT = PARTICIPANT_PREFIX + "student1";

    /**
     * A real participation of a student, so that the tests below can tell a session that is refused from one that creates the token.
     */
    private long startParticipation() {
        userUtilService.addUsers(PARTICIPANT_PREFIX, 1, 0, 0, 0);
        var course = courseUtilService.addEnrolledEmptyCourse(PARTICIPANT_PREFIX);
        var exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        var submission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").type(SubmissionType.MANUAL).submitted(true);
        long participationId = programmingExerciseUtilService.addProgrammingSubmission(exercise, submission, PARTICIPANT).getParticipation().getId();
        // Starting a participation mints its token; the tests create it themselves, so that a refused request is told from an accepted one.
        participationVCSAccessTokenRepository.deleteAll();
        return participationId;
    }

    private String participationTokenPath(long participationId) {
        return "/api/account/participation-vcs-access-token?participationId=" + participationId;
    }

    @Test
    @WithMockUser(username = PARTICIPANT)
    void aSessionThatStartedBeforeTheCredentialsChangedCannotCreateAParticipationAccessToken() throws Exception {
        long participationId = startParticipation();
        userActivityService.recordCredentialsChanged(userUtilService.getUserByLogin(PARTICIPANT).getId(), Instant.now());
        SessionTestUtil.actAsSessionStartedAt(PARTICIPANT, Instant.now().minus(Duration.ofHours(1)));

        request.putWithResponseBody(participationTokenPath(participationId), null, String.class, HttpStatus.UNAUTHORIZED);

        assertThat(participationVCSAccessTokenRepository.count()).as("no token was created for the refused session").isZero();
    }

    @Test
    @WithMockUser(username = PARTICIPANT)
    void aSessionThatStartedAfterTheCredentialsChangedCanCreateAParticipationAccessToken() throws Exception {
        long participationId = startParticipation();
        userActivityService.recordCredentialsChanged(userUtilService.getUserByLogin(PARTICIPANT).getId(), Instant.now().minus(Duration.ofHours(1)));
        SessionTestUtil.actAsSessionStartedAt(PARTICIPANT, Instant.now().minus(Duration.ofMinutes(5)));

        String token = request.putWithResponseBody(participationTokenPath(participationId), null, String.class, HttpStatus.OK);

        assertThat(token).isNotBlank();
        assertThat(participationVCSAccessTokenRepository.count()).isOne();
    }

    @Test
    @WithMockUser(username = LOGIN)
    void aSessionThatStartedBeforeTheCredentialsChangedCannotReadAParticipationAccessToken() throws Exception {
        Instant credentialsChangedAt = Instant.parse("2026-01-01T12:00:00Z");
        userActivityService.recordCredentialsChanged(user.getId(), credentialsChangedAt);
        SessionTestUtil.actAsSessionStartedAt(LOGIN, credentialsChangedAt.minus(Duration.ofHours(1)));

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
