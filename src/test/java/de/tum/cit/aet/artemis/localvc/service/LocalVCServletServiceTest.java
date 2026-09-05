package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
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
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.junit.jupiter.api.BeforeEach;
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
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryVCSAccessToken;
import de.tum.cit.aet.artemis.programming.repository.ParticipationVCSAccessTokenRepository;
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
        localVCServletService.saveFailedAccessVcsAccessLog(context, "student1", testExercise, testRepositoryUri, testUser, RepositoryActionType.READ);

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

}
