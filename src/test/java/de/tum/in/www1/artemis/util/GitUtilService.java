package de.tum.in.www1.artemis.util;

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
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;

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

    private Git localGit;

    private Git remoteGit;

    /**
     * Initializes the repository with three dummy files
     */
    public void initRepo() {
        initRepo(defaultBranch);
    }

    /**
     * Initializes the repository with three dummy files
     * @param defaultBranch The default branch name of the repository
     */
    public void initRepo(String defaultBranch) {
        try {
            deleteRepos();

            remoteGit = LocalRepository.initialize(remotePath.toFile(), defaultBranch);
            // create some files in the remote repository
            remotePath.resolve(FILES.FILE1.toString()).toFile().createNewFile();
            remotePath.resolve(FILES.FILE2.toString()).toFile().createNewFile();
            remotePath.resolve(FILES.FILE3.toString()).toFile().createNewFile();
            remoteGit.add().addFilepattern(".").call();
            remoteGit.commit().setMessage("initial commit").call();

            // clone remote repository
            localGit = Git.cloneRepository().setURI(remotePath.toString()).setDirectory(localPath.toFile()).call();

            reinitializeLocalRepository();
            reinitializeRemoteRepository();
        }
        catch (IOException | GitAPIException ex) {
            System.out.println(ex);
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
        try {
            if (remoteRepo != null) {
                remoteRepo.close();
            }
            if (localRepo != null) {
                localRepo.close();
            }
            if (localGit != null) {
                localGit.close();
            }
            if (remoteGit != null) {
                remoteGit.close();
            }
            FileUtils.deleteDirectory(localPath.toFile());
            FileUtils.deleteDirectory(remotePath.toFile());
            localRepo = null;
            remoteRepo = null;
        }
        catch (IOException ignored) {
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
        try {
            var fileName = Path.of(getCompleteRepoPathStringByType(repo), fileToUpdate.toString()).toString();
            PrintWriter writer = new PrintWriter(fileName, StandardCharsets.UTF_8);
            writer.print(content);
            writer.close();
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
        try {
            Git git = new Git(getRepoByType(repo));
            git.add().addFilepattern(".").call();
            git.commit().setMessage("new commit").call();
        }
        catch (GitAPIException ignored) {
        }
    }

    /**
     * Checks out a branch of the repository. If the branch doesn't exist yet, it gets created
     * @param repo The repository on which the action should be operated
     * @param branch The branch that should be checked out
     */
    public void checkoutBranch(REPOS repo, String branch) {
        checkoutBranch(repo, branch, true);
    }

    /**
     * @param repo The repository on which the action should be operated
     * @param branch The branch that should be checked out
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

    public VcsRepositoryUrl getRepoUrlByType(REPOS repo) {
        return new VcsRepositoryUrl(new File(getCompleteRepoPathStringByType(repo)));
    }

    public static final class MockFileRepositoryUrl extends VcsRepositoryUrl {

        public MockFileRepositoryUrl(File file) {
            super(file);
        }

        @Override
        public VcsRepositoryUrl withUser(String username) {
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
