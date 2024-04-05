package de.tum.in.www1.artemis.util;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;

import de.tum.in.www1.artemis.service.connectors.GitService;

/**
 * This class describes a local repository cloned from an origin repository.
 * In the case of using the local VCS with the local CIS instead of, e.g. Gitlab and Jenkins, the local VCS contains the origin repositories,
 * they are just not kept in an external system, but rather in another folder that belongs to Artemis.
 */
public class LocalRepository {

    public File localRepoFile;

    public File originRepoFile;

    public Git localGit;

    public Git originGit;

    private final String defaultBranch;

    public LocalRepository(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public static Git initialize(File filePath, String defaultBranch) throws GitAPIException {
        return Git.init().setDirectory(filePath).setInitialBranch(defaultBranch).call();
    }

    public void configureRepos(String localRepoFileName, String originRepoFileName) throws Exception {

        this.localRepoFile = Files.createTempDirectory(localRepoFileName).toFile();
        this.localGit = initialize(localRepoFile, defaultBranch);

        this.originRepoFile = Files.createTempDirectory(originRepoFileName).toFile();
        this.originGit = initialize(originRepoFile, defaultBranch);

        this.localGit.remoteAdd().setName("origin").setUri(new URIish(String.valueOf(this.originRepoFile))).call();
    }

    /**
     * Configures the local and origin repositories instantiating the origin repository as a bare repository and making sure the default branch name is set correctly.
     *
     * @param localRepoFileName      name of the local repository to be used as the prefix for the temporary folder
     * @param originRepositoryFolder path to the origin repository folder already created
     * @throws IOException        if e.g. creating the temporary directory fails
     * @throws GitAPIException    if e.g. initializing the remote repository fails
     * @throws URISyntaxException if creating a URI from the origin repository folder fails
     */
    public void configureRepos(String localRepoFileName, Path originRepositoryFolder) throws IOException, GitAPIException, URISyntaxException {

        Path localRepoPath = Files.createTempDirectory(localRepoFileName);
        this.localRepoFile = localRepoPath.toFile();
        this.localGit = initialize(localRepoFile, defaultBranch);

        this.originRepoFile = originRepositoryFolder.toFile();
        // Create a bare remote repository.
        this.originGit = Git.init().setDirectory(originRepositoryFolder.toFile()).setBare(true).call();

        this.localGit.remoteAdd().setName("origin").setUri(new URIish(String.valueOf(this.originRepoFile))).call();

        // Modify the HEAD file to contain the correct branch. Otherwise, cloning the repository does not work.
        Repository repository = originGit.getRepository();
        RefUpdate refUpdate = repository.getRefDatabase().newUpdate(Constants.HEAD, false);
        refUpdate.setForceUpdate(true);
        refUpdate.link("refs/heads/" + defaultBranch);

        // Push a file to the remote repository to create the default branch there.
        // This is needed because the local CI system only considers pushes that update the existing default branch.
        Path filePath = localRepoPath.resolve("test.txt");
        Files.createFile(filePath);
        localGit.add().addFilepattern("test.txt").call();
        GitService.commit(localGit).setMessage("Initial commit").call();
        localGit.push().setRemote("origin").call();
    }

    public void resetLocalRepo() throws IOException {
        if (this.localGit != null) {
            this.localGit.close();
        }
        if (this.localRepoFile != null && this.localRepoFile.exists()) {
            FileUtils.deleteDirectory(this.localRepoFile);
        }

        if (this.originGit != null) {
            this.originGit.close();
        }
        if (this.originRepoFile != null && this.originRepoFile.exists()) {
            FileUtils.deleteDirectory(this.originRepoFile);
        }
    }

    public List<RevCommit> getAllLocalCommits() throws Exception {
        return StreamSupport.stream(this.localGit.log().call().spliterator(), false).toList();
    }

    public List<RevCommit> getAllOriginCommits() throws Exception {
        return StreamSupport.stream(this.originGit.log().call().spliterator(), false).toList();
    }
}
