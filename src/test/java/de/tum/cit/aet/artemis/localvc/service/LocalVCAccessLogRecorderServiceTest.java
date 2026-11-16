package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.sshd.server.session.ServerSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.localvc.service.ssh.SshConstants;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryVCSAccessToken;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.repository.RepositoryVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

@ExtendWith(MockitoExtension.class)
class LocalVCAccessLogRecorderServiceTest {

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    @Mock
    private RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository;

    @Mock
    private VcsAccessLogService vcsAccessLogService;

    @Mock
    private UserVcsAccessTokenService userVcsAccessTokenService;

    @Spy
    private LocalVCRepositoryResolverService repositoryResolver = new LocalVCRepositoryResolverService();

    @InjectMocks
    private LocalVCAccessLogRecorderService accessLogRecorder;

    private User testUser;

    private ProgrammingExercise testExercise;

    private ProgrammingExerciseStudentParticipation testParticipation;

    private LocalVCRepositoryUri testRepositoryUri;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setLogin("testuser");
        testUser.setEmail("testuser@example.com");

        Course testCourse = new Course();
        testCourse.setId(1L);
        testCourse.setShortName("TEST");

        testExercise = new ProgrammingExercise();
        testExercise.setId(1L);
        testExercise.setShortName("EXERCISE");
        testExercise.setCourse(testCourse);
        testExercise.generateAndSetProjectKey();

        testParticipation = new ProgrammingExerciseStudentParticipation();
        testParticipation.setId(1L);

        testRepositoryUri = mock(LocalVCRepositoryUri.class);
        // Use lenient() to avoid unnecessary stubbing errors for tests that don't use this mock
        lenient().when(testRepositoryUri.getRelativeRepositoryPath()).thenReturn(Path.of("test/repo"));
        lenient().when(testRepositoryUri.toString()).thenReturn("http://localhost/git/TEST/EXERCISE-template.git");

