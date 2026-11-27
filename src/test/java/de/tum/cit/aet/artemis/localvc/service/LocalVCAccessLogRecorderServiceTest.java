package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryVCSAccessToken;
import de.tum.cit.aet.artemis.programming.repository.RepositoryVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
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

    @Mock
    private AuxiliaryRepositoryService auxiliaryRepositoryService;

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

        // Setup the VcsAccessLogService as an Optional containing the mock
        ReflectionTestUtils.setField(accessLogRecorder, "vcsAccessLogService", Optional.of(vcsAccessLogService));
    }

    @ParameterizedTest
    @EnumSource(value = RepositoryActionType.class, names = { "READ", "WRITE" })
    void writesTheAccessLogWhenTheCredentialsOfAnAttemptDoNotHold(RepositoryActionType action) {
        HttpServletRequest request = failedRequest();
        when(userRepository.findOneByLogin("testuser")).thenReturn(Optional.of(testUser));
        when(programmingExerciseParticipationService.getParticipationReferenceForRepository(anyString(), anyString(), eq(testExercise.getProjectKey())))
                .thenReturn(Optional.of(testParticipation));
        var expected = action == RepositoryActionType.WRITE ? RepositoryActionType.PUSH_FAIL : RepositoryActionType.CLONE_FAIL;

        accessLogRecorder.recordFailedAuthenticationHTTPS(request, testRepositoryUri, testExercise.getId(), action);

        verify(vcsAccessLogService).saveAccessLog(eq(testUser), eq(testParticipation.getId()), eq(expected), eq(AuthenticationMechanism.PASSWORD), eq(""), eq("10.0.0.1"));
    }

    @Test
    void doesNotLetAFailedAccessLogReplaceTheAuthenticationFailure() {
        HttpServletRequest request = failedRequest();
        when(userRepository.findOneByLogin("testuser")).thenReturn(Optional.of(testUser));
        when(programmingExerciseParticipationService.getParticipationReferenceForRepository(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("the database is away"));

        // The caller is in the middle of answering a rejected authentication with 401. An exception escaping here
        // reaches the servlet container instead, and the client is told the server is broken rather than being asked
        // for credentials.
        assertThatCode(() -> accessLogRecorder.recordFailedAuthenticationHTTPS(request, testRepositoryUri, testExercise.getId(), RepositoryActionType.READ))
                .doesNotThrowAnyException();
        verifyNoInteractions(vcsAccessLogService);
    }

    private HttpServletRequest failedRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(basicAuth("testuser", "wrong-password"));
        lenient().when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        lenient().when(testRepositoryUri.getProjectKey()).thenReturn(testExercise.getProjectKey());
        lenient().when(testRepositoryUri.getRepositoryTypeOrUserName()).thenReturn("testuser");
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
    void aFailedAttemptOfAnUnknownUser_writesNoLog() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(basicAuth("testuser", "wrong-password"));
        when(userRepository.findOneByLogin("testuser")).thenReturn(Optional.empty());

        assertThatCode(() -> accessLogRecorder.recordFailedAuthenticationHTTPS(request, testRepositoryUri, testExercise.getId(), RepositoryActionType.READ))
                .doesNotThrowAnyException();

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

    private MockHttpServletRequest authorizedRequest(String method, AuthenticationMechanism mechanism) {
        var request = new MockHttpServletRequest(method, "/git/TEST/EXERCISE-testuser.git/" + (method.equals("GET") ? "info/refs" : "git-upload-pack"));
        request.setRemoteAddr("10.0.0.1");
        accessLogRecorder.rememberSubjectHTTPS(request, testUser, Optional.of(new AccessLogTarget.OfParticipation(testParticipation.getId())), mechanism);
        return request;
    }

    private void verifyEntry(RepositoryActionType action, AuthenticationMechanism mechanism, String commitHash) {
        verify(vcsAccessLogService).createAccessLog(testUser, testParticipation.getId(), action, mechanism, commitHash, "10.0.0.1");
    }

    /**
     * The entry of an advertisement is written after its response was sent, so it carries the time at which its request started and the next request of the operation cannot
     * be stored before it in the log.
     */
    @Test
    void anAdvertisementThatTheRepositoryAnsweredIsAReadForAFetchAndAWriteForAPush() {
        var fetchStarted = ZonedDateTime.parse("2026-11-17T10:00:00Z");
        var pushStarted = ZonedDateTime.parse("2026-11-17T10:05:00Z");
        var fetchAdvertisement = authorizedRequest("GET", AuthenticationMechanism.PASSWORD);
        accessLogRecorder.auditOf(fetchAdvertisement).onAccessed("head", fetchStarted);
        var pushAdvertisement = authorizedRequest("GET", AuthenticationMechanism.PASSWORD);
        accessLogRecorder.auditOf(pushAdvertisement).onAccessed("head", pushStarted);

        accessLogRecorder.recordFetchHTTPS(fetchAdvertisement);
        accessLogRecorder.recordPushHTTPS(pushAdvertisement);

        verify(vcsAccessLogService).createAccessLog(testUser, testParticipation.getId(), RepositoryActionType.READ, AuthenticationMechanism.PASSWORD, "head", "10.0.0.1",
                fetchStarted);
        verify(vcsAccessLogService).createAccessLog(testUser, testParticipation.getId(), RepositoryActionType.WRITE, AuthenticationMechanism.PASSWORD, "head", "10.0.0.1",
                pushStarted);
        verifyNoMoreInteractions(vcsAccessLogService);
    }

    @Test
    void aRequestThatWasNotAnsweredOrIsNotAnAdvertisementWritesNothing() {
        var notAnswered = authorizedRequest("GET", AuthenticationMechanism.PASSWORD);

        accessLogRecorder.recordFetchHTTPS(notAnswered);
        accessLogRecorder.recordPushHTTPS(notAnswered);

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void aFetchThatSentDataWithoutOfferedObjectsIsOneClone() {
        var request = authorizedRequest("POST", AuthenticationMechanism.USER_VCS_ACCESS_TOKEN);
        accessLogRecorder.auditOf(request).onBeginNegotiateRound(0);
        accessLogRecorder.auditOf(request).onPackSent("head");

        accessLogRecorder.recordFetchHTTPS(request);

        verifyEntry(RepositoryActionType.CLONE, AuthenticationMechanism.USER_VCS_ACCESS_TOKEN, "head");
        verifyNoMoreInteractions(vcsAccessLogService);
    }

    @Test
    void aFetchThatSentDataAfterOfferedObjectsIsOnePull() {
        var request = authorizedRequest("POST", AuthenticationMechanism.PASSWORD);
        accessLogRecorder.auditOf(request).onBeginNegotiateRound(3);
        accessLogRecorder.auditOf(request).onPackSent("head");

        accessLogRecorder.recordFetchHTTPS(request);

        verifyEntry(RepositoryActionType.PULL, AuthenticationMechanism.PASSWORD, "head");
        verifyNoMoreInteractions(vcsAccessLogService);
    }

    @Test
    void theLastRoundOfTheNegotiationDecidesWhetherAFetchIsAClone() {
        var request = authorizedRequest("POST", AuthenticationMechanism.PASSWORD);
        // the first round offers objects that the last round does not need to repeat: what counts is what the client offered when the data was sent
        accessLogRecorder.auditOf(request).onBeginNegotiateRound(5);
        accessLogRecorder.auditOf(request).onBeginNegotiateRound(0);
        accessLogRecorder.auditOf(request).onPackSent("head");

        accessLogRecorder.recordFetchHTTPS(request);

        verifyEntry(RepositoryActionType.CLONE, AuthenticationMechanism.PASSWORD, "head");
    }

    @Test
    void aNegotiationThatSentNoDataIsNoOperation() {
        var request = authorizedRequest("POST", AuthenticationMechanism.PASSWORD);
        accessLogRecorder.auditOf(request).onBeginNegotiateRound(3);

        accessLogRecorder.recordFetchHTTPS(request);

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void aFetchWithoutACommitHasAnEmptyHash() {
        var request = authorizedRequest("POST", AuthenticationMechanism.PASSWORD);
        accessLogRecorder.auditOf(request).onPackSent(null);

        accessLogRecorder.recordFetchHTTPS(request);

        verifyEntry(RepositoryActionType.CLONE, AuthenticationMechanism.PASSWORD, "");
    }

    @Test
    void anAcceptedPushIsOnePushWithTheCommitThatWasPushed() {
        var request = authorizedRequest("POST", AuthenticationMechanism.PASSWORD);
        accessLogRecorder.auditOf(request).onPushReceived("before");
        accessLogRecorder.auditOf(request).onPushAccepted("pushed");

        accessLogRecorder.recordPushHTTPS(request);

        verifyEntry(RepositoryActionType.PUSH, AuthenticationMechanism.PASSWORD, "pushed");
        verifyNoMoreInteractions(vcsAccessLogService);
    }

    @Test
    void aRefusedPushIsOneFailedPushWithTheCommitFromBefore() {
        var request = authorizedRequest("POST", AuthenticationMechanism.PASSWORD);
        accessLogRecorder.auditOf(request).onPushReceived("before");

        accessLogRecorder.recordPushHTTPS(request);

        verifyEntry(RepositoryActionType.PUSH_FAIL, AuthenticationMechanism.PASSWORD, "before");
    }

    @Test
    void theCommitFromBeforeIsTheOneOfTheFirstMomentTheCommandsWereKnown() {
        var request = authorizedRequest("POST", AuthenticationMechanism.PASSWORD);
        // the commands are known before the objects are read and again before they are applied, and the second look is after something may have changed
        accessLogRecorder.auditOf(request).onPushReceived("before");
        accessLogRecorder.auditOf(request).onPushReceived("changed-meanwhile");

        accessLogRecorder.recordPushHTTPS(request);

        verifyEntry(RepositoryActionType.PUSH_FAIL, AuthenticationMechanism.PASSWORD, "before");
    }

    @Test
    void aRequestWithoutCommandsIsNoPush() {
        var request = authorizedRequest("POST", AuthenticationMechanism.PASSWORD);

        accessLogRecorder.recordPushHTTPS(request);

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void aRequestWithoutASubjectWritesNothing() {
        // A build agent clone is authorized in a way of its own and has its own entry, and a repository without a participation has nobody to attribute an entry to.
        var request = new MockHttpServletRequest("POST", "/git/TEST/EXERCISE-testuser.git/git-upload-pack");
        accessLogRecorder.auditOf(request).onBeginNegotiateRound(0);
        accessLogRecorder.auditOf(request).onPackSent("head");
        accessLogRecorder.rememberSubjectHTTPS(request, testUser, Optional.empty(), AuthenticationMechanism.PASSWORD);

        accessLogRecorder.recordFetchHTTPS(request);

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void theAuditOfARequestIsTheSameForTheHookAndTheFilter() {
        var request = authorizedRequest("POST", AuthenticationMechanism.PASSWORD);

        assertThat(accessLogRecorder.auditOf(request)).isSameAs(accessLogRecorder.auditOf(request));
        assertThat(accessLogRecorder.auditOf(authorizedRequest("POST", AuthenticationMechanism.PASSWORD))).isNotSameAs(accessLogRecorder.auditOf(request));
    }

    @Test
    void aFailureToWriteTheEntryDoesNotFailTheOperation() {
        var request = authorizedRequest("POST", AuthenticationMechanism.PASSWORD);
        accessLogRecorder.auditOf(request).onPushReceived("before");
        accessLogRecorder.auditOf(request).onPushAccepted("pushed");
        doThrow(new IllegalStateException("the database is away")).when(vcsAccessLogService).createAccessLog(any(), anyLong(), any(), any(), anyString(), anyString());

        assertThatCode(() -> accessLogRecorder.recordPushHTTPS(request)).doesNotThrowAnyException();
    }

    @Test
    void anSshFetchThatMovedNoDataIsAReadAndAnSshPushWithoutCommandsIsAWrite() throws Exception {
        var subject = new AccessLogSubject(testUser, new AccessLogTarget.OfParticipation(testParticipation.getId()), AuthenticationMechanism.SSH, "10.0.0.2");
        try (var repository = new InMemoryRepository(new DfsRepositoryDescription("empty"))) {
            accessLogRecorder.recordFetchSSH(subject, new GitOperationAudit(), repository);
            accessLogRecorder.recordPushSSH(subject, new GitOperationAudit(), repository);
        }

        verify(vcsAccessLogService).createAccessLog(testUser, testParticipation.getId(), RepositoryActionType.READ, AuthenticationMechanism.SSH, "", "10.0.0.2");
        verify(vcsAccessLogService).createAccessLog(testUser, testParticipation.getId(), RepositoryActionType.WRITE, AuthenticationMechanism.SSH, "", "10.0.0.2");
        verifyNoMoreInteractions(vcsAccessLogService);
    }

    @Test
    void anSshCommandWritesItsOwnEntryFromTheSubjectItCaptured() {
        var audit = new GitOperationAudit();
        audit.onBeginNegotiateRound(0);
        audit.onPackSent("head");
        var subject = new AccessLogSubject(testUser, new AccessLogTarget.OfParticipation(testParticipation.getId()), AuthenticationMechanism.SSH, "10.0.0.2");

        accessLogRecorder.recordFetch(subject, audit);
        accessLogRecorder.recordPush(subject, audit);

        verify(vcsAccessLogService).createAccessLog(testUser, testParticipation.getId(), RepositoryActionType.CLONE, AuthenticationMechanism.SSH, "head", "10.0.0.2");
        verifyNoMoreInteractions(vcsAccessLogService);
    }

    @Test
    void anSshPushIsOnePushOrOneFailedPush() {
        var subject = new AccessLogSubject(testUser, new AccessLogTarget.OfParticipation(testParticipation.getId()), AuthenticationMechanism.SSH, "10.0.0.2");
        var accepted = new GitOperationAudit();
        accepted.onPushReceived("before");
        accepted.onPushAccepted("pushed");
        var refused = new GitOperationAudit();
        refused.onPushReceived("before");

        accessLogRecorder.recordPush(subject, accepted);
        accessLogRecorder.recordPush(subject, refused);

        verify(vcsAccessLogService).createAccessLog(testUser, testParticipation.getId(), RepositoryActionType.PUSH, AuthenticationMechanism.SSH, "pushed", "10.0.0.2");
        verify(vcsAccessLogService).createAccessLog(testUser, testParticipation.getId(), RepositoryActionType.PUSH_FAIL, AuthenticationMechanism.SSH, "before", "10.0.0.2");
    }

    private GitOperationAudit auditOfAClone() {
        var audit = new GitOperationAudit();
        audit.onBeginNegotiateRound(0);
        audit.onPackSent("head");
        return audit;
    }

    @Test
    void anEntryIsWrittenByAThreadThatWasInterruptedAndTheInterruptIsPassedOn() {
        var subject = new AccessLogSubject(testUser, new AccessLogTarget.OfParticipation(testParticipation.getId()), AuthenticationMechanism.SSH, "10.0.0.2");
        var stored = new AtomicInteger();
        doAnswer(invocation -> {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("no connection was acquired", new InterruptedException());
            }
            stored.incrementAndGet();
            return null;
        }).when(vcsAccessLogService).createAccessLog(any(), anyLong(), any(), any(), anyString(), anyString());

        Thread.currentThread().interrupt();
        try {
            accessLogRecorder.recordFetch(subject, auditOfAClone());

            assertThat(stored).hasValue(1);
            // the entry was written at the first attempt: clearing the interrupt is what made it possible, not the second attempt
            verify(vcsAccessLogService, times(1)).createAccessLog(testUser, testParticipation.getId(), RepositoryActionType.CLONE, AuthenticationMechanism.SSH, "head", "10.0.0.2");
            verify(vcsAccessLogService, never()).createAccessLog(any(), anyLong(), any(), any(), anyString(), anyString(), any());
            assertThat(Thread.currentThread().isInterrupted()).as("the interrupt is not lost").isTrue();
        }
        finally {
            Thread.interrupted();
        }
    }

    @Test
    void anEntryIsWrittenOnceMoreWhenTheInterruptArrivesWhileItIsWritten() {
        var subject = new AccessLogSubject(testUser, new AccessLogTarget.OfParticipation(testParticipation.getId()), AuthenticationMechanism.SSH, "10.0.0.2");
        var stored = new AtomicInteger();
        var timeOfRetry = new AtomicReference<ZonedDateTime>();
        var timeInsideTheFirstAttempt = new AtomicReference<ZonedDateTime>();
        // the first attempt is interrupted while it waits for a connection
        doAnswer(invocation -> {
            timeInsideTheFirstAttempt.set(ZonedDateTime.now());
            // a retry that took a new time would fall after this wait, also on a clock with a coarse resolution
            Thread.sleep(10);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("no connection was acquired", new InterruptedException());
        }).when(vcsAccessLogService).createAccessLog(any(), anyLong(), any(), any(), anyString(), anyString());
        // the second one carries the time that was taken before the first attempt
        doAnswer(invocation -> {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("no connection was acquired", new InterruptedException());
            }
            timeOfRetry.set(invocation.getArgument(6));
            stored.incrementAndGet();
            return null;
        }).when(vcsAccessLogService).createAccessLog(any(), anyLong(), any(), any(), anyString(), anyString(), any());

        var before = ZonedDateTime.now();
        try {
            accessLogRecorder.recordFetch(subject, auditOfAClone());

            assertThat(stored).hasValue(1);
            assertThat(timeOfRetry.get()).as("the time of the first attempt, taken before it started").isBetween(before, timeInsideTheFirstAttempt.get());
            // the retry writes the same event as the first attempt, with the time added
            verify(vcsAccessLogService, times(1)).createAccessLog(testUser, testParticipation.getId(), RepositoryActionType.CLONE, AuthenticationMechanism.SSH, "head", "10.0.0.2");
            verify(vcsAccessLogService, times(1)).createAccessLog(testUser, testParticipation.getId(), RepositoryActionType.CLONE, AuthenticationMechanism.SSH, "head", "10.0.0.2",
                    timeOfRetry.get());
            verifyNoMoreInteractions(vcsAccessLogService);
            assertThat(Thread.currentThread().isInterrupted()).as("the interrupt is not lost").isTrue();
        }
        finally {
            Thread.interrupted();
        }
    }

    @Test
    void aFailureThatIsNoInterruptIsNotWrittenAgainAndLeavesTheThreadAlone() {
        var subject = new AccessLogSubject(testUser, new AccessLogTarget.OfParticipation(testParticipation.getId()), AuthenticationMechanism.SSH, "10.0.0.2");
        doThrow(new IllegalStateException("the database is away")).when(vcsAccessLogService).createAccessLog(any(), anyLong(), any(), any(), anyString(), anyString());

        try {
            accessLogRecorder.recordFetch(subject, auditOfAClone());

            verify(vcsAccessLogService, times(1)).createAccessLog(any(), anyLong(), any(), any(), anyString(), anyString());
            // no other attempt either, for example one with a time
            verifyNoMoreInteractions(vcsAccessLogService);
            assertThat(Thread.currentThread().isInterrupted()).isFalse();
        }
        finally {
            // a regression that sets the flag must not leave it set for the next test
            Thread.interrupted();
        }
    }

    @Test
    void anSshCommandWithoutASubjectWritesNothing() {
        var audit = new GitOperationAudit();
        audit.onPackSent("head");
        audit.onPushReceived("before");

        // a build agent session carries no subject and gets an entry of its own
        accessLogRecorder.recordFetch(null, audit);
        accessLogRecorder.recordPush(null, audit);

        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void cacheAttributesInSshSession_remembersTheSubjectAndTheParticipationWithoutWritingAnything() {
        ServerSession session = mock(ServerSession.class);

        accessLogRecorder.cacheAttributesInSshSession(testUser, Optional.of(testParticipation), Optional.of(new AccessLogTarget.OfParticipation(testParticipation.getId())),
                AuthenticationMechanism.SSH, "10.0.0.2", session);

        verify(session).setAttribute(SshConstants.PARTICIPATION_KEY, testParticipation);
        verify(session).setAttribute(SshConstants.ACCESS_LOG_SUBJECT_KEY,
                new AccessLogSubject(testUser, new AccessLogTarget.OfParticipation(testParticipation.getId()), AuthenticationMechanism.SSH, "10.0.0.2"));
        verifyNoInteractions(vcsAccessLogService);
    }

    @Test
    void cacheAttributesInSshSession_dropsTheStateOfTheCommandBefore() {
        ServerSession session = mock(ServerSession.class);

        accessLogRecorder.cacheAttributesInSshSession(testUser, Optional.empty(), Optional.empty(), AuthenticationMechanism.SSH, "10.0.0.2", session);

        // a command whose repository has no participation must not be handled with the participation of the one before
        verify(session).removeAttribute(SshConstants.ACCESS_LOG_SUBJECT_KEY);
        verify(session).removeAttribute(SshConstants.PARTICIPATION_KEY);
        verify(session, never()).setAttribute(any(), any());
        verifyNoInteractions(vcsAccessLogService);
    }

    private static String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void theTestsRepositoryIsLoggedForTheExerciseAndTheNamedAuxiliaryRepositoryForItself() {
        when(auxiliaryRepositoryService.findAuxiliaryRepositoryIdOfExercise("extras", 1L)).thenReturn(Optional.of(9L));

        assertThat(accessLogRecorder.staffRepositoryTargetOf(1L, "tests")).contains(new AccessLogTarget.OfStaffRepository(1L, RepositoryType.TESTS, null, null));
        assertThat(accessLogRecorder.staffRepositoryTargetOf(1L, "extras")).contains(new AccessLogTarget.OfStaffRepository(1L, RepositoryType.AUXILIARY, 9L, "extras"));
    }

    @Test
    void theTemplateAndTheSolutionAreNotStaffRepositoriesAndAreNeverLookedUpAsAuxiliaryOnes() {
        assertThat(accessLogRecorder.staffRepositoryTargetOf(1L, "exercise")).isEmpty();
        assertThat(accessLogRecorder.staffRepositoryTargetOf(1L, "solution")).isEmpty();
        verifyNoInteractions(auxiliaryRepositoryService);
    }

    @Test
    void aFailureToFindTheAuxiliaryRepositoryDoesNotRejectTheAuthorizedOperation() {
        when(auxiliaryRepositoryService.findAuxiliaryRepositoryIdOfExercise("extras", 1L)).thenThrow(new IllegalStateException("the database is away"));

        assertThat(accessLogRecorder.targetOf(Optional.empty(), 1L, "extras")).isEmpty();
    }

    @Test
    void aStudentIsNotAStaffRepositoryUnlessTheNameIsThatOfAnAuxiliaryRepository() {
        when(auxiliaryRepositoryService.findAuxiliaryRepositoryIdOfExercise("student1", 1L)).thenReturn(Optional.empty());

        assertThat(accessLogRecorder.staffRepositoryTargetOf(1L, "student1")).isEmpty();
        verify(auxiliaryRepositoryService).findAuxiliaryRepositoryIdOfExercise("student1", 1L);
    }

    @Test
    void theParticipationIsThePreferredTargetAndTheStaffRepositoryTheFallback() {
        var participationTarget = accessLogRecorder.targetOf(Optional.of(testParticipation), 1L, "tests");
        var staffTarget = accessLogRecorder.targetOf(Optional.empty(), 1L, "tests");

        assertThat(participationTarget).contains(new AccessLogTarget.OfParticipation(testParticipation.getId()));
        assertThat(staffTarget).contains(new AccessLogTarget.OfStaffRepository(1L, RepositoryType.TESTS, null, null));
        assertThat(accessLogRecorder.targetOf(Optional.empty(), 1L, "exercise")).isEmpty();
    }

    @Test
    void anOperationOnTheTestsRepositoryIsWrittenToTheLogOfTheStaffRepositories() {
        var target = new AccessLogTarget.OfStaffRepository(1L, RepositoryType.TESTS, null, null);
        var subject = new AccessLogSubject(testUser, target, AuthenticationMechanism.PASSWORD, "10.0.0.2");
        var clone = new GitOperationAudit();
        clone.onBeginNegotiateRound(0);
        clone.onPackSent("head");
        var push = new GitOperationAudit();
        push.onPushReceived("before");
        push.onPushAccepted("pushed");

        accessLogRecorder.recordFetch(subject, clone);
        accessLogRecorder.recordPush(subject, push);

        verify(vcsAccessLogService).createStaffRepositoryAccessLog(eq(testUser), eq(target), eq(RepositoryActionType.CLONE), eq(AuthenticationMechanism.PASSWORD), eq("head"),
                eq("10.0.0.2"), any(ZonedDateTime.class));
        verify(vcsAccessLogService).createStaffRepositoryAccessLog(eq(testUser), eq(target), eq(RepositoryActionType.PUSH), eq(AuthenticationMechanism.PASSWORD), eq("pushed"),
                eq("10.0.0.2"), any(ZonedDateTime.class));
        verifyNoMoreInteractions(vcsAccessLogService);
    }

    @Test
    void anSshSessionRemembersTheStaffRepositoryItsCommandIsFor() {
        ServerSession session = mock(ServerSession.class);
        var target = new AccessLogTarget.OfStaffRepository(1L, RepositoryType.TESTS, null, null);

        accessLogRecorder.cacheAttributesInSshSession(testUser, Optional.empty(), Optional.of(target), AuthenticationMechanism.SSH, "10.0.0.2", session);

        verify(session).setAttribute(SshConstants.ACCESS_LOG_SUBJECT_KEY, new AccessLogSubject(testUser, target, AuthenticationMechanism.SSH, "10.0.0.2"));
        // there is no participation to remember, and the one of the command before is dropped
        verify(session).removeAttribute(SshConstants.PARTICIPATION_KEY);
        verify(session, never()).setAttribute(eq(SshConstants.PARTICIPATION_KEY), any());
    }

    @Test
    void aFailedAuthenticationOnTheRepositoryOfAStudentNamedLikeAnAuxiliaryRepositoryIsWrittenForTheParticipation() {
        HttpServletRequest request = failedRequest();
        lenient().when(testRepositoryUri.getRepositoryTypeOrUserName()).thenReturn("extras");
        when(userRepository.findOneByLogin("testuser")).thenReturn(Optional.of(testUser));
        when(programmingExerciseParticipationService.getParticipationReferenceForRepository(anyString(), anyString(), eq(testExercise.getProjectKey())))
                .thenReturn(Optional.of(testParticipation));

        accessLogRecorder.recordFailedAuthenticationHTTPS(request, testRepositoryUri, testExercise.getId(), RepositoryActionType.READ);

        verify(vcsAccessLogService).saveAccessLog(eq(testUser), eq(testParticipation.getId()), eq(RepositoryActionType.CLONE_FAIL), eq(AuthenticationMechanism.PASSWORD), eq(""),
                eq("10.0.0.1"));
        verifyNoInteractions(auxiliaryRepositoryService);
    }

    @Test
    void aFailedAuthenticationOnAnAuxiliaryRepositoryIsWrittenForTheExerciseWhenNoParticipationHasTheName() {
        HttpServletRequest request = failedRequest();
        lenient().when(testRepositoryUri.getRepositoryTypeOrUserName()).thenReturn("extras");
        when(userRepository.findOneByLogin("testuser")).thenReturn(Optional.of(testUser));
        when(programmingExerciseParticipationService.getParticipationReferenceForRepository(anyString(), anyString(), eq(testExercise.getProjectKey())))
                .thenReturn(Optional.empty());
        when(auxiliaryRepositoryService.findAuxiliaryRepositoryIdOfExercise("extras", testExercise.getId())).thenReturn(Optional.of(9L));

        accessLogRecorder.recordFailedAuthenticationHTTPS(request, testRepositoryUri, testExercise.getId(), RepositoryActionType.WRITE);

        verify(vcsAccessLogService).saveStaffRepositoryAccessLog(testUser, new AccessLogTarget.OfStaffRepository(testExercise.getId(), RepositoryType.AUXILIARY, 9L, "extras"),
                RepositoryActionType.PUSH_FAIL, AuthenticationMechanism.PASSWORD, "", "10.0.0.1");
    }

    @ParameterizedTest
    @EnumSource(value = RepositoryActionType.class, names = { "READ", "WRITE" })
    void aFailedAuthenticationOnTheTestsRepositoryIsWrittenForTheExerciseAndNotForTheSolution(RepositoryActionType action) {
        HttpServletRequest request = failedRequest();
        lenient().when(testRepositoryUri.getRepositoryTypeOrUserName()).thenReturn("tests");
        when(userRepository.findOneByLogin("testuser")).thenReturn(Optional.of(testUser));
        var expected = action == RepositoryActionType.WRITE ? RepositoryActionType.PUSH_FAIL : RepositoryActionType.CLONE_FAIL;

        accessLogRecorder.recordFailedAuthenticationHTTPS(request, testRepositoryUri, testExercise.getId(), action);

        verify(vcsAccessLogService).saveStaffRepositoryAccessLog(testUser, new AccessLogTarget.OfStaffRepository(testExercise.getId(), RepositoryType.TESTS, null, null), expected,
                AuthenticationMechanism.PASSWORD, "", "10.0.0.1");
        verify(vcsAccessLogService, never()).saveAccessLog(any(), anyLong(), any(), any(), anyString(), anyString());
        // the tests repository used to be looked up as the solution participation
        verifyNoInteractions(programmingExerciseParticipationService);
    }

    private static String vcsAccessToken(char fill) {
        return "vcpat-" + String.valueOf(fill).repeat(44);
    }
}
