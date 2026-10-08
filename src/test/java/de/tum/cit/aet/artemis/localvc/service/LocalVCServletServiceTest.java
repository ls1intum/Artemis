package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.util.ReflectionTestUtils;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCInternalException;
import de.tum.cit.aet.artemis.localvc.service.ssh.SshConstants;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryVCSAccessToken;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.dto.GitRepositoryAccessDTO;
import de.tum.cit.aet.artemis.programming.repository.ParticipationVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.RepositoryVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTestCaseChangedService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

@ExtendWith(MockitoExtension.class)
class LocalVCServletServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private RepositoryAccessService repositoryAccessService;

    @Mock
    private AuthorizationCheckService authorizationCheckService;

    @Mock
    private ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    @Mock
    private AuxiliaryRepositoryService auxiliaryRepositoryService;

    @Mock
    private ContinuousIntegrationTriggerService ciTriggerService;

    @Mock
    private ProgrammingSubmissionService programmingSubmissionService;

    @Mock
    private ProgrammingSubmissionMessagingService programmingSubmissionMessagingService;

    @Mock
    private ProgrammingExerciseTestCaseChangedService programmingExerciseTestCaseChangedService;

    @Mock
    private ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository;

    @Mock
    private RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository;

    @Mock
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Mock
    private VcsAccessLogService vcsAccessLogService;

    @Mock
    private UserVcsAccessTokenService userVcsAccessTokenService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private ExerciseVersionService exerciseVersionService;

    @Mock
    private MailSendingService mailSendingService;

    @Mock
    private DistributedDataProvider distributedDataProvider;

    @Mock
    private DistributedMap<Long, Boolean> httpsCloneEmailCache;

    @InjectMocks
    private LocalVCServletService localVCServletService;

    private User testUser;

    private ProgrammingExercise testExercise;

    private ProgrammingExerciseStudentParticipation testParticipation;

    private LocalVCRepositoryUri testRepositoryUri;

    @BeforeEach
    void setUp() throws Exception {
        testUser = new User();
        testUser.setId(1L);
        testUser.setLogin("testuser");
        testUser.setEmail("testuser@example.com");
        testUser.setLangKey("en");
        testUser.setFirstName("Test");
        testUser.setLastName("User");

        // Create a course with required properties
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
        lenient().when(testRepositoryUri.getRelativeRepositoryPath()).thenReturn(java.nio.file.Path.of("test/repo"));
        lenient().when(testRepositoryUri.toString()).thenReturn("http://localhost/git/TEST/test-testuser.git");

        lenient().when(distributedDataProvider.<Long, Boolean>getExpiringMap(anyString(), any())).thenReturn(httpsCloneEmailCache);

        // Setup the VcsAccessLogService as an Optional containing the mock
        ReflectionTestUtils.setField(localVCServletService, "vcsAccessLogService", Optional.of(vcsAccessLogService));

        ReflectionTestUtils.setField(localVCServletService, "localVCBasePath", java.nio.file.Path.of("/tmp/test-repos"));
        ReflectionTestUtils.setField(localVCServletService, "localVCBaseUri", URI.create("http://localhost"));
    }

    @Test
    void writesTheAccessLogWhenAFetchAuthenticationFails() {
        HttpServletRequest request = failedFetchRequest();
        when(userRepository.findOneByLogin("testuser")).thenReturn(Optional.of(testUser));
        when(programmingExerciseParticipationService.getParticipationReferenceForRepository(anyString(), anyString(), eq(testExercise.getProjectKey())))
                .thenReturn(Optional.of(testParticipation));

        localVCServletService.createVCSAccessLogForFailedAuthenticationAttempt(request);

        verify(vcsAccessLogService).saveAccessLog(eq(testUser), eq(testParticipation), eq(RepositoryActionType.CLONE_FAIL), eq(AuthenticationMechanism.PASSWORD), anyString(),
                eq("10.0.0.1"));
        // Neither the exercise nor the participation's submissions are needed to record that a repository was touched.
        verifyNoInteractions(programmingExerciseRepository);
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
        assertThatCode(() -> localVCServletService.createVCSAccessLogForFailedAuthenticationAttempt(request)).doesNotThrowAnyException();
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
    void testAuthenticationContextSession_getIpAddress() {
        ServerSession session = mock(ServerSession.class);
        when(session.getClientAddress()).thenReturn(java.net.InetSocketAddress.createUnresolved("192.168.1.1", 22));

        AuthenticationContext.Session sessionContext = new AuthenticationContext.Session(session);

        String ipAddress = sessionContext.getIpAddress();

        assertThat(ipAddress).contains("192.168.1.1");
    }

    @Test
    void testAuthenticationContextRequest_getIpAddress() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        AuthenticationContext.Request requestContext = new AuthenticationContext.Request(request);

        String ipAddress = requestContext.getIpAddress();

        assertThat(ipAddress).isEqualTo("10.0.0.1");
    }

    @Test
    void testSaveFailedAccessVcsAccessLog_withSshSession() throws Exception {
        ServerSession session = mock(ServerSession.class);
        when(session.getClientAddress()).thenReturn(java.net.InetSocketAddress.createUnresolved("10.0.0.5", 22));

        AuthenticationContext.Session context = new AuthenticationContext.Session(session);

        // Call the public method directly (no reflection needed)
        localVCServletService.saveFailedAccessVcsAccessLog(context, "student1", testExercise.getId(), testRepositoryUri, testUser, RepositoryActionType.READ);

        verify(vcsAccessLogService).saveAccessLog(eq(testUser), any(), eq(RepositoryActionType.CLONE_FAIL), eq(AuthenticationMechanism.SSH), anyString(), anyString());
    }

    @Test
    void testResolveAuthenticationMechanismFromSessionOrRequest_withSession() throws Exception {
        ServerSession session = mock(ServerSession.class);
        AuthenticationContext.Session context = new AuthenticationContext.Session(session);

        // Use reflection to call the private method for testing
        java.lang.reflect.Method method = LocalVCServletService.class.getDeclaredMethod("resolveAuthenticationMechanismFromSessionOrRequest", AuthenticationContext.class,
                User.class, LocalVCRepositoryUri.class);
        method.setAccessible(true);

        AuthenticationMechanism result = (AuthenticationMechanism) method.invoke(localVCServletService, context, testUser, testRepositoryUri);

        assertThat(result).isEqualTo(AuthenticationMechanism.SSH);
    }

    @Test
    void testResolveAuthenticationMechanismFromSessionOrRequest_withRequestAndMissingHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        AuthenticationContext.Request context = new AuthenticationContext.Request(request);

        // Use reflection to call the private method for testing
        java.lang.reflect.Method method = LocalVCServletService.class.getDeclaredMethod("resolveAuthenticationMechanismFromSessionOrRequest", AuthenticationContext.class,
                User.class, LocalVCRepositoryUri.class);
        method.setAccessible(true);

        AuthenticationMechanism result = (AuthenticationMechanism) method.invoke(localVCServletService, context, testUser, testRepositoryUri);

        assertThat(result).isEqualTo(AuthenticationMechanism.AUTH_HEADER_MISSING);
    }

    @Test
    void testResolveAuthenticationMechanismFromSessionOrRequest_withRequestAndValidHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String authHeader = "Basic " + java.util.Base64.getEncoder().encodeToString("user:password".getBytes());
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authHeader);

        AuthenticationContext.Request context = new AuthenticationContext.Request(request);

        // Use reflection to call the private method for testing
        java.lang.reflect.Method method = LocalVCServletService.class.getDeclaredMethod("resolveAuthenticationMechanismFromSessionOrRequest", AuthenticationContext.class,
                User.class, LocalVCRepositoryUri.class);
        method.setAccessible(true);

        AuthenticationMechanism result = (AuthenticationMechanism) method.invoke(localVCServletService, context, testUser, testRepositoryUri);

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

        // Use reflection to call the private method for testing
        java.lang.reflect.Method method = LocalVCServletService.class.getDeclaredMethod("resolveAuthenticationMechanismFromSessionOrRequest", AuthenticationContext.class,
                User.class, LocalVCRepositoryUri.class);
        method.setAccessible(true);

        AuthenticationMechanism result = (AuthenticationMechanism) method.invoke(localVCServletService, context, testUser, testRepositoryUri);

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
        when(repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(testUser.getId(), "http://localhost/git/TEST/test-testuser.git"))
                .thenReturn(Optional.of(repositoryToken));

        AuthenticationContext.Request context = new AuthenticationContext.Request(request);

        java.lang.reflect.Method method = LocalVCServletService.class.getDeclaredMethod("resolveAuthenticationMechanismFromSessionOrRequest", AuthenticationContext.class,
                User.class, LocalVCRepositoryUri.class);
        method.setAccessible(true);

        AuthenticationMechanism result = (AuthenticationMechanism) method.invoke(localVCServletService, context, testUser, testRepositoryUri);

        assertThat(result).isEqualTo(AuthenticationMechanism.REPOSITORY_VCS_ACCESS_TOKEN);
    }

    @Test
    void testAuthenticationMechanismAuthHeaderMissing() {
        // Test that the new AUTH_HEADER_MISSING enum value exists and can be used
        AuthenticationMechanism mechanism = AuthenticationMechanism.AUTH_HEADER_MISSING;

        assertThat(mechanism).isNotNull();
        assertThat(mechanism.name()).isEqualTo("AUTH_HEADER_MISSING");
    }

    @Test
    void testRepositoryActionTypeForFailedOperations() {
        // Test the mapping of repository actions for failed operations
        // WRITE -> PUSH_FAIL, READ -> CLONE_FAIL
        assertThat(RepositoryActionType.PUSH_FAIL).isNotNull();
        assertThat(RepositoryActionType.CLONE_FAIL).isNotNull();
    }

    @Test
    void testUpdateAndStoreVCSAccessLogForCloneAndPullHTTPS_sendsEmailOnPasswordCloneWhenNotInCache() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/git/TEST/test-testuser.git/git-upload-pack");
        lenient().doReturn(testUser).when(request).getAttribute("artemis.authenticatedUser");
        lenient().doReturn(AuthenticationMechanism.PASSWORD).when(request).getAttribute("artemis.authenticationMechanism");

        String password = "plain-password";
        String authHeader = "Basic " + java.util.Base64.getEncoder().encodeToString(("testuser:" + password).getBytes());

        // putIfAbsent return null, first clone with HTTPS
        when(httpsCloneEmailCache.putIfAbsent(eq(testUser.getId()), eq(Boolean.TRUE), any(Duration.class))).thenReturn(null);

        // clientOffered == 0 means CLONE operation
        localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(request, authHeader, 0);

        verify(mailSendingService).buildAndSendAsync(any(MailRecipientDTO.class), eq("email.httpsCloneTip.title"), eq("mail/httpsCloneTipEmail"), eq(Map.of()));
    }

    @Test
    void testUpdateAndStoreVCSAccessLogForCloneAndPullHTTPS_doesNotSendEmailWhenAlreadyInCache() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/git/TEST/test-testuser.git/git-upload-pack");
        lenient().doReturn(testUser).when(request).getAttribute("artemis.authenticatedUser");
        lenient().doReturn(AuthenticationMechanism.PASSWORD).when(request).getAttribute("artemis.authenticationMechanism");

        String password = "plain-password";
        String authHeader = "Basic " + java.util.Base64.getEncoder().encodeToString(("testuser:" + password).getBytes());

        when(httpsCloneEmailCache.putIfAbsent(eq(testUser.getId()), eq(Boolean.TRUE), any(Duration.class))).thenReturn(Boolean.TRUE);

        localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(request, authHeader, 0);

        verify(mailSendingService, never()).buildAndSendAsync(any(), any(), any(), any());
    }

    @Test
    void testUpdateAndStoreVCSAccessLogForCloneAndPullHTTPS_doesNotSendEmailWhenUsingToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/git/TEST/test-testuser.git/git-upload-pack");
        lenient().doReturn(testUser).when(request).getAttribute("artemis.authenticatedUser");
        lenient().doReturn(AuthenticationMechanism.USER_VCS_ACCESS_TOKEN).when(request).getAttribute("artemis.authenticationMechanism");

        String token = "vcpat-" + "a".repeat(44);
        String authHeader = "Basic " + java.util.Base64.getEncoder().encodeToString(("testuser:" + token).getBytes());

        localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(request, authHeader, 0);

        verifyNoInteractions(mailSendingService);
        verifyNoInteractions(httpsCloneEmailCache);
    }

    @Test
    void testUpdateAndStoreVCSAccessLogForCloneAndPullHTTPS_doesNotSendEmailOnPullOperation() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/git/TEST/test-testuser.git/git-upload-pack");

        String password = "plain-password";
        String authHeader = "Basic " + java.util.Base64.getEncoder().encodeToString(("testuser:" + password).getBytes());

        // clientOffered > 0 means a PULL operation instead of clone
        localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(request, authHeader, 1);

        verifyNoInteractions(mailSendingService);
        verifyNoInteractions(httpsCloneEmailCache);
    }

    @Test
    void resolveRepository_withAPathEscapingTheBaseDirectory_isNotFound(@TempDir java.nio.file.Path baseDir) {
        ReflectionTestUtils.setField(localVCServletService, "localVCBasePath", baseDir);

        // A path that climbs out of the base directory must be refused before the file system is touched, not resolved to whatever lies outside.
        assertThatExceptionOfType(RepositoryNotFoundException.class).isThrownBy(() -> localVCServletService.resolveRepository("../../../../../../etc/passwd"));
    }

    @Test
    void resolveRepository_withAPathThatDoesNotExist_isNotFound(@TempDir java.nio.file.Path baseDir) {
        ReflectionTestUtils.setField(localVCServletService, "localVCBasePath", baseDir);

        assertThatExceptionOfType(RepositoryNotFoundException.class).isThrownBy(() -> localVCServletService.resolveRepository("ABC/abc-exercise.git"));
    }

    @Test
    void resolveRepository_withASymlinkLeadingOutOfTheBaseDirectory_isNotFound(@TempDir java.nio.file.Path baseDir, @TempDir java.nio.file.Path outside) throws Exception {
        // The path itself stays inside the base directory, so only resolving symlinks reveals that the repository is elsewhere.
        java.nio.file.Files.createDirectories(outside.resolve("escaped.git"));
        java.nio.file.Path project = java.nio.file.Files.createDirectories(baseDir.resolve("ABC"));
        java.nio.file.Files.createSymbolicLink(project.resolve("abc-exercise.git"), outside.resolve("escaped.git"));
        ReflectionTestUtils.setField(localVCServletService, "localVCBasePath", baseDir);

        assertThatExceptionOfType(RepositoryNotFoundException.class).isThrownBy(() -> localVCServletService.resolveRepository("ABC/abc-exercise.git"));
    }

    @Test
    void resolveRepository_withCarriageReturnsInThePath_isNotFoundAndLogsNoLineBreak(@TempDir java.nio.file.Path baseDir) {
        // A repository path comes straight from the request. Logging it unchanged would let a caller forge log lines (CWE-117), so it is sanitised before it is logged.
        ReflectionTestUtils.setField(localVCServletService, "localVCBasePath", baseDir);
        Logger logger = (Logger) LoggerFactory.getLogger(LocalVCServletService.class);
        ListAppender<ILoggingEvent> loggedEvents = new ListAppender<>();
        loggedEvents.start();
        logger.addAppender(loggedEvents);

        try {
            assertThatExceptionOfType(RepositoryNotFoundException.class).isThrownBy(() -> localVCServletService.resolveRepository("ABC\r\ninjected/abc-exercise.git"));

            assertThat(loggedEvents.list).as("the failed lookup is logged").isNotEmpty();
            assertThat(loggedEvents.list).allSatisfy(
                    event -> assertThat(event.getFormattedMessage()).as("no log line may carry a line break from the request").doesNotContain("\r").doesNotContain("\n"));
            assertThat(loggedEvents.list).as("the path is still identifiable in the log, with the line breaks replaced")
                    .anyMatch(event -> event.getFormattedMessage().contains("ABC__injected/abc-exercise.git"));
        }
        finally {
            logger.detachAppender(loggedEvents);
        }
    }

    private static String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static String vcsAccessToken(char fill) {
        return "vcpat-" + String.valueOf(fill).repeat(44);
    }

    private HttpServletRequest gitFetchRequest(String authorizationHeader) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getRequestURI()).thenReturn("/git/TEST/test-testuser.git/git-upload-pack");
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authorizationHeader);
        return request;
    }

    /** A node without local CI, which is the only kind of node where the shared build agent credential can still apply. */
    private void configureNodeWithoutLocalCI(String buildAgentUsername, String buildAgentPassword, boolean useSshForBuildAgent) {
        ReflectionTestUtils.setField(localVCServletService, "distributedDataAccessService", Optional.empty());
        ReflectionTestUtils.setField(localVCServletService, "buildAgentAddressRegistryService", Optional.empty());
        ReflectionTestUtils.setField(localVCServletService, "buildJobCloneTokenService", Optional.empty());
        ReflectionTestUtils.setField(localVCServletService, "buildAgentGitUsername", buildAgentUsername);
        ReflectionTestUtils.setField(localVCServletService, "buildAgentGitPassword", buildAgentPassword);
        ReflectionTestUtils.setField(localVCServletService, "useSshForBuildAgent", useSshForBuildAgent);
    }

    @Test
    void authenticateAndAuthorizeGitRequest_withTheSharedBuildAgentCredential_skipsTheRateLimitAndTheAuthorization() throws Exception {
        configureNodeWithoutLocalCI("build-agent", "a-long-shared-secret", false);
        HttpServletRequest request = gitFetchRequest(basicAuth("build-agent", "a-long-shared-secret"));

        assertThatCode(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ)).doesNotThrowAnyException();

        verifyNoInteractions(rateLimitService, userRepository, repositoryAccessService);
    }

    @Test
    void authenticateAndAuthorizeGitRequest_withAWrongBuildAgentPassword_isHandledLikeAnyOtherCredential() {
        configureNodeWithoutLocalCI("build-agent", "a-long-shared-secret", false);
        HttpServletRequest request = gitFetchRequest(basicAuth("build-agent", "another-secret"));

        // not accepted by the shortcut, so the request goes on to look up the exercise of the repository, which does not exist here
        assertThatExceptionOfType(LocalVCInternalException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ));
    }

    @Test
    void authenticateAndAuthorizeGitRequest_withABlankConfiguredBuildAgentPassword_doesNotAcceptABlankPassword() {
        configureNodeWithoutLocalCI("build-agent", "", false);
        HttpServletRequest request = gitFetchRequest(basicAuth("build-agent", ""));

        // a blank configured password would otherwise match the equally blank password of anyone who knows the user name
        assertThatExceptionOfType(LocalVCInternalException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ));
    }

    @Test
    void authenticateAndAuthorizeGitRequest_whenBuildAgentsUseSsh_doesNotAcceptTheSharedBuildAgentCredential() {
        configureNodeWithoutLocalCI("build-agent", "a-long-shared-secret", true);
        HttpServletRequest request = gitFetchRequest(basicAuth("build-agent", "a-long-shared-secret"));

        assertThatExceptionOfType(LocalVCInternalException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ));
    }

    @Test
    void authenticateAndAuthorizeGitRequest_withTheSharedBuildAgentCredentialForAPush_isNotAccepted() {
        configureNodeWithoutLocalCI("build-agent", "a-long-shared-secret", false);
        HttpServletRequest request = gitFetchRequest(basicAuth("build-agent", "a-long-shared-secret"));

        // the shortcut only grants read access
        assertThatExceptionOfType(LocalVCInternalException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE));
    }

    @Test
    void saveFailedAccessVcsAccessLog_whenTheParticipationCannotBeResolved_writesNoLogAndDoesNotFail() {
        ServerSession session = mock(ServerSession.class);
        when(programmingExerciseParticipationService.fetchParticipationByRepository(anyString(), anyString(), anyLong()))
                .thenThrow(new EntityNotFoundException("participation", 1L));

        assertThatCode(() -> localVCServletService.saveFailedAccessVcsAccessLog(new AuthenticationContext.Session(session), "testuser", testExercise.getId(), testRepositoryUri,
                testUser, RepositoryActionType.WRITE)).doesNotThrowAnyException();

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void createVCSAccessLogForFailedAuthenticationAttempt_forAnUnknownUser_writesNoLog() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(basicAuth("testuser", "wrong-password"));
        when(userRepository.findOneByLogin("testuser")).thenReturn(Optional.empty());

        assertThatCode(() -> localVCServletService.createVCSAccessLogForFailedAuthenticationAttempt(request)).doesNotThrowAnyException();

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void resolveAuthenticationMechanism_forATokenOfNeitherTheUserNorTheRepository_isTheParticipationToken() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(basicAuth("user", vcsAccessToken('c')));
        when(userVcsAccessTokenService.findToken(testUser.getId())).thenReturn(vcsAccessToken('a'));
        Method method = LocalVCServletService.class.getDeclaredMethod("resolveAuthenticationMechanismFromSessionOrRequest", AuthenticationContext.class, User.class,
                LocalVCRepositoryUri.class);
        method.setAccessible(true);

        AuthenticationMechanism result = (AuthenticationMechanism) method.invoke(localVCServletService, new AuthenticationContext.Request(request), testUser, testRepositoryUri);

        assertThat(result).isEqualTo(AuthenticationMechanism.PARTICIPATION_VCS_ACCESS_TOKEN);
    }

    @Nested
    class ParticipationVcsAccessTokenAuthentication {

        private static final long TEAM_PARTICIPATION_ID = 7L;

        private static final long PRACTICE_PARTICIPATION_ID = 5L;

        private GitRepositoryAccessDTO exerciseOfMode(ExerciseMode mode) {
            return new GitRepositoryAccessDTO(testExercise.getId(), 1L, mode, null, null, null, null, null, null, null);
        }

        private ProgrammingExerciseStudentParticipation participation(long id, boolean practiceMode) {
            var participation = new ProgrammingExerciseStudentParticipation();
            participation.setId(id);
            participation.setPracticeMode(practiceMode);
            return participation;
        }

        private boolean authenticate(String providedToken, GitRepositoryAccessDTO exercise, Supplier<ProgrammingExerciseParticipation> requestedParticipation) throws Exception {
            Method method = LocalVCServletService.class.getDeclaredMethod("tryAuthenticationWithParticipationVCSAccessToken", User.class, String.class,
                    GitRepositoryAccessDTO.class, Supplier.class);
            method.setAccessible(true);
            return (boolean) method.invoke(localVCServletService, testUser, providedToken, exercise, requestedParticipation);
        }

        @Test
        void teamRepository_acceptsTheTokenOfTheTeamParticipationOfTheUser() throws Exception {
            String token = vcsAccessToken('t');
            when(programmingExerciseParticipationService.findTeamParticipationByExerciseAndUser(testExercise.getId(), testUser))
                    .thenReturn(Optional.of(participation(TEAM_PARTICIPATION_ID, false)));
            when(participationVCSAccessTokenRepository.findTokenByUserIdAndParticipationId(testUser.getId(), TEAM_PARTICIPATION_ID)).thenReturn(Optional.of(token));

            assertThat(authenticate(token, exerciseOfMode(ExerciseMode.TEAM), () -> participation(TEAM_PARTICIPATION_ID, false))).isTrue();
        }

        @Test
        void practiceRepositoryOfATeamExercise_acceptsOnlyTheTokenOfThePracticeParticipation() throws Exception {
            String practiceToken = vcsAccessToken('p');
            String teamToken = vcsAccessToken('t');
            when(participationVCSAccessTokenRepository.findTokenByUserIdAndParticipationId(testUser.getId(), PRACTICE_PARTICIPATION_ID)).thenReturn(Optional.of(practiceToken));
            Supplier<ProgrammingExerciseParticipation> practiceRepository = () -> participation(PRACTICE_PARTICIPATION_ID, true);

            assertThat(authenticate(practiceToken, exerciseOfMode(ExerciseMode.TEAM), practiceRepository)).isTrue();
            assertThat(authenticate(teamToken, exerciseOfMode(ExerciseMode.TEAM), practiceRepository)).isFalse();

            // the practice repository belongs to the student alone, so the team is never consulted
            verify(programmingExerciseParticipationService, never()).findTeamParticipationByExerciseAndUser(anyLong(), any(User.class));
        }

        @Test
        void individualRepository_acceptsTheTokenOfTheRequestedParticipation() throws Exception {
            String token = vcsAccessToken('i');
            when(participationVCSAccessTokenRepository.findTokenByUserIdAndParticipationId(testUser.getId(), 3L)).thenReturn(Optional.of(token));

            assertThat(authenticate(token, exerciseOfMode(ExerciseMode.INDIVIDUAL), () -> participation(3L, false))).isTrue();
            assertThat(authenticate(vcsAccessToken('x'), exerciseOfMode(ExerciseMode.INDIVIDUAL), () -> participation(3L, false))).isFalse();
        }

        @Test
        void teamRepository_withoutATeamOfTheUser_failsTheAuthentication() throws Exception {
            when(programmingExerciseParticipationService.findTeamParticipationByExerciseAndUser(testExercise.getId(), testUser)).thenThrow(new EntityNotFoundException("team", 1L));

            assertThatThrownBy(() -> authenticate(vcsAccessToken('t'), exerciseOfMode(ExerciseMode.TEAM), () -> participation(TEAM_PARTICIPATION_ID, false)))
                    .isInstanceOf(InvocationTargetException.class).hasCauseInstanceOf(LocalVCAuthException.class);
        }

        @Test
        void aPasswordIsNotLookedUpAsAToken() throws Exception {
            assertThat(authenticate("a-password", exerciseOfMode(ExerciseMode.TEAM), () -> participation(TEAM_PARTICIPATION_ID, false))).isFalse();

            verifyNoInteractions(participationVCSAccessTokenRepository, programmingExerciseParticipationService);
        }
    }

    @Nested
    class HttpsCloneAccessLog {

        private HttpServletRequest postRequest(String method) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getMethod()).thenReturn(method);
            return request;
        }

        @Test
        void aRequestThatIsNoPost_isNotLogged() {
            localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(postRequest("GET"), basicAuth("testuser", "pw"), 0);

            verifyNoInteractions(vcsAccessLogService, mailSendingService);
        }

        @Test
        void aBuildAgentClone_isNotRelabeledAsTheCloneOfAStudent() {
            HttpServletRequest request = postRequest("POST");
            when(request.getAttribute("artemis.buildAgentClone")).thenReturn("build-agent-1");

            localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(request, basicAuth("build-agent-1", "bjct-token"), 0);

            verifyNoInteractions(vcsAccessLogService, mailSendingService);
        }

        @Test
        void theBuildJobUser_isNotLogged() {
            HttpServletRequest request = postRequest("POST");

            localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(request, basicAuth(LocalVCServletService.BUILD_USER_NAME, "pw"), 0);

            verifyNoInteractions(vcsAccessLogService, mailSendingService);
        }

        @Test
        void aMalformedAuthorizationHeader_isIgnored() {
            HttpServletRequest request = postRequest("POST");

            assertThatCode(() -> localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(request, "not-a-basic-header", 0)).doesNotThrowAnyException();

            verifyNoInteractions(vcsAccessLogService, mailSendingService);
        }

        @Test
        void aFailingMailTip_doesNotFailTheClone() {
            HttpServletRequest request = postRequest("POST");
            when(request.getRequestURI()).thenReturn("/git/TEST/test-testuser.git/git-upload-pack");
            lenient().doReturn(testUser).when(request).getAttribute("artemis.authenticatedUser");
            lenient().doReturn(AuthenticationMechanism.PASSWORD).when(request).getAttribute("artemis.authenticationMechanism");
            when(httpsCloneEmailCache.putIfAbsent(eq(testUser.getId()), eq(Boolean.TRUE), any(Duration.class))).thenReturn(null);
            doThrow(new IllegalStateException("mail server away")).when(mailSendingService).buildAndSendAsync(any(), any(), any(), any());

            assertThatCode(() -> localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(request, basicAuth("testuser", "pw"), 0)).doesNotThrowAnyException();
        }

        @Test
        void aUserWithoutAnEmailAddress_isNotSentAMailTip() {
            testUser.setEmail(" ");
            HttpServletRequest request = postRequest("POST");
            when(request.getRequestURI()).thenReturn("/git/TEST/test-testuser.git/git-upload-pack");
            lenient().doReturn(testUser).when(request).getAttribute("artemis.authenticatedUser");
            lenient().doReturn(AuthenticationMechanism.PASSWORD).when(request).getAttribute("artemis.authenticationMechanism");

            localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(request, basicAuth("testuser", "pw"), 0);

            verifyNoInteractions(mailSendingService, httpsCloneEmailCache);
        }
    }

    @Nested
    class SshCloneAccessLog {

        private ServerSession sessionOf(User user, VcsAccessLog accessLog) {
            ServerSession session = mock(ServerSession.class);
            when(session.getAttribute(SshConstants.USER_KEY)).thenReturn(user);
            lenient().when(session.getAttribute(SshConstants.VCS_ACCESS_LOG_KEY)).thenReturn(accessLog);
            return session;
        }

        @Test
        void aSessionWithoutUser_isNotLogged() {
            localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullSSH(sessionOf(null, null), 0);

            verifyNoInteractions(vcsAccessLogService);
        }

        @Test
        void aSessionWithoutPreliminaryLog_isNotLogged() {
            localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullSSH(sessionOf(testUser, null), 0);

            verifyNoInteractions(vcsAccessLogService);
        }

        @Test
        void aCloneAndAPull_areLoggedWithTheirRepositoryActionType() {
            var cloneLog = new VcsAccessLog();
            var pullLog = new VcsAccessLog();

            localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullSSH(sessionOf(testUser, cloneLog), 0);
            localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullSSH(sessionOf(testUser, pullLog), 3);

            assertThat(cloneLog.getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE);
            assertThat(pullLog.getRepositoryActionType()).isEqualTo(RepositoryActionType.PULL);
            verify(vcsAccessLogService).saveVcsAccesslog(cloneLog);
            verify(vcsAccessLogService).saveVcsAccesslog(pullLog);
        }

        @Test
        void aSessionThatFailsWhileBeingRead_doesNotFailTheClone() {
            ServerSession session = mock(ServerSession.class);
            when(session.getAttribute(SshConstants.USER_KEY)).thenThrow(new IllegalStateException("session closed"));

            assertThatCode(() -> localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullSSH(session, 0)).doesNotThrowAnyException();

            verifyNoInteractions(vcsAccessLogService);
        }

        @Test
        void cacheAttributesInSshSession_withoutReadableRepository_cachesNothing() {
            ServerSession session = mock(ServerSession.class);

            Optional<ProgrammingExerciseParticipation> participation = Optional.of(testParticipation);

            localVCServletService.cacheAttributesInSshSession(testUser, participation, RepositoryActionType.READ, AuthenticationMechanism.SSH, "10.0.0.1", testRepositoryUri,
                    session);

            verify(session, never()).setAttribute(any(), any());
        }
    }

    @Nested
    class BuildAgentDetection {

        private HttpServletRequest requestOf(String authorizationHeader) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            lenient().when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authorizationHeader);
            return request;
        }

        @Test
        void aRequestAuthorizedAsABuildAgentClone_isABuildAgentClone() {
            HttpServletRequest request = requestOf(basicAuth("student", "pw"));
            when(request.getAttribute("artemis.buildAgentClone")).thenReturn("build-agent-1");

            assertThat(localVCServletService.isBuildAgentClone(request)).isTrue();
        }

        @Test
        void theBuildJobUser_isABuildAgentClone() {
            assertThat(localVCServletService.isBuildAgentClone(requestOf(basicAuth(LocalVCServletService.BUILD_USER_NAME, "pw")))).isTrue();
        }

        @Test
        void theConfiguredBuildAgentUser_isABuildAgentCloneAndAStudentIsNot() {
            ReflectionTestUtils.setField(localVCServletService, "buildAgentGitUsername", "custom-agent");

            assertThat(localVCServletService.isBuildAgentClone(requestOf(basicAuth("custom-agent", "pw")))).isTrue();
            assertThat(localVCServletService.isBuildAgentClone(requestOf(basicAuth("student", "pw")))).isFalse();
        }

        @Test
        void aRequestWithoutCredentials_isNoBuildAgentClone() {
            assertThat(localVCServletService.isBuildAgentClone(requestOf(null))).isFalse();
        }
    }

    @Nested
    class BranchingAndForcePush {

        private Repository repositoryOfProject(String projectKey) {
            Repository repository = mock(Repository.class);
            when(repository.getDirectory()).thenReturn(new File("/tmp/test-repos/" + projectKey + "/" + projectKey.toLowerCase(Locale.ROOT) + "-testuser.git"));
            return repository;
        }

        private void configureBranching(boolean allowBranching, String branchRegex) {
            var buildConfig = new ProgrammingExerciseBuildConfig();
            buildConfig.setAllowBranching(allowBranching);
            buildConfig.setBranchRegex(branchRegex);
            when(programmingExerciseBuildConfigRepository.findAllByProjectKey("TEST")).thenReturn(List.of(buildConfig));
        }

        @Test
        void aBranchMatchingTheRegex_isAllowed() {
            configureBranching(true, "feature/.*");

            assertThat(localVCServletService.isBranchNameAllowedForRepository(repositoryOfProject("TEST"), "feature/login"))
                    .isEqualTo(LocalVCServletService.BranchingStatus.BRANCH_ALLOWED);
        }

        @Test
        void aBranchNotMatchingTheRegex_isRefused() {
            configureBranching(true, "feature/.*");

            assertThat(localVCServletService.isBranchNameAllowedForRepository(repositoryOfProject("TEST"), "hotfix"))
                    .isEqualTo(LocalVCServletService.BranchingStatus.NAME_DOES_NOT_MATCH_REGEX);
        }

        @Test
        void anInvalidRegexOfTheInstructor_refusesEveryBranch() {
            configureBranching(true, "feature/[");

            assertThat(localVCServletService.isBranchNameAllowedForRepository(repositoryOfProject("TEST"), "feature/login"))
                    .isEqualTo(LocalVCServletService.BranchingStatus.NAME_DOES_NOT_MATCH_REGEX);
        }

        @Test
        void disabledBranching_refusesEveryBranch() {
            configureBranching(false, "feature/.*");

            assertThat(localVCServletService.isBranchNameAllowedForRepository(repositoryOfProject("TEST"), "feature/login"))
                    .isEqualTo(LocalVCServletService.BranchingStatus.BRANCHING_DISABLED);
        }

        @Test
        void aProjectWithoutExactlyOneExercise_failsTheBranchCheck() {
            when(programmingExerciseBuildConfigRepository.findAllByProjectKey("TEST")).thenReturn(List.of());
            Repository repository = repositoryOfProject("TEST");

            assertThatExceptionOfType(LocalVCInternalException.class).isThrownBy(() -> localVCServletService.isBranchNameAllowedForRepository(repository, "main"));
        }

        @Test
        void aProjectWithoutExercise_failsTheForcePushCheck() {
            when(programmingExerciseRepository.findOneByProjectKeyOrThrow("TEST", false)).thenThrow(new EntityNotFoundException("exercise", 1L));
            Repository repository = repositoryOfProject("TEST");

            assertThatExceptionOfType(LocalVCInternalException.class).isThrownBy(() -> localVCServletService.isUserAllowedToForcePush(testUser, repository));
        }
    }
}