        // The repository does not exist, so reading its latest commit finds nothing.
        ReflectionTestUtils.setField(repositoryResolver, "localVCBasePath", Path.of("/tmp/test-repos"));
        ReflectionTestUtils.setField(repositoryResolver, "localVCBaseUri", URI.create("http://localhost"));
        // Setup the VcsAccessLogService as an Optional containing the mock
        ReflectionTestUtils.setField(accessLogRecorder, "vcsAccessLogService", Optional.of(vcsAccessLogService));
    }

    @Test
    void writesTheAccessLogWhenAFetchAuthenticationFails() {
        HttpServletRequest request = failedFetchRequest();
        when(userRepository.findOneByLogin("testuser")).thenReturn(Optional.of(testUser));
        when(programmingExerciseParticipationService.getParticipationReferenceForRepository(anyString(), anyString(), eq(testExercise.getProjectKey())))
                .thenReturn(Optional.of(testParticipation));

        accessLogRecorder.createVCSAccessLogForFailedAuthenticationAttempt(request);

        verify(vcsAccessLogService).saveAccessLog(eq(testUser), eq(testParticipation), eq(RepositoryActionType.CLONE_FAIL), eq(AuthenticationMechanism.PASSWORD), anyString(),
                eq("10.0.0.1"));
    }

    @Test
    void doesNotLetAFailedAccessLogReplaceTheAuthenticationFailure() {
        HttpServletRequest request = failedFetchRequest();
        when(userRepository.findOneByLogin("testuser")).thenReturn(Optional.of(testUser));
        when(programmingExerciseParticipationService.getParticipationReferenceForRepository(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("the database is away"));

        // The caller is in the middle of answering a rejected authentication with 401. An exception escaping here
        // reaches the servlet container instead, and the client is told the server is broken rather than being asked
        // for credentials, which is what a null exercise used to do.
        assertThatCode(() -> accessLogRecorder.createVCSAccessLogForFailedAuthenticationAttempt(request)).doesNotThrowAnyException();
        verifyNoInteractions(vcsAccessLogService);
    }

    private HttpServletRequest failedFetchRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String projectKey = testExercise.getProjectKey();
        when(request.getRequestURI()).thenReturn("/git/" + projectKey + "/" + projectKey.toLowerCase(Locale.ROOT) + "-testuser.git/info/refs");
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic " + Base64.getEncoder().encodeToString("testuser:wrong-password".getBytes(StandardCharsets.UTF_8)));
        lenient().when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        return request;
    }

    @Test
    void testResolveAuthenticationMechanismFromSessionOrRequest_withSession() throws Exception {
        ServerSession session = mock(ServerSession.class);
        AuthenticationContext.Session context = new AuthenticationContext.Session(session);

        AuthenticationMechanism result = accessLogRecorder.resolveAuthenticationMechanismFromSessionOrRequest(context, testUser, testRepositoryUri);

        assertThat(result).isEqualTo(AuthenticationMechanism.SSH);
    }

    @Test
    void testResolveAuthenticationMechanismFromSessionOrRequest_withRequestAndMissingHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        AuthenticationContext.Request context = new AuthenticationContext.Request(request);

        AuthenticationMechanism result = accessLogRecorder.resolveAuthenticationMechanismFromSessionOrRequest(context, testUser, testRepositoryUri);

        assertThat(result).isEqualTo(AuthenticationMechanism.AUTH_HEADER_MISSING);
    }

    @Test
    void testResolveAuthenticationMechanismFromSessionOrRequest_withRequestAndValidHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String authHeader = "Basic " + java.util.Base64.getEncoder().encodeToString("user:password".getBytes());
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authHeader);

        AuthenticationContext.Request context = new AuthenticationContext.Request(request);

        AuthenticationMechanism result = accessLogRecorder.resolveAuthenticationMechanismFromSessionOrRequest(context, testUser, testRepositoryUri);

        assertThat(result).isEqualTo(AuthenticationMechanism.PASSWORD);
    }

    @Test
    void testResolveAuthenticationMechanismFromSessionOrRequest_withRequestAndTokenHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        // Create a valid token with the correct prefix and length (50 characters total)
        String token = "vcpat-" + "a".repeat(44); // 6 + 44 = 50 characters total
        String authHeader = "Basic " + java.util.Base64.getEncoder().encodeToString(("user:" + token).getBytes());
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authHeader);

        when(userVcsAccessTokenService.findToken(testUser.getId())).thenReturn(token);

        AuthenticationContext.Request context = new AuthenticationContext.Request(request);

        AuthenticationMechanism result = accessLogRecorder.resolveAuthenticationMechanismFromSessionOrRequest(context, testUser, testRepositoryUri);

        assertThat(result).isEqualTo(AuthenticationMechanism.USER_VCS_ACCESS_TOKEN);
    }

    @Test
    void testResolveAuthenticationMechanismFromSessionOrRequest_withRepositoryToken() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        // A valid token that does NOT match the user-level token, but matches a repository-scoped staff token for the requested repository.
        String token = "vcpat-" + "b".repeat(44);
        String authHeader = "Basic " + java.util.Base64.getEncoder().encodeToString(("user:" + token).getBytes());
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authHeader);

        RepositoryVCSAccessToken repositoryToken = new RepositoryVCSAccessToken();
        repositoryToken.setVcsAccessToken(token);
        when(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(testUser.getId(), "http://localhost/git/TEST/EXERCISE-template.git"))
                .thenReturn(Optional.of(repositoryToken));

        AuthenticationContext.Request context = new AuthenticationContext.Request(request);

        AuthenticationMechanism result = accessLogRecorder.resolveAuthenticationMechanismFromSessionOrRequest(context, testUser, testRepositoryUri);

        assertThat(result).isEqualTo(AuthenticationMechanism.REPOSITORY_VCS_ACCESS_TOKEN);
    }

    @Test
    void createVCSAccessLogForFailedAuthenticationAttempt_forAnUnknownUser_writesNoLog() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(basicAuth("testuser", "wrong-password"));
        when(userRepository.findOneByLogin("testuser")).thenReturn(Optional.empty());

        assertThatCode(() -> accessLogRecorder.createVCSAccessLogForFailedAuthenticationAttempt(request)).doesNotThrowAnyException();

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void resolveAuthenticationMechanism_forATokenOfNeitherTheUserNorTheRepository_isTheParticipationToken() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(basicAuth("user", vcsAccessToken('c')));
        when(userVcsAccessTokenService.findToken(testUser.getId())).thenReturn(vcsAccessToken('a'));

        AuthenticationMechanism result = accessLogRecorder.resolveAuthenticationMechanismFromSessionOrRequest(new AuthenticationContext.Request(request), testUser,
                testRepositoryUri);

        assertThat(result).isEqualTo(AuthenticationMechanism.PARTICIPATION_VCS_ACCESS_TOKEN);
    }

    private MockHttpServletRequest authorizedRequest(String method, RepositoryActionType action, AuthenticationMechanism mechanism, VcsAccessLog entry) {
        var request = new MockHttpServletRequest(method, "/git/TEST/test-testuser.git/" + (method.equals("GET") ? "info/refs" : "git-upload-pack"));
        when(vcsAccessLogService.createAccessLog(any(), any(), any(), any(), any(), any())).thenReturn(entry);
        accessLogRecorder.saveVcsAccessLogForHTTPS(request, testRepositoryUri, testUser, action, Optional.of(testParticipation), mechanism);
        return request;
    }

    @Test
    void aRepositoryAdvertisementIsReadEvidenceAndIsNeverMarkedAsAClone() {
        var entry = entryOf(RepositoryActionType.READ);
        var request = authorizedRequest("GET", RepositoryActionType.READ, AuthenticationMechanism.PASSWORD, entry);

        accessLogRecorder.markFetchAsCloneHTTPS(request, 0);

        assertThat(entry.getRepositoryActionType()).isEqualTo(RepositoryActionType.READ);
        verify(vcsAccessLogService).createAccessLog(eq(testUser), eq(testParticipation), eq(RepositoryActionType.READ), eq(AuthenticationMechanism.PASSWORD), anyString(), any());
        verify(vcsAccessLogService, never()).updateAccessLog(any());
    }

    @Test
    void aPushAdvertisementIsWriteEvidenceAndKeepsThePreviousHash() {
        var entry = entryOf(RepositoryActionType.WRITE);
        var request = authorizedRequest("GET", RepositoryActionType.WRITE, AuthenticationMechanism.USER_VCS_ACCESS_TOKEN, entry);

        accessLogRecorder.completePushAccessLogHTTPS(request, "pushed");

        assertThat(entry.getCommitHash()).isEmpty();
        verify(vcsAccessLogService).createAccessLog(eq(testUser), eq(testParticipation), eq(RepositoryActionType.WRITE), eq(AuthenticationMechanism.USER_VCS_ACCESS_TOKEN),
                anyString(), any());
        verify(vcsAccessLogService, never()).updateAccessLog(any());
    }

    @Test
    void aBuildAgentCloneIsNotRelabeledAsTheCloneOfAStudent() {
        var entry = entryOf(RepositoryActionType.PULL);
        var request = authorizedRequest("POST", RepositoryActionType.READ, AuthenticationMechanism.PASSWORD, entry);
        request.setAttribute("artemis.buildAgentClone", "build-agent-1");

        accessLogRecorder.markFetchAsCloneHTTPS(request, 0);

        assertThat(entry.getRepositoryActionType()).isEqualTo(RepositoryActionType.PULL);
        verify(vcsAccessLogService, never()).updateAccessLog(any());
    }

    @Test
    void aRequestWithoutAnEntryAmendsNothing() {
        var request = new MockHttpServletRequest("POST", "/git/TEST/tests.git/git-upload-pack");
        accessLogRecorder.saveVcsAccessLogForHTTPS(request, testRepositoryUri, testUser, RepositoryActionType.READ, Optional.empty(), AuthenticationMechanism.PASSWORD);

        accessLogRecorder.markFetchAsCloneHTTPS(request, 0);
        accessLogRecorder.completePushAccessLogHTTPS(request, "pushed");

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void auditUpdateFailuresDoNotFailTheAuthorizedTransfer() {
        var clone = authorizedRequest("POST", RepositoryActionType.READ, AuthenticationMechanism.PASSWORD, entryOf(RepositoryActionType.PULL));
        var push = authorizedRequest("POST", RepositoryActionType.WRITE, AuthenticationMechanism.PASSWORD, entryOf(RepositoryActionType.PUSH));
        doThrow(new IllegalStateException("database unavailable")).when(vcsAccessLogService).updateAccessLog(any());

        assertThatCode(() -> accessLogRecorder.markFetchAsCloneHTTPS(clone, 0)).doesNotThrowAnyException();
        assertThatCode(() -> accessLogRecorder.completePushAccessLogHTTPS(push, "pushed")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = AuthenticationMechanism.class, names = { "PASSWORD", "PARTICIPATION_VCS_ACCESS_TOKEN", "USER_VCS_ACCESS_TOKEN", "REPOSITORY_VCS_ACCESS_TOKEN" })
    void overlappingHttpsFetchesAmendOnlyTheTransferRequestThatNegotiatedAClone(AuthenticationMechanism mechanism) {
        var clone = entryOf(RepositoryActionType.PULL);
        var pull = entryOf(RepositoryActionType.PULL);
        var advertisement = entryOf(RepositoryActionType.READ);
        authorizedRequest("GET", RepositoryActionType.READ, mechanism, advertisement);
        var cloneRequest = authorizedRequest("POST", RepositoryActionType.READ, mechanism, clone);
        var pullRequest = authorizedRequest("POST", RepositoryActionType.READ, mechanism, pull);

        accessLogRecorder.markFetchAsCloneHTTPS(cloneRequest, 0);
        accessLogRecorder.markFetchAsCloneHTTPS(pullRequest, 3);

        assertThat(clone.getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE);
        assertThat(pull.getRepositoryActionType()).isEqualTo(RepositoryActionType.PULL);
        assertThat(advertisement.getRepositoryActionType()).isEqualTo(RepositoryActionType.READ);
        verify(vcsAccessLogService).updateAccessLog(clone);
    }

    @ParameterizedTest
    @EnumSource(value = AuthenticationMechanism.class, names = { "PASSWORD", "PARTICIPATION_VCS_ACCESS_TOKEN", "USER_VCS_ACCESS_TOKEN", "REPOSITORY_VCS_ACCESS_TOKEN" })
    void overlappingHttpsPushesKeepTheirOwnCommitHashes(AuthenticationMechanism mechanism) {
        var first = entryOf(RepositoryActionType.PUSH);
        var second = entryOf(RepositoryActionType.PUSH);
        var firstRequest = authorizedRequest("POST", RepositoryActionType.WRITE, mechanism, first);
        var secondRequest = authorizedRequest("POST", RepositoryActionType.WRITE, mechanism, second);

        accessLogRecorder.completePushAccessLogHTTPS(firstRequest, "first-pushed");

        assertThat(first.getCommitHash()).isEqualTo("first-pushed");
        assertThat(second.getCommitHash()).isEmpty();
        accessLogRecorder.completePushAccessLogHTTPS(secondRequest, "second-pushed");
        assertThat(first.getCommitHash()).isEqualTo("first-pushed");
        assertThat(second.getCommitHash()).isEqualTo("second-pushed");
        verify(vcsAccessLogService).updateAccessLog(first);
        verify(vcsAccessLogService).updateAccessLog(second);
    }

    @ParameterizedTest
    @EnumSource(value = RepositoryActionType.class, names = { "READ", "WRITE" })
    void aFailedTransferInsertNeverAmendsAnEarlierOperation(RepositoryActionType action) {
        var earlier = entryOf(action == RepositoryActionType.WRITE ? RepositoryActionType.PUSH : RepositoryActionType.PULL);
        authorizedRequest("POST", action, AuthenticationMechanism.PASSWORD, earlier);
        when(vcsAccessLogService.createAccessLog(any(), any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("insert failed"));
        var failedRequest = new MockHttpServletRequest("POST", "/git/TEST/test-testuser.git/git-receive-pack");

        assertThatCode(() -> accessLogRecorder.saveVcsAccessLogForHTTPS(failedRequest, testRepositoryUri, testUser, action, Optional.of(testParticipation),
                AuthenticationMechanism.PASSWORD)).doesNotThrowAnyException();
        accessLogRecorder.completePushAccessLogHTTPS(failedRequest, "unrecorded");
        accessLogRecorder.markFetchAsCloneHTTPS(failedRequest, 0);

        assertThat(earlier.getCommitHash()).isEmpty();
        assertThat(earlier.getRepositoryActionType()).isEqualTo(action == RepositoryActionType.WRITE ? RepositoryActionType.PUSH : RepositoryActionType.PULL);
        verify(vcsAccessLogService, never()).updateAccessLog(any());
    }

    @Test
    void anSshFetchHookKeepsItsCommandEntryWhenAnotherCommandReusesTheSession() {
        var first = entryOf(RepositoryActionType.PULL);
        var second = entryOf(RepositoryActionType.PULL);
        ServerSession session = sessionOf(testUser, first);
        var hook = new LocalVCFetchPreUploadHookSSH(accessLogRecorder, session);
        lenient().when(session.getAttribute(SshConstants.VCS_ACCESS_LOG_KEY)).thenReturn(second);

        hook.onBeginNegotiateRound(null, List.of(), 0);

        assertThat(first.getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE);
        assertThat(second.getRepositoryActionType()).isEqualTo(RepositoryActionType.PULL);
    }

    private void markSshFetch(ServerSession session, int clientOffered) {
        new LocalVCFetchPreUploadHookSSH(accessLogRecorder, session).onBeginNegotiateRound(null, List.of(), clientOffered);
    }

    private VcsAccessLog entryOf(RepositoryActionType actionType) {
        return new VcsAccessLog(testUser, testParticipation, testUser.getName(), testUser.getEmail(), actionType, AuthenticationMechanism.SSH, "", "10.0.0.1");
    }

    private ServerSession sessionOf(User user, VcsAccessLog accessLog) {
        ServerSession session = mock(ServerSession.class);
        lenient().when(session.getAttribute(SshConstants.USER_KEY)).thenReturn(user);
        lenient().when(session.getAttribute(SshConstants.VCS_ACCESS_LOG_KEY)).thenReturn(accessLog);
        return session;
    }

    @Test
    void aSessionWithoutUser_isNotMarkedAsAClone() {
        markSshFetch(sessionOf(null, entryOf(RepositoryActionType.PULL)), 0);

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void aSessionWithoutPreliminaryLog_isNotMarkedAsAClone() {
        markSshFetch(sessionOf(testUser, null), 0);

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void aFetchOverSshWithoutOfferedObjects_isStoredAsAClone() {
        var accessLog = entryOf(RepositoryActionType.PULL);

        markSshFetch(sessionOf(testUser, accessLog), 0);

        assertThat(accessLog.getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE);
        verify(vcsAccessLogService).updateAccessLog(accessLog);
    }

    @Test
    void aFetchOverSshWithOfferedObjects_staysAPullAndIsNotStoredAgain() {
        var accessLog = entryOf(RepositoryActionType.PULL);

        markSshFetch(sessionOf(testUser, accessLog), 3);

        assertThat(accessLog.getRepositoryActionType()).isEqualTo(RepositoryActionType.PULL);
        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void theEntryOfAPush_isNeverTurnedIntoAClone() {
        var pushEntry = entryOf(RepositoryActionType.PUSH);

        // The session of a connection that carries several commands holds the entry of whichever command resolved last.
        markSshFetch(sessionOf(testUser, pushEntry), 0);

        assertThat(pushEntry.getRepositoryActionType()).isEqualTo(RepositoryActionType.PUSH);
        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void anEntryThatIsAlreadyACloneIsNotStoredAgain() {
        var cloneEntry = entryOf(RepositoryActionType.CLONE);

        markSshFetch(sessionOf(testUser, cloneEntry), 0);

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void theCommitOfAPushOverSsh_isStoredOnTheEntryOfTheCommand() {
        var pushEntry = entryOf(RepositoryActionType.PUSH);

        accessLogRecorder.completePushAccessLog(pushEntry, "0123456789abcdef");

        assertThat(pushEntry.getCommitHash()).isEqualTo("0123456789abcdef");
        verify(vcsAccessLogService).updateAccessLog(pushEntry);
    }

    @Test
    void theCommitOfAPush_isNeverStoredOnTheEntryOfAFetch() {
        var pullEntry = entryOf(RepositoryActionType.PULL);

        accessLogRecorder.completePushAccessLog(pullEntry, "0123456789abcdef");

        assertThat(pullEntry.getCommitHash()).isEmpty();
        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void aPushOverSshWithoutEntry_isIgnored() {
        assertThatCode(() -> accessLogRecorder.completePushAccessLog(null, "abc")).doesNotThrowAnyException();

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void cacheAttributesInSshSession_withAnUnreadableRepository_stillWritesTheEntry() {
        ServerSession session = mock(ServerSession.class);
        var stored = entryOf(RepositoryActionType.PULL);
        when(vcsAccessLogService.createAccessLog(eq(testUser), eq(testParticipation), eq(RepositoryActionType.PULL), eq(AuthenticationMechanism.SSH), eq(""), eq("10.0.0.1")))
                .thenReturn(stored);

        // The repository does not exist, so there is no commit hash to read. That must not cost the operation its entry.
        accessLogRecorder.cacheAttributesInSshSession(testUser, Optional.of(testParticipation), RepositoryActionType.READ, AuthenticationMechanism.SSH, "10.0.0.1",
                testRepositoryUri, session);

        verify(session).setAttribute(SshConstants.VCS_ACCESS_LOG_KEY, stored);
        verify(session).setAttribute(SshConstants.PARTICIPATION_KEY, testParticipation);
    }

    @Test
    void cacheAttributesInSshSession_writesAPushEntryForAWriteCommand() {
        ServerSession session = mock(ServerSession.class);
        var stored = entryOf(RepositoryActionType.PUSH);
        when(vcsAccessLogService.createAccessLog(eq(testUser), eq(testParticipation), eq(RepositoryActionType.PUSH), any(), any(), any())).thenReturn(stored);

        accessLogRecorder.cacheAttributesInSshSession(testUser, Optional.of(testParticipation), RepositoryActionType.WRITE, AuthenticationMechanism.SSH, "10.0.0.1",
                testRepositoryUri, session);

        verify(session).setAttribute(SshConstants.VCS_ACCESS_LOG_KEY, stored);
    }

    @Test
    void cacheAttributesInSshSession_whenTheEntryCannotBeWritten_stillCachesTheParticipation() {
        ServerSession session = mock(ServerSession.class);
        when(vcsAccessLogService.createAccessLog(any(), any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("the database is away"));

        assertThatCode(() -> accessLogRecorder.cacheAttributesInSshSession(testUser, Optional.of(testParticipation), RepositoryActionType.WRITE, AuthenticationMechanism.SSH,
                "10.0.0.1", testRepositoryUri, session)).doesNotThrowAnyException();

        verify(session).setAttribute(SshConstants.PARTICIPATION_KEY, testParticipation);
        verify(session, never()).setAttribute(eq(SshConstants.VCS_ACCESS_LOG_KEY), any());
    }

    @Test
    void cacheAttributesInSshSession_dropsTheStateOfTheCommandBefore() {
        ServerSession session = mock(ServerSession.class);

        accessLogRecorder.cacheAttributesInSshSession(testUser, Optional.of(testParticipation), RepositoryActionType.READ, AuthenticationMechanism.SSH, "10.0.0.1",
                testRepositoryUri, session);

        // whether or not the new state could be cached, the old one must not survive into this command
        verify(session).removeAttribute(SshConstants.VCS_ACCESS_LOG_KEY);
        verify(session).removeAttribute(SshConstants.PARTICIPATION_KEY);
    }

    @Test
    void cacheAttributesInSshSession_forARepositoryWithoutParticipation_leavesNoStateBehind() {
        ServerSession session = mock(ServerSession.class);

        accessLogRecorder.cacheAttributesInSshSession(testUser, Optional.empty(), RepositoryActionType.WRITE, AuthenticationMechanism.SSH, "10.0.0.1", testRepositoryUri, session);

        verify(session).removeAttribute(SshConstants.VCS_ACCESS_LOG_KEY);
        verify(session).removeAttribute(SshConstants.PARTICIPATION_KEY);
        verify(session, never()).setAttribute(any(), any());
        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void aUserWhoseNameIsTheBuildJobUserName_isLoggedOverSsh() {
        var user = new User();
        user.setLogin("buildjob_user");
        assertThat(user.getName()).isEqualTo("buildjob_user");
        var accessLog = entryOf(RepositoryActionType.PULL);

        // build agent sessions carry no user and get an audit entry of their own, so the name alone must not exclude anyone
        markSshFetch(sessionOf(user, accessLog), 0);

        verify(vcsAccessLogService).updateAccessLog(accessLog);
    }

    private static String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static String vcsAccessToken(char fill) {
        return "vcpat-" + String.valueOf(fill).repeat(44);
    }
}
