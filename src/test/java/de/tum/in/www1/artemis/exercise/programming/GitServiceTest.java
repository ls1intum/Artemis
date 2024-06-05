package de.tum.in.www1.artemis.exercise.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.File;
import de.tum.in.www1.artemis.domain.FileType;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.exception.GitException;
import de.tum.in.www1.artemis.user.UserFactory;
import de.tum.in.www1.artemis.util.GitUtilService;

class GitServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private GitUtilService gitUtilService;

    private static final String TEST_PREFIX = "gitservice";

    @BeforeEach
    void beforeEach() {
        gitUtilService.initRepo();
    }

    @AfterEach
    void afterEach() {
        gitUtilService.deleteRepos();
        gitService.clearCachedRepositories();
    }

    @Test
    void testDoSomeCommits() {
        Collection<ReflogEntry> reflog = gitUtilService.getReflog(GitUtilService.REPOS.LOCAL);
        assertThat(reflog).hasSize(1);

        gitUtilService.updateFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1, "lorem ipsum");
        gitUtilService.stashAndCommitAll(GitUtilService.REPOS.LOCAL);

        gitUtilService.updateFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE2, "lorem ipsum solet");
        gitUtilService.stashAndCommitAll(GitUtilService.REPOS.LOCAL);

        reflog = gitUtilService.getReflog(GitUtilService.REPOS.LOCAL);
        assertThat(reflog).hasSize(3);
    }

    @Test
    void testCheckoutRepositoryAlreadyOnServer() {
        gitUtilService.initRepo(defaultBranch);
        String newFileContent = "const a = arr.reduce(sum)";
        gitUtilService.updateFile(GitUtilService.REPOS.REMOTE, GitUtilService.FILES.FILE1, newFileContent);
        // Note: the test updates the file, but does not commit the update to the remote repository...
        assertThat(gitUtilService.getFileContent(GitUtilService.REPOS.REMOTE, GitUtilService.FILES.FILE1)).isEqualTo(newFileContent);
        // ... therefore it is NOT available in the local repository
        assertThat(gitUtilService.getFileContent(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1)).isNotEqualTo(newFileContent);
    }

    @Test
    void testCheckoutRepositoryNotOnServer() throws GitAPIException, IOException {
        var repoUri = gitUtilService.getRepoUriByType(GitUtilService.REPOS.REMOTE);
        gitUtilService.deleteRepo(GitUtilService.REPOS.LOCAL);
        gitUtilService.reinitializeLocalRepository();
        try (var repo = gitService.getOrCheckoutRepository(repoUri, true)) {
            assertThat(gitUtilService.isLocalEqualToRemote()).isTrue();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(strings = { "master", "main", "someOtherName" })
    void testResetToOriginHead(String defaultBranch) {
        gitUtilService.initRepo(defaultBranch);
        gitUtilService.updateFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1, "Some Change");
        assertThat(gitUtilService.isLocalEqualToRemote()).isFalse();

        try (var repo = gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL)) {
            gitService.resetToOriginHead(repo);

            assertThat(gitUtilService.isLocalEqualToRemote()).isTrue();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(strings = { "master", "main", "someOtherName" })
    void testGetOriginHead(String defaultBranch) throws GitAPIException {
        gitUtilService.initRepo(defaultBranch);
        // Checkout a different branch in local repo
        gitUtilService.checkoutBranch(GitUtilService.REPOS.LOCAL, "other-branch");

        try (var repo = gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL)) {
            assertThat(gitService.getOriginHead(repo)).isEqualTo(defaultBranch);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testCheckoutRepositoryAtCommit(boolean withUri) throws GitAPIException {
        // first commit
        prepareRepositoryContent();
        String commitHash = getCommitHash("my first commit");
        if (withUri) {
            var uri = gitUtilService.getRepoUriByType(GitUtilService.REPOS.LOCAL);
            try (var repo = gitService.checkoutRepositoryAtCommit(uri, commitHash, true)) {
            }
        }
        else {
            try (var repo = gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL)) {
                gitService.checkoutRepositoryAtCommit(repo, commitHash);
            }
        }
        assertThat(gitUtilService.getFileContent(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1)).isEqualTo("lorem ipsum");
        assertThat(gitUtilService.getLog(GitUtilService.REPOS.LOCAL)).hasSize(2);
        assertThat(gitUtilService.getFileContent(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE2)).isEmpty();
    }

    @Test
    void testCheckoutRepositoryAtCommitGitApiExceptionThrowsGitException() {
        String commitHash = "someHash";
        try (var repo = gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL)) {
            assertThatThrownBy(() -> gitService.checkoutRepositoryAtCommit(repo, commitHash)).isInstanceOf(GitException.class);

        }
    }

    @Test
    void testSwitchBackToDefaultBranchHead() throws GitAPIException {
        prepareRepositoryContent();
        String commitHash = getCommitHash("my first commit");
        // switch to different commit at branch
        try (var repo = gitService.checkoutRepositoryAtCommit(gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL), commitHash)) {
            assertThat(gitUtilService.getFileContent(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1)).isEqualTo("lorem ipsum");
            assertThat(gitUtilService.getLog(GitUtilService.REPOS.LOCAL)).hasSize(2);
            assertThat(gitUtilService.getFileContent(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE2)).isEmpty();
            // switch back
            gitService.switchBackToDefaultBranchHead(repo);
            assertThat(gitUtilService.getFileContent(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE2)).isEqualTo("lorem ipsum solet");
            // switch to different branch
            gitUtilService.checkoutBranch(GitUtilService.REPOS.LOCAL, "my-other-branch", true);
            // switch back again
            gitService.switchBackToDefaultBranchHead(repo);
            assertThat(gitUtilService.getFileContent(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1)).isEqualTo("lorem ipsum");
            assertThat(gitUtilService.getFileContent(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE2)).isEqualTo("lorem ipsum solet");
        }
    }

    @NotNull
    private String getCommitHash(String msg) {
        AtomicReference<String> commitHash = new AtomicReference<>();
        gitUtilService.getLog(GitUtilService.REPOS.LOCAL).forEach(revCommit -> {
            if (msg.equals(revCommit.getFullMessage())) {
                commitHash.set(revCommit.getId().getName());
            }
        });
        return commitHash.get();
    }

    private void prepareRepositoryContent() {
        // first commit
        gitUtilService.updateFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1, "lorem ipsum");
        gitUtilService.stashAndCommitAll(GitUtilService.REPOS.LOCAL, "my first commit");
        // second commit
        gitUtilService.updateFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE2, "lorem ipsum solet");
        gitUtilService.stashAndCommitAll(GitUtilService.REPOS.LOCAL, "my second commit");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(strings = { "master", "main", "someOtherName" })
    void testPushSourceToTargetRepoWithBranch(String defaultBranch) throws GitAPIException, IOException {
        gitUtilService.initRepo(defaultBranch);

        Repository localRepo = gitUtilService.getRepoByType(GitUtilService.REPOS.REMOTE);
        var repoUri = gitUtilService.getRepoUriByType(GitUtilService.REPOS.REMOTE);

        Git git = new Git(localRepo);
        assertThat(git.getRepository().getBranch()).isEqualTo(defaultBranch);

        gitService.pushSourceToTargetRepo(localRepo, repoUri, defaultBranch);

        assertThat(git.getRepository().getBranch()).isEqualTo(this.defaultBranch);

        if (!this.defaultBranch.equals(defaultBranch)) {
            assertThat(localRepo.getConfig().toText()).doesNotContain(defaultBranch);
        }

        gitUtilService.deleteRepos();
    }

    @Test
    void testGetExistingCheckedOutRepositoryByLocalPathRemovesEmptyRepo() throws IOException {
        Repository localRepo = gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL);

        doReturn(localRepo.getLocalPath()).when(gitService).getLocalPathOfRepo(any(), any());

        assertThat(gitService.isRepositoryCached(localRepo.getRemoteRepositoryUri())).isFalse();

        gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.getLocalPath(), localRepo.getRemoteRepositoryUri());

        assertThat(gitService.isRepositoryCached(localRepo.getRemoteRepositoryUri())).isTrue();

        FileUtils.deleteDirectory(localRepo.getLocalPath().toFile());

        Repository repo = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.getLocalPath(), localRepo.getRemoteRepositoryUri());

        assertThat(gitService.isRepositoryCached(localRepo.getRemoteRepositoryUri())).isFalse();
        assertThat(repo).isNull();
    }

    @ParameterizedTest
    @MethodSource("getRepositoryWithParticipationParameter")
    void testGetRepositoryWithParticipation(boolean hideStudentName, boolean zipOutput, @TempDir Path outputDir) throws IOException {
        String login = TEST_PREFIX + "student1";

        gitUtilService.initParticipationRepo(UserFactory.generateActivatedUser(login));

        Repository localRepo = gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL);
        Path outputFile = gitService.getRepositoryWithParticipation(localRepo, outputDir.toString(), hideStudentName, zipOutput);

        assertThat(outputFile.getParent()).isEqualTo(outputDir);
        if (zipOutput) {
            assertThat(outputFile).isRegularFile();
            assertThat(outputFile.toString()).endsWith(".zip");
        }
        else {
            assertThat(outputFile).isNotEmptyDirectory();
        }
        if (hideStudentName) {
            assertThat(outputFile.getFileName().toString()).doesNotContain(login);
        }
        else {
            assertThat(outputFile.getFileName().toString()).contains(login);
        }
        gitService.deleteLocalRepository(localRepo);
    }

    private static Stream<Arguments> getRepositoryWithParticipationParameter() {
        List<Boolean> booleans = List.of(true, false);

        return booleans.stream().flatMap(firstParameter -> booleans.stream().map(secondParameter -> arguments(firstParameter, secondParameter)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("getBranchCombinationsToTest")
    void testGetExistingCheckedOutRepositoryByLocalPathSetsBranchCorrectly(String defaultBranchVCS, String defaultBranchArtemis) throws IOException {
        gitUtilService.initRepo(defaultBranchVCS);

        Repository localRepo = gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL);

        Repository repo = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.getLocalPath(), localRepo.getRemoteRepositoryUri(), defaultBranchArtemis);

        assertThat(repo.getConfig().getString(ConfigConstants.CONFIG_BRANCH_SECTION, defaultBranchArtemis, ConfigConstants.CONFIG_REMOTE_SECTION)).isEqualTo("origin");
        assertThat(repo.getConfig().getString(ConfigConstants.CONFIG_BRANCH_SECTION, defaultBranchArtemis, ConfigConstants.CONFIG_MERGE_SECTION))
                .isEqualTo("refs/heads/" + defaultBranchArtemis);

        gitService.deleteLocalRepository(localRepo);
    }

    private static Stream<Arguments> getBranchCombinationsToTest() {
        List<String> branchNames = List.of("master", "main", "someOtherName");

        return branchNames.stream().flatMap(firstParameter -> branchNames.stream().map(secondParameter -> arguments(firstParameter, secondParameter)));
    }

    @Test
    void testListFilesAndFolders() {
        Repository localRepo = gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL);

        var map = gitService.listFilesAndFolders(localRepo);

        assertThat(map).hasSize(4).containsEntry(new File(gitUtilService.getFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1), localRepo), FileType.FILE)
                .containsEntry(new File(gitUtilService.getFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE2), localRepo), FileType.FILE)
                .containsEntry(new File(gitUtilService.getFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE3), localRepo), FileType.FILE)
                .containsEntry(new File(localRepo.getLocalPath().toFile(), localRepo), FileType.FOLDER);
    }

    @Test
    void testListFiles() {
        Repository localRepo = gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL);

        var fileList = gitService.listFiles(localRepo);

        assertThat(fileList).hasSize(3).contains(new File(gitUtilService.getFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1), localRepo))
                .contains(new File(gitUtilService.getFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE2), localRepo))
                .contains(new File(gitUtilService.getFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE3), localRepo))
                .doesNotContain(new File(localRepo.getLocalPath().toFile(), localRepo));
    }

    @Test
    void testGetFileByName() {
        Repository localRepo = gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL);

        var presentFile = gitService.getFileByName(localRepo, gitUtilService.getFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1).getName());
        assertThat(presentFile).contains(new File(gitUtilService.getFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1), localRepo));

        var nonPresentFile = gitService.getFileByName(localRepo, "NameThatWillNeverBePResent");
        assertThat(nonPresentFile).isNotPresent();
    }

    @Test
    void testCombineAllCommitsIntoInitialCommitTest() throws GitAPIException {
        String newFileContent1 = "lorem ipsum";
        String newFileContent2 = "lorem ipsum solet";
        String fileContent = gitUtilService.getFileContent(GitUtilService.REPOS.REMOTE, GitUtilService.FILES.FILE3);

        // These commits should be combined into the initial commit
        gitUtilService.updateFile(GitUtilService.REPOS.REMOTE, GitUtilService.FILES.FILE1, newFileContent1);
        gitUtilService.stashAndCommitAll(GitUtilService.REPOS.REMOTE);
        gitUtilService.updateFile(GitUtilService.REPOS.REMOTE, GitUtilService.FILES.FILE2, newFileContent2);
        gitUtilService.stashAndCommitAll(GitUtilService.REPOS.REMOTE);

        // This commit should be removed
        gitUtilService.updateFile(GitUtilService.REPOS.REMOTE, GitUtilService.FILES.FILE3, fileContent);
        gitUtilService.stashAndCommitAll(GitUtilService.REPOS.REMOTE);

        gitService.combineAllCommitsIntoInitialCommit(gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL));

        Arrays.stream(GitUtilService.REPOS.values()).forEach(repo -> {
            Iterable<RevCommit> commits = gitUtilService.getLog(repo);
            Long numberOfCommits = StreamSupport.stream(commits.spliterator(), false).count();

            String fileContent1 = gitUtilService.getFileContent(repo, GitUtilService.FILES.FILE1);
            String fileContent2 = gitUtilService.getFileContent(repo, GitUtilService.FILES.FILE2);
            String fileContent3 = gitUtilService.getFileContent(repo, GitUtilService.FILES.FILE3);

            assertThat(numberOfCommits).isEqualTo(1L);
            assertThat(fileContent1).isEqualTo(newFileContent1);
            assertThat(fileContent2).isEqualTo(newFileContent2);
            assertThat(fileContent3).isEqualTo(fileContent);
        });
    }

    @Test
    void testCombineAllCommitsIntoInitialCommitWithoutNewCommitsTest() throws GitAPIException {
        String oldFileContent1 = gitUtilService.getFileContent(GitUtilService.REPOS.REMOTE, GitUtilService.FILES.FILE1);
        String oldFileContent2 = gitUtilService.getFileContent(GitUtilService.REPOS.REMOTE, GitUtilService.FILES.FILE2);
        String oldFileContent3 = gitUtilService.getFileContent(GitUtilService.REPOS.REMOTE, GitUtilService.FILES.FILE3);

        gitService.combineAllCommitsIntoInitialCommit(gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL));

        Arrays.stream(GitUtilService.REPOS.values()).forEach(repo -> {
            Iterable<RevCommit> commits = gitUtilService.getLog(repo);
            Long numberOfCommits = StreamSupport.stream(commits.spliterator(), false).count();

            String fileContent1 = gitUtilService.getFileContent(repo, GitUtilService.FILES.FILE1);
            String fileContent2 = gitUtilService.getFileContent(repo, GitUtilService.FILES.FILE2);
            String fileContent3 = gitUtilService.getFileContent(repo, GitUtilService.FILES.FILE3);

            assertThat(numberOfCommits).isEqualTo(1L);
            assertThat(fileContent1).isEqualTo(oldFileContent1);
            assertThat(fileContent2).isEqualTo(oldFileContent2);
            assertThat(fileContent3).isEqualTo(oldFileContent3);
        });
    }

    @Test
    void testGetCommitsInfo() throws GitAPIException {
        prepareRepositoryContent();
        var commitsInfos = gitService.getCommitInfos(gitUtilService.getRepoUriByType(GitUtilService.REPOS.LOCAL));
        assertThat(commitsInfos).hasSize(3);
        assertThat(commitsInfos.get(0).hash()).isEqualTo(getCommitHash("my second commit"));
        assertThat(commitsInfos.get(0).message()).isEqualTo("my second commit");
        assertThat(commitsInfos.get(1).hash()).isEqualTo(getCommitHash("my first commit"));
        assertThat(commitsInfos.get(1).message()).isEqualTo("my first commit");
        assertThat(commitsInfos.get(2).hash()).isEqualTo(getCommitHash("initial commit"));
        assertThat(commitsInfos.get(2).message()).isEqualTo("initial commit");
    }
}
