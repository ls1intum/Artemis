package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.StreamSupport;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.util.GitUtilService;

public class GitServiceTest extends AbstractSpringIntegrationTest {

    @Autowired
    GitUtilService gitUtilService;

    @BeforeEach
    public void beforeEach() throws GitAPIException, InterruptedException {
        gitUtilService.initRepo();
    }

    @AfterEach
    public void afterEach() {
        gitUtilService.deleteRepos();
    }

    @Test
    public void doSomeCommits() {
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
    public void checkoutRepositoryAlreadyOnServer() throws GitAPIException, InterruptedException {
        URL localPath = gitUtilService.getLocalRepoUrlByType(GitUtilService.REPOS.REMOTE);
        String newFileContent = "const a = arr.reduce(sum)";
        gitUtilService.updateFile(GitUtilService.REPOS.REMOTE, GitUtilService.FILES.FILE1, newFileContent);
        gitService.getOrCheckoutRepository(localPath, true);

        assertThat(gitUtilService.getFileContent(GitUtilService.REPOS.REMOTE, GitUtilService.FILES.FILE1)).isEqualTo(newFileContent);
    }

    @Test
    public void checkoutRepositoryNotOnServer() throws GitAPIException, InterruptedException, IOException {
        URL localPath = gitUtilService.getLocalRepoUrlByType(GitUtilService.REPOS.REMOTE);
        gitUtilService.deleteRepo(GitUtilService.REPOS.LOCAL);
        gitService.getOrCheckoutRepository(localPath, true);
        gitUtilService.reinitializeRepo(GitUtilService.REPOS.LOCAL);

        assertThat(gitUtilService.isLocalEqualToRemote()).isTrue();
    }

    @Test
    public void combineAllCommitsIntoInitialCommitTest() throws GitAPIException {
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
    public void combineAllCommitsIntoInitialCommitWithoutNewCommitsTest() throws GitAPIException {
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
