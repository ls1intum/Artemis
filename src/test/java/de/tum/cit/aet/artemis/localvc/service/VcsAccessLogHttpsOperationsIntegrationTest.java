package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
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
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * The VCS access log over HTTPS retains separate advertisement and transfer entries; a transfer never changes the entry of another request.
 * <p>
 * The tests run real JGit clients against the embedded LocalVC server, so they go through the handshake request and the data transfer request of every operation.
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

    private List<VcsAccessLog> entries() {
        return vcsAccessLogRepository.findAllByParticipationId(participation.getId()).stream()
                .filter(entry -> entry.getUser() != null && student1Login.equals(entry.getUser().getLogin())).toList();
    }

    private MockHttpServletRequest authorizedRequest(String method, RepositoryActionType action, AuthenticationMechanism mechanism) {
        var request = new MockHttpServletRequest(method,
                "/git/" + projectKey1 + "/" + assignmentRepositorySlug + ".git/" + (method.equals("GET") ? "info/refs" : "git-upload-pack"));
        var repositoryUri = new LocalVCRepositoryUri(localVCLocalCITestService.buildLocalVCUri(student1Login, projectKey1, assignmentRepositorySlug));
        accessLogRecorder.saveVcsAccessLogForHTTPS(request, repositoryUri, userUtilService.getUserByLogin(student1Login), action, Optional.of(participation), mechanism);
        return request;
    }

    @ParameterizedTest
    @EnumSource(value = AuthenticationMechanism.class, names = { "PASSWORD", "PARTICIPATION_VCS_ACCESS_TOKEN", "USER_VCS_ACCESS_TOKEN", "REPOSITORY_VCS_ACCESS_TOKEN" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void overlappingCloneAndPullTransfersPersistTheirOwnActions(AuthenticationMechanism mechanism) {
        authorizedRequest("GET", RepositoryActionType.READ, mechanism);
        authorizedRequest("GET", RepositoryActionType.READ, mechanism);
        var clone = authorizedRequest("POST", RepositoryActionType.READ, mechanism);
        var pull = authorizedRequest("POST", RepositoryActionType.READ, mechanism);
        var user = userUtilService.getUserByLogin(student1Login);
        var sshPull = vcsAccessLogService.createAccessLog(user, participation, RepositoryActionType.PULL, AuthenticationMechanism.SSH, "ssh-head", "10.0.0.2");
        var agentPull = vcsAccessLogRepository
                .save(new VcsAccessLog(null, participation, "Build agent", "", RepositoryActionType.PULL, AuthenticationMechanism.BUILD_JOB_TOKEN, "agent-head", "10.0.0.3"));

        accessLogRecorder.markFetchAsCloneHTTPS(clone, 0);
        accessLogRecorder.markFetchAsCloneHTTPS(pull, 3);

        assertThat(entries()).extracting(VcsAccessLog::getRepositoryActionType).containsExactly(RepositoryActionType.READ, RepositoryActionType.READ, RepositoryActionType.CLONE,
                RepositoryActionType.PULL, RepositoryActionType.PULL);
        assertThat(entries().subList(0, 4)).extracting(VcsAccessLog::getAuthenticationMechanism).containsOnly(mechanism);
        assertThat(vcsAccessLogRepository.findById(sshPull.getId()).orElseThrow().getRepositoryActionType()).isEqualTo(RepositoryActionType.PULL);
        assertThat(vcsAccessLogRepository.findById(agentPull.getId()).orElseThrow().getRepositoryActionType()).isEqualTo(RepositoryActionType.PULL);
    }

    @ParameterizedTest
    @EnumSource(value = AuthenticationMechanism.class, names = { "PASSWORD", "PARTICIPATION_VCS_ACCESS_TOKEN", "USER_VCS_ACCESS_TOKEN", "REPOSITORY_VCS_ACCESS_TOKEN" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void overlappingPushTransfersPersistOnlyTheirOwnAcceptedHashes(AuthenticationMechanism mechanism) throws Exception {
        String before = headOfTheRepository();
        authorizedRequest("GET", RepositoryActionType.WRITE, mechanism);
        authorizedRequest("GET", RepositoryActionType.WRITE, mechanism);
        var first = authorizedRequest("POST", RepositoryActionType.WRITE, mechanism);
        var second = authorizedRequest("POST", RepositoryActionType.WRITE, mechanism);
        var sshPush = vcsAccessLogService.createAccessLog(userUtilService.getUserByLogin(student1Login), participation, RepositoryActionType.PUSH, AuthenticationMechanism.SSH,
                "ssh-before", "10.0.0.2");

        accessLogRecorder.completePushAccessLogHTTPS(first, "first-pushed");

        assertThat(entries()).extracting(VcsAccessLog::getCommitHash).containsExactly(before, before, "first-pushed", before, "ssh-before");
        accessLogRecorder.completePushAccessLogHTTPS(second, "second-pushed");
        assertThat(entries()).extracting(VcsAccessLog::getCommitHash).containsExactly(before, before, "first-pushed", "second-pushed", "ssh-before");
        assertThat(entries().subList(0, 4)).extracting(VcsAccessLog::getAuthenticationMechanism).containsOnly(mechanism);
        assertThat(vcsAccessLogRepository.findById(sshPush.getId()).orElseThrow().getCommitHash()).isEqualTo("ssh-before");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aCloneLogsItsAdvertisementAndItsCloneTransfer() throws Exception {
        String head = headOfTheRepository();

        try (Git _ = cloneOverHttps()) {
            assertThat(entries()).extracting(VcsAccessLog::getRepositoryActionType).containsExactly(RepositoryActionType.READ, RepositoryActionType.CLONE);
            assertThat(entries().getLast()).satisfies(entry -> {
                assertThat(entry.getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE);
                assertThat(entry.getUser().getLogin()).isEqualTo(student1Login);
                assertThat(entry.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.PASSWORD);
                assertThat(entry.getCommitHash()).isEqualTo(head);
            });
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aPushLogsItsAdvertisementAndTheCommitThatWasPushed() throws Exception {
        String commit = pushNewCommit();

        assertThat(entries()).extracting(VcsAccessLog::getRepositoryActionType).containsExactly(RepositoryActionType.WRITE, RepositoryActionType.PUSH);
        assertThat(entries().getLast()).satisfies(entry -> {
            assertThat(entry.getRepositoryActionType()).isEqualTo(RepositoryActionType.PUSH);
            assertThat(entry.getUser().getLogin()).isEqualTo(student1Login);
            assertThat(entry.getCommitHash()).isEqualTo(commit);
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aFetchOfNewCommitsIsLoggedAsAPull() throws Exception {
        try (Git clone = cloneOverHttps()) {
            String pushed = pushNewCommit();

            localVCLocalCITestService.testFetchSuccessful(clone, student1Login, projectKey1, assignmentRepositorySlug);

            assertThat(entries()).extracting(VcsAccessLog::getRepositoryActionType).containsExactly(RepositoryActionType.READ, RepositoryActionType.CLONE,
                    RepositoryActionType.WRITE, RepositoryActionType.PUSH, RepositoryActionType.READ, RepositoryActionType.PULL);
            assertThat(entries().getLast().getCommitHash()).isEqualTo(pushed);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aFetchWithNothingToTransferRetainsItsReadAdvertisement() throws Exception {
        try (Git clone = cloneOverHttps()) {
            localVCLocalCITestService.testFetchSuccessful(clone, student1Login, projectKey1, assignmentRepositorySlug);

            assertThat(entries()).extracting(VcsAccessLog::getRepositoryActionType).containsExactly(RepositoryActionType.READ, RepositoryActionType.CLONE,
                    RepositoryActionType.READ);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aPushWithNothingToTransferRetainsItsWriteAdvertisement() throws Exception {
        String before = headOfTheRepository();

        localVCLocalCITestService.testPushSuccessful(assignmentRepository.workingCopy(), student1Login, projectKey1, assignmentRepositorySlug);

        assertThat(entries()).extracting(VcsAccessLog::getRepositoryActionType).containsExactly(RepositoryActionType.WRITE);
        assertThat(entries().getFirst().getCommitHash()).isEqualTo(before);
    }

    /**
     * Operations in a row retain their advertisements and transfers without modifying an earlier request.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void severalOperationsInARowKeepTheirOwnEntries() throws Exception {
        String initialHead = headOfTheRepository();
        String firstCommit = pushNewCommit();
        try (Git clone = cloneOverHttps()) {
            String secondCommit = pushNewCommit();
            localVCLocalCITestService.testFetchSuccessful(clone, student1Login, projectKey1, assignmentRepositorySlug);
            try (Git _ = cloneOverHttps()) {
                assertThat(entries()).extracting(VcsAccessLog::getRepositoryActionType).containsExactly(RepositoryActionType.WRITE, RepositoryActionType.PUSH,
                        RepositoryActionType.READ, RepositoryActionType.CLONE, RepositoryActionType.WRITE, RepositoryActionType.PUSH, RepositoryActionType.READ,
                        RepositoryActionType.PULL, RepositoryActionType.READ, RepositoryActionType.CLONE);
                assertThat(entries()).extracting(VcsAccessLog::getCommitHash).containsExactly(initialHead, firstCommit, firstCommit, firstCommit, firstCommit, secondCommit,
                        secondCommit, secondCommit, secondCommit, secondCommit);
            }
        }
    }
}
