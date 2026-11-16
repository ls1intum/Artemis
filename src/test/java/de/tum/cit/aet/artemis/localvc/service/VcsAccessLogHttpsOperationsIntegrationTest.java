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
import org.springframework.beans.factory.annotation.Autowired;
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
 * The VCS access log of git operations over HTTPS: every operation writes exactly one entry, of the kind of operation it was, and an operation never changes the entry of another.
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
        return vcsAccessLogRepository.findAllByParticipationId(participation.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aCloneIsLoggedOnceAsAClone() throws Exception {
        String head = headOfTheRepository();

        try (Git _ = cloneOverHttps()) {
            assertThat(entries()).singleElement().satisfies(entry -> {
                assertThat(entry.getRepositoryActionType()).isEqualTo(RepositoryActionType.CLONE);
                assertThat(entry.getUser().getLogin()).isEqualTo(student1Login);
                assertThat(entry.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.PASSWORD);
                assertThat(entry.getCommitHash()).isEqualTo(head);
            });
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aPushIsLoggedOnceAsAPushWithTheCommitThatWasPushed() throws Exception {
        String commit = pushNewCommit();

        assertThat(entries()).singleElement().satisfies(entry -> {
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

            assertThat(entries()).extracting(VcsAccessLog::getRepositoryActionType).containsExactly(RepositoryActionType.CLONE, RepositoryActionType.PUSH,
                    RepositoryActionType.PULL);
            assertThat(entries().getLast().getCommitHash()).isEqualTo(pushed);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aFetchWithNothingToTransferIsLoggedAsAPull() throws Exception {
        try (Git clone = cloneOverHttps()) {
            localVCLocalCITestService.testFetchSuccessful(clone, student1Login, projectKey1, assignmentRepositorySlug);

            assertThat(entries()).extracting(VcsAccessLog::getRepositoryActionType).containsExactly(RepositoryActionType.CLONE, RepositoryActionType.PULL);
        }
    }

    /**
     * Operations in a row, as a student does them in an exam: each one writes the entry of its own kind, and none of them touches an entry that an earlier one wrote.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void severalOperationsInARowEachWriteOneEntryOfTheirOwnKind() throws Exception {
        String firstCommit = pushNewCommit();
        try (Git clone = cloneOverHttps()) {
            String secondCommit = pushNewCommit();
            localVCLocalCITestService.testFetchSuccessful(clone, student1Login, projectKey1, assignmentRepositorySlug);
            try (Git _ = cloneOverHttps()) {
                assertThat(entries()).extracting(VcsAccessLog::getRepositoryActionType).containsExactly(RepositoryActionType.PUSH, RepositoryActionType.CLONE,
                        RepositoryActionType.PUSH, RepositoryActionType.PULL, RepositoryActionType.CLONE);
                assertThat(entries()).extracting(VcsAccessLog::getCommitHash).containsExactly(firstCommit, firstCommit, secondCommit, secondCommit, secondCommit);
            }
        }
    }
}
