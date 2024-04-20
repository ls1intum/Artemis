package de.tum.in.www1.artemis.util;

import static org.assertj.core.api.Fail.fail;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.service.connectors.GitService;

@Service
public class GitUtilService {

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    // Note: the first string has to be same as artemis.repo-clone-path (see src/test/resources/config/application-artemis.yml) because here local git repos will be cloned
    private final Path localPath = Path.of(".", "repos", "server-integration-test").resolve("test-repository").normalize();

    private final Path remotePath = Files.createTempDirectory("remotegittest").resolve("scm/test-repository");

    public GitUtilService() throws IOException {
    }

    public enum FILES {
        FILE1, FILE2, FILE3
    }

    public enum REPOS {
        LOCAL, REMOTE
    }

    private Repository remoteRepo;

    private Repository localRepo;

    /**
     * Initializes the repository with three dummy files
     */
    public void initRepo() {
        initRepo(defaultBranch);
    }

    /**
     * Initializes the repository with three dummy files
     *
     * @param defaultBranch The default branch name of the repository
     */
    public void initRepo(String defaultBranch) {
        try {
            deleteRepos();

            Files.createDirectories(remotePath);
            Git remoteGit = LocalRepository.initialize(remotePath.toFile(), defaultBranch);
            // create some files in the remote repository
            remotePath.resolve(FILES.FILE1.toString()).toFile().createNewFile();
            remotePath.resolve(FILES.FILE2.toString()).toFile().createNewFile();
            remotePath.resolve(FILES.FILE3.toString()).toFile().createNewFile();
            remoteGit.add().addFilepattern(".").call();
            GitService.commit(remoteGit).setMessage("initial commit").call();

            // clone remote repository
            Git localGit = Git.cloneRepository().setURI(remotePath.toString()).setDirectory(localPath.toFile()).call();

            reinitializeLocalRepository();
            reinitializeRemoteRepository();

            localGit.close();
            remoteGit.close();
        }
        catch (IOException | GitAPIException ex) {
            fail(ex.getMessage(), ex);
        }
    }

    public void reinitializeLocalRepository() throws IOException {
        localRepo = initializeRepo(localPath);
    }

    public void reinitializeRemoteRepository() throws IOException {
        remoteRepo = initializeRepo(remotePath);
    }

