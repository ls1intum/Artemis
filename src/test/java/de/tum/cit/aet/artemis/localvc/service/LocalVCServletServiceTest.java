package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCInternalException;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.dto.GitRepositoryAccessDTO;
import de.tum.cit.aet.artemis.programming.repository.ParticipationVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.RepositoryVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

@ExtendWith(MockitoExtension.class)
class LocalVCServletServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RateLimitService rateLimitService;

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
    private ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository;

    @Mock
    private RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository;

    @Mock
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Mock
    private VcsAccessLogService vcsAccessLogService;

    @Mock
    private UserVcsAccessTokenService userVcsAccessTokenService;

    @Spy
    private LocalVCRepositoryResolverService repositoryResolver = new LocalVCRepositoryResolverService();

    @Mock
    private LocalVCAccessLogRecorderService accessLogRecorder;

    @Mock
    private LocalVCBuildAgentAuthenticationService buildAgentAuthenticationService;

    @Mock
    private PasswordAuthenticationNoticeService passwordAuthenticationNoticeService;

    @InjectMocks
    private LocalVCServletService localVCServletService;

    private User testUser;

    private ProgrammingExercise testExercise;

    private LocalVCRepositoryUri testRepositoryUri;

    @BeforeEach
    void setUp() {
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

        testRepositoryUri = mock(LocalVCRepositoryUri.class);
        // Use lenient() to avoid unnecessary stubbing errors for tests that don't use this mock
        lenient().when(testRepositoryUri.getRelativeRepositoryPath()).thenReturn(Path.of("test/repo"));
        lenient().when(testRepositoryUri.toString()).thenReturn("http://localhost/git/TEST/EXERCISE-template.git");

        // The repositories do not exist on disk, so reading the latest commit of one finds nothing
        ReflectionTestUtils.setField(repositoryResolver, "localVCBasePath", Path.of("/tmp/test-repos"));
        ReflectionTestUtils.setField(repositoryResolver, "localVCBaseUri", URI.create("http://localhost"));
        // Setup the VcsAccessLogService as an Optional containing the mock
        ReflectionTestUtils.setField(localVCServletService, "vcsAccessLogService", Optional.of(vcsAccessLogService));
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

        when(accessLogRecorder.resolveAuthenticationMechanismFromSessionOrRequest(any(), eq(testUser), eq(testRepositoryUri))).thenReturn(AuthenticationMechanism.SSH);

        localVCServletService.saveFailedAccessVcsAccessLog(context, "student1", testExercise.getId(), testRepositoryUri, testUser, RepositoryActionType.READ);

        verify(vcsAccessLogService).saveAccessLog(eq(testUser), any(), eq(RepositoryActionType.CLONE_FAIL), eq(AuthenticationMechanism.SSH), anyString(), anyString());
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

    @Test
    void authenticateAndAuthorizeGitRequest_withTheSharedBuildAgentCredential_skipsTheRateLimitAndTheAuthorization() throws Exception {
        HttpServletRequest request = gitFetchRequest(basicAuth("build-agent", "a-long-shared-secret"));
        when(buildAgentAuthenticationService.authenticateSharedBuildAgentCredential(request, request.getHeader(HttpHeaders.AUTHORIZATION))).thenReturn(true);

        assertThatCode(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ)).doesNotThrowAnyException();

        verifyNoInteractions(rateLimitService, userRepository, repositoryAccessService);
    }

    @Test
    void authenticateAndAuthorizeGitRequest_withACredentialTheBuildAgentShortcutsDecline_isHandledLikeAnyOtherCredential() {
        HttpServletRequest request = gitFetchRequest(basicAuth("build-agent", "another-secret"));

        // not accepted by the shortcuts, so the request goes on to look up the exercise of the repository, which does not exist here
        assertThatExceptionOfType(LocalVCInternalException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ));
    }

    @Test
    void authenticateAndAuthorizeGitRequest_forAPush_neverOffersTheBuildAgentShortcuts() {
        HttpServletRequest request = gitFetchRequest(basicAuth("build-agent", "a-long-shared-secret"));

        // the shortcuts only grant read access
        assertThatExceptionOfType(LocalVCInternalException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE));

        verifyNoInteractions(buildAgentAuthenticationService);
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

    @Nested
    class ParticipationVcsAccessTokenAuthentication {

        private static final long TEAM_PARTICIPATION_ID = 7L;

        private static final long PRACTICE_PARTICIPATION_ID = 5L;

        private GitRepositoryAccessDTO exerciseOfMode(ExerciseMode mode) {
            return new GitRepositoryAccessDTO(testExercise.getId(), 1L, mode, null, null, null, null, null, null, null, null);
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
