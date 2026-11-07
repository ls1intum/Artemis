package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.Locale;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.sshd.server.session.ServerSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.localvc.service.ssh.SshConstants;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
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

    private HttpServletRequest postRequest(String method) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        return request;
    }

    @Test
    void aRequestThatIsNoPost_isNotLogged() {
        accessLogRecorder.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(postRequest("GET"), basicAuth("testuser", "pw"), 0);

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void aBuildAgentClone_isNotRelabeledAsTheCloneOfAStudent() {
        HttpServletRequest request = postRequest("POST");
        when(request.getAttribute("artemis.buildAgentClone")).thenReturn("build-agent-1");

        accessLogRecorder.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(request, basicAuth("build-agent-1", "bjct-token"), 0);

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void theBuildJobUser_isNotLogged() {
        HttpServletRequest request = postRequest("POST");

        accessLogRecorder.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(request, basicAuth(LocalVCBuildAgentAuthenticationService.BUILD_USER_NAME, "pw"), 0);

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void aMalformedAuthorizationHeader_isIgnored() {
        HttpServletRequest request = postRequest("POST");

        assertThatCode(() -> accessLogRecorder.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(request, "not-a-basic-header", 0)).doesNotThrowAnyException();

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void aStudentFetchWithoutOfferedObjects_isCompletedAsAClone() {
        HttpServletRequest request = studentFetchRequest();

        accessLogRecorder.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(request, basicAuth("testuser", "pw"), 0);

        ArgumentCaptor<LocalVCRepositoryUri> repositoryUri = ArgumentCaptor.forClass(LocalVCRepositoryUri.class);
        verify(vcsAccessLogService).updateRepositoryActionType(repositoryUri.capture(), eq(RepositoryActionType.CLONE));
        assertThat(repositoryUri.getValue().getProjectKey()).isEqualTo("TEST");
    }

    @Test
    void aStudentFetchWithOfferedObjects_isCompletedAsAPull() {
        HttpServletRequest request = studentFetchRequest();

        accessLogRecorder.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(request, basicAuth("testuser", "pw"), 3);

        ArgumentCaptor<LocalVCRepositoryUri> repositoryUri = ArgumentCaptor.forClass(LocalVCRepositoryUri.class);
        verify(vcsAccessLogService).updateRepositoryActionType(repositoryUri.capture(), eq(RepositoryActionType.PULL));
        assertThat(repositoryUri.getValue().getProjectKey()).isEqualTo("TEST");
    }

    private HttpServletRequest studentFetchRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/git/TEST/test-testuser.git/git-upload-pack");
        return request;
    }

    private ServerSession sessionOf(User user, VcsAccessLog accessLog) {
        ServerSession session = mock(ServerSession.class);
        when(session.getAttribute(SshConstants.USER_KEY)).thenReturn(user);
        lenient().when(session.getAttribute(SshConstants.VCS_ACCESS_LOG_KEY)).thenReturn(accessLog);
        return session;
    }

    @Test
    void aSessionWithoutUser_isNotLogged() {
        accessLogRecorder.updateAndStoreVCSAccessLogForCloneAndPullSSH(sessionOf(null, null), 0);

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void aSessionWithoutPreliminaryLog_isNotLogged() {
        accessLogRecorder.updateAndStoreVCSAccessLogForCloneAndPullSSH(sessionOf(testUser, null), 0);

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void aCloneAndAPull_areLoggedWithTheirRepositoryActionType() {
        var cloneLog = new VcsAccessLog();
        var pullLog = new VcsAccessLog();

        accessLogRecorder.updateAndStoreVCSAccessLogForCloneAndPullSSH(sessionOf(testUser, cloneLog), 0);
        accessLogRecorder.updateAndStoreVCSAccessLogForCloneAndPullSSH(sessionOf(testUser, pullLog), 3);

        assertThat(cloneLog.getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE);
        assertThat(pullLog.getRepositoryActionType()).isEqualTo(RepositoryActionType.PULL);
        verify(vcsAccessLogService).saveVcsAccesslog(cloneLog);
        verify(vcsAccessLogService).saveVcsAccesslog(pullLog);
    }

    @Test
    void aSessionThatFailsWhileBeingRead_doesNotFailTheClone() {
        ServerSession session = mock(ServerSession.class);
        when(session.getAttribute(SshConstants.USER_KEY)).thenThrow(new IllegalStateException("session closed"));

        assertThatCode(() -> accessLogRecorder.updateAndStoreVCSAccessLogForCloneAndPullSSH(session, 0)).doesNotThrowAnyException();

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void cacheAttributesInSshSession_withoutReadableRepository_cachesNothing() {
        ServerSession session = mock(ServerSession.class);

        Optional<ProgrammingExerciseParticipation> participation = Optional.of(testParticipation);

        accessLogRecorder.cacheAttributesInSshSession(testUser, participation, RepositoryActionType.READ, AuthenticationMechanism.SSH, "10.0.0.1", testRepositoryUri, session);

        verify(session, never()).setAttribute(any(), any());
    }

    private static String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static String vcsAccessToken(char fill) {
        return "vcpat-" + String.valueOf(fill).repeat(44);
    }
}
