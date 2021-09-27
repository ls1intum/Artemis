package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import de.tum.in.www1.artemis.domain.File;
import de.tum.in.www1.artemis.domain.FileType;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.util.GitUtilService;

public class GitServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private GitUtilService gitUtilService;

    @Value("${artemis.version-control.default-branch:master}")
    private String defaultBranch;

    @BeforeEach
    public void beforeEach() {
        gitUtilService.initRepo();
    }

    @AfterEach
    public void afterEach() {
        gitUtilService.deleteRepos();
    }

    @Test
    public void testDoSomeCommits() {
        Collection<ReflogEntry> reflog = gitUtilService.getReflog(GitUtilService.REPOS.LOCAL);
        assertThat(reflog.size()).isEqualTo(1);

        gitUtilService.updateFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1, "lorem ipsum");
        gitUtilService.stashAndCommitAll(GitUtilService.REPOS.LOCAL);

        gitUtilService.updateFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE2, "lorem ipsum solet");
        gitUtilService.stashAndCommitAll(GitUtilService.REPOS.LOCAL);

        reflog = gitUtilService.getReflog(GitUtilService.REPOS.LOCAL);
        assertThat(reflog.size()).isEqualTo(3);
    }

    @Test
    public void testCheckoutRepositoryAlreadyOnServer() throws GitAPIException, InterruptedException {
        gitUtilService.initRepo(defaultBranch);
        var repoUrl = gitUtilService.getRepoUrlByType(GitUtilService.REPOS.REMOTE);
        String newFileContent = "const a = arr.reduce(sum)";
        gitUtilService.updateFile(GitUtilService.REPOS.REMOTE, GitUtilService.FILES.FILE1, newFileContent);
        // Note: the test updates the file, but does not commit the update to the remote repository...
        gitService.getOrCheckoutRepository(repoUrl, true);

        assertThat(gitUtilService.getFileContent(GitUtilService.REPOS.REMOTE, GitUtilService.FILES.FILE1)).isEqualTo(newFileContent);
        // ... therefore it is NOT available in the local repository
        assertThat(gitUtilService.getFileContent(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1)).isNotEqualTo(newFileContent);
    }

    @Test
    public void testCheckoutRepositoryNotOnServer() throws GitAPIException, InterruptedException, IOException {
        var repoUrl = gitUtilService.getRepoUrlByType(GitUtilService.REPOS.REMOTE);
        gitUtilService.deleteRepo(GitUtilService.REPOS.LOCAL);
        gitUtilService.reinitializeLocalRepository();
        gitService.getOrCheckoutRepository(repoUrl, true);
        assertThat(gitUtilService.isLocalEqualToRemote()).isTrue();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(strings = { "master", "main", "someOtherName" })
    public void testResetToOriginHead(String defaultBranch) {
        gitUtilService.initRepo(defaultBranch);
        gitUtilService.updateFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1, "Some Change");
        assertThat(gitUtilService.isLocalEqualToRemote()).isFalse();

        gitService.resetToOriginHead(gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL));

        assertThat(gitUtilService.isLocalEqualToRemote()).isTrue();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(strings = { "master", "main", "someOtherName" })
    public void testGetOriginHead(String defaultBranch) throws GitAPIException {
        gitUtilService.initRepo(defaultBranch);
        // Checkout a different branch in local repo
        gitUtilService.checkoutBranch(GitUtilService.REPOS.LOCAL, "other-branch");

        var repo = gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL);
        assertThat(gitService.getOriginHead(repo)).isEqualTo(defaultBranch);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(strings = { "master", "main", "someOtherName" })
    public void testPushSourceToTargetRepoWithoutBranch(String defaultBranch) throws GitAPIException, IOException {
        gitUtilService.initRepo(defaultBranch);

        Repository localRepo = gitUtilService.getRepoByType(GitUtilService.REPOS.REMOTE);
        var repoUrl = gitUtilService.getRepoUrlByType(GitUtilService.REPOS.REMOTE);

        Git git = new Git(localRepo);
        assertThat(git.getRepository().getBranch()).isEqualTo(defaultBranch);

        gitService.pushSourceToTargetRepo(localRepo, repoUrl);

        assertThat(git.getRepository().getBranch()).isEqualTo(this.defaultBranch);

        if (!this.defaultBranch.equals(defaultBranch)) {
            assertThat(localRepo.getConfig().toText()).doesNotContain(defaultBranch);
        }

        gitUtilService.deleteRepos();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(strings = { "master", "main", "someOtherName" })
    public void testPushSourceToTargetRepoWithBranch(String defaultBranch) throws GitAPIException, IOException {
        gitUtilService.initRepo(defaultBranch);

        Repository localRepo = gitUtilService.getRepoByType(GitUtilService.REPOS.REMOTE);
        var repoUrl = gitUtilService.getRepoUrlByType(GitUtilService.REPOS.REMOTE);

        Git git = new Git(localRepo);
        assertThat(git.getRepository().getBranch()).isEqualTo(defaultBranch);

        gitService.pushSourceToTargetRepo(localRepo, repoUrl, defaultBranch);

        assertThat(git.getRepository().getBranch()).isEqualTo(this.defaultBranch);

        if (!this.defaultBranch.equals(defaultBranch)) {
            assertThat(localRepo.getConfig().toText()).doesNotContain(defaultBranch);
        }

        gitUtilService.deleteRepos();
    }

    @Test
    public void testGetExistingCheckedOutRepositoryByLocalPathRemovesEmptyRepo() throws IOException {
        Repository localRepo = gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL);

        doReturn(localRepo.getLocalPath()).when(gitService).getLocalPathOfRepo(any(), any());

        assertThat(gitService.isRepositoryCached(localRepo.getRemoteRepositoryUrl())).isFalse();

        gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.getLocalPath(), localRepo.getRemoteRepositoryUrl());

        assertThat(gitService.isRepositoryCached(localRepo.getRemoteRepositoryUrl())).isTrue();

        FileUtils.deleteDirectory(localRepo.getLocalPath().toFile());

        Repository repo = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.getLocalPath(), localRepo.getRemoteRepositoryUrl());

        assertThat(gitService.isRepositoryCached(localRepo.getRemoteRepositoryUrl())).isFalse();
        assertThat(repo).isNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("getBranchCombinationsToTest")
    public void testGetExistingCheckedOutRepositoryByLocalPathSetsBranchCorrectly(String defaultBranchVCS, String defaultBranchArtemis) throws IOException {
        gitUtilService.initRepo(defaultBranchVCS);

        Repository localRepo = gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL);

        Repository repo = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.getLocalPath(), localRepo.getRemoteRepositoryUrl(), defaultBranchArtemis);

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
    public void testListFilesAndFolders() {
        Repository localRepo = gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL);

        var map = gitService.listFilesAndFolders(localRepo);

        assertThat(map.size()).isEqualTo(4);
        assertThat(map).containsEntry(new File(gitUtilService.getFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1), localRepo), FileType.FILE);
        assertThat(map).containsEntry(new File(gitUtilService.getFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE2), localRepo), FileType.FILE);
        assertThat(map).containsEntry(new File(gitUtilService.getFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE3), localRepo), FileType.FILE);
        assertThat(map).containsEntry(new File(localRepo.getLocalPath().toFile(), localRepo), FileType.FOLDER);
    }

    @Test
    public void testListFiles() {
        Repository localRepo = gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL);

        var fileList = gitService.listFiles(localRepo);

        assertThat(fileList.size()).isEqualTo(3);
        assertThat(fileList).contains(new File(gitUtilService.getFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1), localRepo));
        assertThat(fileList).contains(new File(gitUtilService.getFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE2), localRepo));
        assertThat(fileList).contains(new File(gitUtilService.getFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE3), localRepo));
        assertThat(fileList).doesNotContain(new File(localRepo.getLocalPath().toFile(), localRepo));
    }

    @Test
    public void testGetFileByName() {
        Repository localRepo = gitUtilService.getRepoByType(GitUtilService.REPOS.LOCAL);

        var presentFile = gitService.getFileByName(localRepo, gitUtilService.getFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1).getName());
        assertThat(presentFile).isPresent();
        assertThat(presentFile).contains(new File(gitUtilService.getFile(GitUtilService.REPOS.LOCAL, GitUtilService.FILES.FILE1), localRepo));

        var nonPresentFile = gitService.getFileByName(localRepo, "NameThatWillNeverBePResent");
        assertThat(nonPresentFile).isNotPresent();
    }

    @Test
    public void testCombineAllCommitsIntoInitialCommitTest() throws GitAPIException {
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
    public void testCombineAllCommitsIntoInitialCommitWithoutNewCommitsTest() throws GitAPIException {
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
}