    private Repository initializeRepo(Path path) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.setGitDir(path.resolve(".git").toFile()).readEnvironment().findGitDir().setup(); // scan environment GIT_* variables
        return new Repository(builder, path, null);
    }

    public void deleteRepos() {
        if (remoteRepo != null) {
            remoteRepo.closeBeforeDelete();
        }
        if (localRepo != null) {
            localRepo.closeBeforeDelete();
        }

        try {
            tryToDeleteDirectory(localPath);
            localRepo = null;
            tryToDeleteDirectory(remotePath);
            remoteRepo = null;
        }
        catch (Exception e) {
            fail("Failed while deleting the repositories", e);
        }
    }

    private void tryToDeleteDirectory(Path path) throws Exception {
        for (int i = 0; i < 10 && FileUtils.isDirectory(path.toFile()); i++) {
            try {
                FileUtils.deleteDirectory(path.toFile());
            }
            catch (IOException e) {
                Thread.sleep(10);
            }
        }

        if (FileUtils.isDirectory(path.toFile())) {
            fail("Could not delete directory with path " + path);
        }
    }

    public void deleteRepo(REPOS repo) {
        try {
            String repoPath = getCompleteRepoPathStringByType(repo);
            FileUtils.deleteDirectory(new File(repoPath));
            setRepositoryToNull(repo);
        }
        catch (IOException ignored) {
        }
    }

    public Collection<ReflogEntry> getReflog(REPOS repo) {
        try {
            Git git = new Git(getRepoByType(repo));
            return git.reflog().call();
        }
        catch (GitAPIException ignored) {
        }
        return null;
    }

    public Iterable<RevCommit> getLog(REPOS repo) {
        try {
            Git git = new Git(getRepoByType(repo));
            return git.log().call();
        }
        catch (GitAPIException ignored) {
        }
        return null;
    }

    public void updateFile(REPOS repo, FILES fileToUpdate, String content) {
        var fileName = Path.of(getCompleteRepoPathStringByType(repo), fileToUpdate.toString()).toString();
        try (PrintWriter writer = new PrintWriter(fileName, StandardCharsets.UTF_8)) {
            writer.print(content);
        }
        catch (IOException ignored) {
        }
    }

    public File getFile(REPOS repo, FILES fileToRead) {
        Path path = Path.of(getCompleteRepoPathStringByType(repo), fileToRead.toString());
        return path.toFile();
    }

    public String getFileContent(REPOS repo, FILES fileToRead) {
        try {
            Path path = Path.of(getCompleteRepoPathStringByType(repo), fileToRead.toString());
            byte[] encoded = Files.readAllBytes(path);
            return new String(encoded, Charset.defaultCharset());
        }
        catch (IOException ex) {
            return null;
        }
    }

    public void stashAndCommitAll(REPOS repo) {
        stashAndCommitAll(repo, "new commit");
    }

    public void stashAndCommitAll(REPOS repo, String commitMsg) {
        try (Git git = new Git(getRepoByType(repo))) {
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage(commitMsg).call();
        }
        catch (GitAPIException ignored) {
        }
    }

    /**
     * Checks out a branch of the repository. If the branch doesn't exist yet, it gets created
     *
     * @param repo   The repository on which the action should be operated
     * @param branch The branch that should be checked out
     */
    public void checkoutBranch(REPOS repo, String branch) {
        checkoutBranch(repo, branch, true);
    }

    /**
     * @param repo         The repository on which the action should be operated
     * @param branch       The branch that should be checked out
     * @param createBranch indicator if a non-existing branch should get created
     */
    public void checkoutBranch(REPOS repo, String branch, boolean createBranch) {
        try {
            Git git = new Git(getRepoByType(repo));
            git.checkout().setCreateBranch(createBranch).setName(branch).call();
            git.close();
        }
        catch (GitAPIException ignored) {
        }
    }

    public boolean isLocalEqualToRemote() {
        String fileContentLocal1 = getFileContent(REPOS.LOCAL, GitUtilService.FILES.FILE1);
        String fileContentLocal2 = getFileContent(REPOS.LOCAL, GitUtilService.FILES.FILE2);
        String fileContentLocal3 = getFileContent(REPOS.LOCAL, GitUtilService.FILES.FILE3);

        String fileContentRemote1 = getFileContent(REPOS.REMOTE, GitUtilService.FILES.FILE1);
        String fileContentRemote2 = getFileContent(REPOS.REMOTE, GitUtilService.FILES.FILE2);
        String fileContentRemote3 = getFileContent(REPOS.REMOTE, GitUtilService.FILES.FILE3);

        return fileContentLocal1.equals(fileContentRemote1) && fileContentLocal2.equals(fileContentRemote2) && fileContentLocal3.equals(fileContentRemote3);
    }

    public void setRepositoryToNull(REPOS repo) {
        if (repo == REPOS.LOCAL) {
            localRepo = null;
        }
        else if (repo == REPOS.REMOTE) {
            remoteRepo = null;
        }
    }

    public Repository getRepoByType(REPOS repo) {
        return repo == REPOS.LOCAL ? localRepo : remoteRepo;
    }

    public String getCompleteRepoPathStringByType(REPOS repo) {
        return repo == REPOS.LOCAL ? localPath.toString() : remotePath.toString();
    }

    public VcsRepositoryUri getRepoUriByType(REPOS repo) {
        return new VcsRepositoryUri(new File(getCompleteRepoPathStringByType(repo)));
    }

    public static final class MockFileRepositoryUri extends VcsRepositoryUri {

        public MockFileRepositoryUri(File file) {
            super(file);
        }

        @Override
        public VcsRepositoryUri withUser(String username) {
            // the mocked url should already include the user specific part
            return this;
        }

    }

    public void writeEmptyJsonFileToPath(Path path) throws Exception {
        var fileContent = "{}";
        path.toFile().getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(path.toFile(), StandardCharsets.UTF_8)) {
            writer.write(fileContent);
        }
    }
}
