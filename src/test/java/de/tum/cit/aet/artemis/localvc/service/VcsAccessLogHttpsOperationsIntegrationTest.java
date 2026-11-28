package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.localci.service.LocalVCLocalCITestService;
import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.service.VcsAccessLogOperations;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * The VCS access log over HTTPS stores what each request learned, when the request is over, and never changes it afterwards. The log that is shown has one entry per operation.
 * <p>
 * The tests run real JGit clients against the embedded LocalVC server, so they go through the handshake request and the data transfer request of every operation. The entries
 * are read like the endpoint of the log does, which leaves out the advertisement of an operation that went on to move data.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class VcsAccessLogHttpsOperationsIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "vcsaccesshttps";

    private LocalVCTestRepository assignmentRepository;

    private ProgrammingExerciseStudentParticipation participation;

    private final List<Path> clones = new ArrayList<>();

    private int pushedCommits;

    @Autowired
    private TempFileUtilService tempFileUtilService;

    @Autowired
    private LocalVCAccessLogRecorderService accessLogRecorder;

    @Autowired
    private VcsAccessLogService vcsAccessLogService;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeAll
    void setupAll() {
        CredentialsProvider.setDefault(new UsernamePasswordCredentialsProvider(localVCUsername, localVCPassword));
    }

    @BeforeEach
    void initRepositories() throws GitAPIException, IOException, URISyntaxException, InvalidNameException {
        assignmentRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, assignmentRepositorySlug);

        var student1 = new LdapUserDto().login(student1Login);
        student1.setUid(new LdapName("cn=student1,ou=test,o=lab"));
        var fakeUser = new LdapUserDto().login(localVCBaseUsername);
        fakeUser.setUid(new LdapName("cn=" + localVCBaseUsername + ",ou=test,o=lab"));
        doReturn(Optional.of(student1)).when(ldapUserService).findByLogin(student1.getLogin());
        doReturn(Optional.of(fakeUser)).when(ldapUserService).findByLogin(localVCBaseUsername);
        doReturn(true).when(ldapTemplate).compare(anyString(), anyString(), any());

        dockerClientTestService.mockInspectImage(dockerClient);

        participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        vcsAccessLogRepository.deleteAll();
        pushedCommits = 0;
    }

    @AfterEach
    void removeRepositories() throws IOException {
        assignmentRepository.deleteWorkingCopy();
        for (Path clone : clones) {
            FileUtils.deleteDirectory(clone.toFile());
        }
        clones.clear();
    }

    private Git cloneOverHttps() throws GitAPIException, IOException {
        Path directory = tempFileUtilService.createTempDirectory("vcs-access-log-clone");
        clones.add(directory);
        return Git.cloneRepository().setURI(localVCLocalCITestService.buildLocalVCUri(student1Login, projectKey1, assignmentRepositorySlug)).setDirectory(directory.toFile())
                .setCredentialsProvider(LocalVCLocalCITestService.ONLY_THE_CREDENTIALS_IN_THE_URI).call();
    }

    private String pushNewCommit() throws Exception {
        String commit = localVCLocalCITestService.commitFile(assignmentRepository.workingCopyPath(), assignmentRepository.workingCopy(), "file-" + (++pushedCommits) + ".txt");
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.workingCopy(), student1Login, projectKey1, assignmentRepositorySlug);
        return commit;
    }

    private String headOfTheRepository() throws IOException {
        return assignmentRepository.workingCopy().getRepository().resolve("HEAD").name();
    }

    /**
     * The entries of operations that moved data are written when the operation is over, which is just after the client has its answer, so the tests wait for them. The
     * advertisement of such an operation is written first and is not shown once its transfer is there, so an entry that is only an advertisement means the operation is still
     * running.
     */
    private List<VcsAccessLog> awaitEntries(int count) {
        await().atMost(Duration.ofSeconds(10)).until(() -> entries().size() >= count && entries().stream().noneMatch(VcsAccessLogHttpsOperationsIntegrationTest::isAdvertisement));
        return entries();
    }

    /**
     * The entries when the operations did not move data, which is what the advertisement of each of them is.
     */
    private List<VcsAccessLog> awaitEntriesWithAccesses(int count) {
        await().atMost(Duration.ofSeconds(10)).until(() -> entries().size() >= count);
        return entries();
    }

    private static boolean isAdvertisement(VcsAccessLog entry) {
        return entry.getRepositoryActionType() == RepositoryActionType.READ || entry.getRepositoryActionType() == RepositoryActionType.WRITE;
    }

    /**
     * Nothing more is written. There is no event to wait for, so the log has to stay as it is for a moment.
     */
    private void assertNoMoreEntriesThan(int count) {
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(entries()).hasSize(count));
    }

    /** What the log shows: one entry per operation. */
    private List<VcsAccessLog> entries() {
        return VcsAccessLogOperations.oneEntryPerOperation(vcsAccessLogRepository.findAllByParticipationId(participation.getId())).stream()
                .filter(entry -> entry.getUser() != null && student1Login.equals(entry.getUser().getLogin())).toList();
    }

    /** What was stored, with the advertisement of every operation. */
    private List<VcsAccessLog> storedEntries() {
        return vcsAccessLogRepository.findAllByParticipationId(participation.getId()).stream()
                .filter(entry -> entry.getUser() != null && student1Login.equals(entry.getUser().getLogin())).toList();
    }

    private MockHttpServletRequest authorizedRequest(String method, RepositoryActionType action, AuthenticationMechanism mechanism) {
        var request = new MockHttpServletRequest(method,
                "/git/" + projectKey1 + "/" + assignmentRepositorySlug + ".git/" + (method.equals("GET") ? "info/refs" : "git-upload-pack"));
        accessLogRecorder.rememberSubjectHTTPS(request, userUtilService.getUserByLogin(student1Login), Optional.of(new AccessLogTarget.OfParticipation(participation.getId())),
                mechanism);
        return request;
    }

    private static List<Object> kindAndHash(VcsAccessLog entry) {
        return List.of(entry.getRepositoryActionType(), entry.getCommitHash());
    }

    /**
     * Operations that overlap end in another order than they began, and the entries of other transports and of build agents are around them. Every request owns what its hooks
     * learned, so each operation is written for itself, whatever the others do.
     */
    @ParameterizedTest
    @EnumSource(value = AuthenticationMechanism.class, names = { "PASSWORD", "PARTICIPATION_VCS_ACCESS_TOKEN", "USER_VCS_ACCESS_TOKEN", "REPOSITORY_VCS_ACCESS_TOKEN" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void overlappingOperationsEachWriteTheirOwnEntry(AuthenticationMechanism mechanism) {
        var advertisement = authorizedRequest("GET", RepositoryActionType.READ, mechanism);
        var cloneOne = authorizedRequest("POST", RepositoryActionType.READ, mechanism);
        var cloneTwo = authorizedRequest("POST", RepositoryActionType.READ, mechanism);
        var pull = authorizedRequest("POST", RepositoryActionType.READ, mechanism);
        var acceptedPush = authorizedRequest("POST", RepositoryActionType.WRITE, mechanism);
        var rejectedPush = authorizedRequest("POST", RepositoryActionType.WRITE, mechanism);
        var pushWithoutCommands = authorizedRequest("POST", RepositoryActionType.WRITE, mechanism);
        var user = userUtilService.getUserByLogin(student1Login);
        var sshPull = vcsAccessLogService.createAccessLog(user, participation.getId(), RepositoryActionType.PULL, AuthenticationMechanism.SSH, "ssh-head", "10.0.0.2");
        var agentPull = vcsAccessLogRepository
                .save(new VcsAccessLog(null, participation, "Build agent", "", RepositoryActionType.PULL, AuthenticationMechanism.BUILD_JOB_TOKEN, "agent-head", "10.0.0.3"));

        accessLogRecorder.auditOf(cloneOne).onBeginNegotiateRound(0);
        accessLogRecorder.auditOf(cloneOne).onPackSent("head-one");
        accessLogRecorder.auditOf(cloneTwo).onBeginNegotiateRound(0);
        accessLogRecorder.auditOf(cloneTwo).onPackSent("head-two");
        accessLogRecorder.auditOf(pull).onBeginNegotiateRound(3);
        accessLogRecorder.auditOf(pull).onPackSent("head-three");
        accessLogRecorder.auditOf(acceptedPush).onPushReceived("before-accepted");
        accessLogRecorder.auditOf(acceptedPush).onPushAccepted("pushed");
        accessLogRecorder.auditOf(rejectedPush).onPushReceived("before-rejected");

        // they end in another order than they began
        accessLogRecorder.recordFetchHTTPS(pull);
        accessLogRecorder.recordFetchHTTPS(cloneTwo);
        accessLogRecorder.recordPushHTTPS(acceptedPush);
        accessLogRecorder.recordFetchHTTPS(advertisement);
        accessLogRecorder.recordPushHTTPS(rejectedPush);
        accessLogRecorder.recordPushHTTPS(pushWithoutCommands);
        accessLogRecorder.recordFetchHTTPS(cloneOne);

        var written = entries().stream().filter(entry -> entry.getAuthenticationMechanism() == mechanism).toList();
        assertThat(written).extracting(VcsAccessLogHttpsOperationsIntegrationTest::kindAndHash).containsExactly(List.of(RepositoryActionType.PULL, "head-three"),
                List.of(RepositoryActionType.CLONE, "head-two"), List.of(RepositoryActionType.PUSH, "pushed"), List.of(RepositoryActionType.PUSH_FAIL, "before-rejected"),
                List.of(RepositoryActionType.CLONE, "head-one"));
        assertThat(vcsAccessLogRepository.findById(sshPull.getId()).orElseThrow()).extracting(VcsAccessLog::getRepositoryActionType, VcsAccessLog::getCommitHash)
                .containsExactly(RepositoryActionType.PULL, "ssh-head");
        assertThat(vcsAccessLogRepository.findById(agentPull.getId()).orElseThrow()).extracting(VcsAccessLog::getRepositoryActionType, VcsAccessLog::getCommitHash)
                .containsExactly(RepositoryActionType.PULL, "agent-head");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aCloneIsLoggedOnceAsAClone() throws Exception {
        String head = headOfTheRepository();

        try (Git _ = cloneOverHttps()) {
            assertThat(awaitEntries(1)).singleElement().satisfies(entry -> {
                assertThat(entry.getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE);
                assertThat(entry.getUser().getLogin()).isEqualTo(student1Login);
                assertThat(entry.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.PASSWORD);
                assertThat(entry.getCommitHash()).isEqualTo(head);
            });
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aPushIsLoggedOnceWithTheCommitThatWasPushed() throws Exception {
        String commit = pushNewCommit();

        assertThat(awaitEntries(1)).singleElement().satisfies(entry -> {
            assertThat(entry.getRepositoryActionType()).isEqualTo(RepositoryActionType.PUSH);
            assertThat(entry.getUser().getLogin()).isEqualTo(student1Login);
            assertThat(entry.getCommitHash()).isEqualTo(commit);
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aFetchOfNewCommitsIsLoggedOnceAsAPull() throws Exception {
        try (Git clone = cloneOverHttps()) {
            String pushed = pushNewCommit();
            awaitEntries(2);

            localVCLocalCITestService.testFetchSuccessful(clone, student1Login, projectKey1, assignmentRepositorySlug);

            assertThat(awaitEntries(3)).extracting(VcsAccessLog::getRepositoryActionType).containsExactly(RepositoryActionType.CLONE, RepositoryActionType.PUSH,
                    RepositoryActionType.PULL);
            assertThat(entries().getLast().getCommitHash()).isEqualTo(pushed);
        }
    }

    /**
     * Somebody accessed the repository even though there was nothing to fetch, and the log shows it as a read of the newest commit.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aFetchThatIsUpToDateIsLoggedAsARead() throws Exception {
        String head = headOfTheRepository();
        try (Git clone = cloneOverHttps()) {
            awaitEntries(1);
            localVCLocalCITestService.testFetchSuccessful(clone, student1Login, projectKey1, assignmentRepositorySlug);

            assertThat(awaitEntriesWithAccesses(2)).extracting(VcsAccessLogHttpsOperationsIntegrationTest::kindAndHash).containsExactly(List.of(RepositoryActionType.CLONE, head),
                    List.of(RepositoryActionType.READ, head));
            assertNoMoreEntriesThan(2);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aPushWithNothingNewIsLoggedAsAWrite() throws Exception {
        String head = headOfTheRepository();

        localVCLocalCITestService.testPushSuccessful(assignmentRepository.workingCopy(), student1Login, projectKey1, assignmentRepositorySlug);

        assertThat(awaitEntriesWithAccesses(1)).singleElement().satisfies(entry -> {
            assertThat(entry.getRepositoryActionType()).isEqualTo(RepositoryActionType.WRITE);
            assertThat(entry.getCommitHash()).isEqualTo(head);
        });
        assertNoMoreEntriesThan(1);
    }

    /**
     * Credentials that do not hold are checked before the references are advertised, so a refused push follows no advertisement of its own and takes nothing away from the access
     * of an earlier push that had nothing new.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aPushWithAWrongPasswordLeavesAnEarlierPushWithNothingNewInTheLog() throws Exception {
        String head = headOfTheRepository();
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.workingCopy(), student1Login, projectKey1, assignmentRepositorySlug);
        awaitEntriesWithAccesses(1);

        localVCLocalCITestService.testPushReturnsError(assignmentRepository.workingCopy(), student1Login, "wrong-password", projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);

        assertThat(awaitEntriesWithAccesses(2)).extracting(VcsAccessLogHttpsOperationsIntegrationTest::kindAndHash).containsExactly(List.of(RepositoryActionType.WRITE, head),
                List.of(RepositoryActionType.PUSH_FAIL, ""));
        assertNoMoreEntriesThan(2);
    }

    /**
     * The advertisement and the transfer of one clone are two requests that nothing links, and both are stored. The log joins them when it is read, so what is shown is one
     * entry per operation, while nothing that was stored is ever changed.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void theAdvertisementOfAnOperationThatMovedDataIsStoredButNotShown() throws Exception {
        try (Git _ = cloneOverHttps()) {
            awaitEntries(1);

            assertThat(storedEntries()).extracting(VcsAccessLog::getRepositoryActionType).containsExactly(RepositoryActionType.READ, RepositoryActionType.CLONE);
            assertThat(entries()).extracting(VcsAccessLog::getRepositoryActionType).containsExactly(RepositoryActionType.CLONE);
        }
    }

    /**
     * A push that the server refuses brought commands, so it is an operation, but not a push of the commit: it is a failed push, with the commit that the repository still has.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aPushThatIsRefusedIsLoggedOnceAsAFailedPushWithTheCommitFromBefore() throws Exception {
        String before = headOfTheRepository();
        assignmentRepository.workingCopy().checkout().setCreateBranch(true).setName("a-branch-that-is-not-allowed").call();
        localVCLocalCITestService.commitFile(assignmentRepository.workingCopyPath(), assignmentRepository.workingCopy(), "refused.txt");

        pushRefusedBranch();

        assertThat(awaitEntries(1)).singleElement().satisfies(entry -> {
            assertThat(entry.getRepositoryActionType()).isEqualTo(RepositoryActionType.PUSH_FAIL);
            assertThat(entry.getCommitHash()).isEqualTo(before);
        });
    }

    private void pushRefusedBranch() throws Exception {
        // the server answers this push with a refusal of the branch, which JGit reports as a rejected ref update rather than as an exception
        var results = assignmentRepository.workingCopy().push().setRemote(localVCLocalCITestService.buildLocalVCUri(student1Login, projectKey1, assignmentRepositorySlug))
                .setCredentialsProvider(LocalVCLocalCITestService.ONLY_THE_CREDENTIALS_IN_THE_URI).setRefSpecs(new RefSpec("refs/heads/a-branch-that-is-not-allowed")).call();
        assertThat(results).flatExtracting(result -> result.getRemoteUpdates()).extracting(update -> update.getStatus()).containsOnly(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
    }

    /**
     * Credentials that do not hold are a failed attempt of the user they name, once, whatever kind of operation it was. The first request of every operation carries no
     * credentials at all and is no attempt.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aFetchAndAPushWithAWrongPasswordAreLoggedOnceEachAsFailedAttempts() throws Exception {
        try (Git clone = cloneOverHttps()) {
            vcsAccessLogRepository.deleteAll();

            localVCLocalCITestService.testFetchReturnsError(clone, student1Login, "wrong-password", projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
            localVCLocalCITestService.testPushReturnsError(assignmentRepository.workingCopy(), student1Login, "wrong-password", projectKey1, assignmentRepositorySlug,
                    NOT_AUTHORIZED);

            assertThat(awaitEntries(2)).extracting(VcsAccessLog::getRepositoryActionType).containsExactlyInAnyOrder(RepositoryActionType.CLONE_FAIL,
                    RepositoryActionType.PUSH_FAIL);
            assertThat(entries()).extracting(VcsAccessLog::getAuthenticationMechanism).containsOnly(AuthenticationMechanism.PASSWORD);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aFetchWithoutAnyPasswordIsNoAttempt() throws Exception {
        try (Git clone = cloneOverHttps()) {
            vcsAccessLogRepository.deleteAll();

            localVCLocalCITestService.testFetchReturnsError(clone, student1Login, "", projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);

            assertNoMoreEntriesThan(0);
        }
    }

    /**
     * Operations in a row each write one entry of their own, and none of them changes an earlier one.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void severalOperationsInARowKeepTheirOwnEntries() throws Exception {
        String firstCommit = pushNewCommit();
        awaitEntries(1);
        try (Git clone = cloneOverHttps()) {
            awaitEntries(2);
            String secondCommit = pushNewCommit();
            awaitEntries(3);
            localVCLocalCITestService.testFetchSuccessful(clone, student1Login, projectKey1, assignmentRepositorySlug);
            awaitEntries(4);
            try (Git _ = cloneOverHttps()) {
                assertThat(awaitEntries(5)).extracting(VcsAccessLogHttpsOperationsIntegrationTest::kindAndHash).containsExactly(List.of(RepositoryActionType.PUSH, firstCommit),
                        List.of(RepositoryActionType.CLONE, firstCommit), List.of(RepositoryActionType.PUSH, secondCommit), List.of(RepositoryActionType.PULL, secondCommit),
                        List.of(RepositoryActionType.CLONE, secondCommit));
            }
        }
    }
}
