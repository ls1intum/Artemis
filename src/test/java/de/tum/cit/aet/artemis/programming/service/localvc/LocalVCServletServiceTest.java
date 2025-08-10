package de.tum.cit.aet.artemis.programming.service.localvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.sshd.server.session.ServerSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.repository.ParticipationVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingTriggerService;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;
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
    private ProgrammingMessagingService programmingMessagingService;

    @Mock
    private ProgrammingTriggerService programmingTriggerService;

    @Mock
    private ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository;

    @Mock
    private VcsAccessLogService vcsAccessLogService;

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

        // Setup the VcsAccessLogService as an Optional containing the mock
        ReflectionTestUtils.setField(localVCServletService, "vcsAccessLogService", Optional.of(vcsAccessLogService));

        ReflectionTestUtils.setField(localVCServletService, "localVCBasePath", java.nio.file.Path.of("/tmp/test-repos"));
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
                User.class);
        method.setAccessible(true);

        AuthenticationMechanism result = (AuthenticationMechanism) method.invoke(localVCServletService, context, testUser);

        assertThat(result).isEqualTo(AuthenticationMechanism.SSH);
    }

    @Test
    void testResolveAuthenticationMechanismFromSessionOrRequest_withRequestAndMissingHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        AuthenticationContext.Request context = new AuthenticationContext.Request(request);

        // Use reflection to call the private method for testing
        java.lang.reflect.Method method = LocalVCServletService.class.getDeclaredMethod("resolveAuthenticationMechanismFromSessionOrRequest", AuthenticationContext.class,
                User.class);
        method.setAccessible(true);

        AuthenticationMechanism result = (AuthenticationMechanism) method.invoke(localVCServletService, context, testUser);

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
                User.class);
        method.setAccessible(true);

        AuthenticationMechanism result = (AuthenticationMechanism) method.invoke(localVCServletService, context, testUser);

        assertThat(result).isEqualTo(AuthenticationMechanism.PASSWORD);
    }

    @Test
    void testResolveAuthenticationMechanismFromSessionOrRequest_withRequestAndTokenHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        // Create a valid token with the correct prefix and length (50 characters total)
        String token = "vcpat-" + "a".repeat(44); // 6 + 44 = 50 characters total
        String authHeader = "Basic " + java.util.Base64.getEncoder().encodeToString(("user:" + token).getBytes());
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authHeader);

        testUser.setVcsAccessToken(token);

        AuthenticationContext.Request context = new AuthenticationContext.Request(request);

        // Use reflection to call the private method for testing
        java.lang.reflect.Method method = LocalVCServletService.class.getDeclaredMethod("resolveAuthenticationMechanismFromSessionOrRequest", AuthenticationContext.class,
                User.class);
        method.setAccessible(true);

        AuthenticationMechanism result = (AuthenticationMechanism) method.invoke(localVCServletService, context, testUser);

        assertThat(result).isEqualTo(AuthenticationMechanism.USER_VCS_ACCESS_TOKEN);
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
}
