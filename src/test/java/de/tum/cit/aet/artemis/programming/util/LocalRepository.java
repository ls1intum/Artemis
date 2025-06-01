package de.tum.cit.aet.artemis.programming.util;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.stream.StreamSupport;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.programming.service.GitService;

/**
 * This class describes a local repository cloned from an origin repository.
 * In the case of using LocalVC with LocalCI, LocalVC contains the origin repositories,
 * they are just not kept in an external system, but rather in another folder that belongs to Artemis.
 */
// TODO we should NOT use this class anymore for server tests and instead write proper integration tests that use the LocalVC service and the Artemis server API without mocking
// repositories
public class LocalRepository {

    private static final Logger log = LoggerFactory.getLogger(LocalRepository.class);

    public File workingCopyGitRepoFile;

    public File bareGitRepoFile;

    public Git workingCopyGitRepo;

    public Git bareGitRepo;

    private final String defaultBranch;

    public LocalRepository(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public static Git initialize(File filePath, String defaultBranch, boolean bare) throws GitAPIException, IOException {

        Files.createDirectories(filePath.toPath());

        // Create a bare local repository with JGit.
        Git git = Git.init().setDirectory(filePath).setInitialBranch(defaultBranch).setBare(bare).call();

        // Change the default branch to the Artemis default branch.
        Repository repository = git.getRepository();
        RefUpdate refUpdate = repository.getRefDatabase().newUpdate(Constants.HEAD, false);
        refUpdate.setForceUpdate(true);
        refUpdate.link("refs/heads/" + defaultBranch);

        return git;
    }

    /**
     * Configures the local and origin repositories, instantiating the origin repository as a bare repository if specified. The default branch name will be set accordingly.
     *
     * @param repoBasePath       The base path where the repositories will be created
     * @param localRepoFileName  The name of the directory in which the local repository will be created
     * @param originRepoFileName The name of the directory in which the origin repository will be created
     * @param originIsBare       Whether the origin repository should be bare or not. Set this to false only if you need to create files in the origin repository.
     */
    public void configureRepos(Path repoBasePath, String localRepoFileName, String originRepoFileName, boolean originIsBare) throws Exception {
        workingCopyGitRepoFile = getRepoPath(repoBasePath, localRepoFileName).toFile();
        workingCopyGitRepo = initialize(workingCopyGitRepoFile, defaultBranch, false);

        bareGitRepoFile = getRepoPath(repoBasePath, originRepoFileName).toFile();
        bareGitRepo = initialize(bareGitRepoFile, defaultBranch, originIsBare);

        workingCopyGitRepo.remoteAdd().setName("origin").setUri(new URIish(bareGitRepoFile.toURI().toString())).call();

        // Add an initial commit directly in the local working copy
        File readme = new File(workingCopyGitRepoFile, "README.md");
        Files.write(readme.toPath(), "Initial commit".getBytes());
        workingCopyGitRepo.add().addFilepattern("README.md").call();
        workingCopyGitRepo.commit().setMessage("Initial commit").call();

        // Push the initial commit to the origin (bare or not)
        workingCopyGitRepo.push().setRemote("origin").setPushAll().call();
        log.info("Configured local repository with one commit, working copy at {} and origin repository at {}", workingCopyGitRepoFile, bareGitRepoFile);
    }

    /**
     * Configures the local repository and the origin repository, instantiating the origin repository as a bare repository and setting the default branch name accordingly.
     *
     * @param repoBasePath       The base path where the repositories will be created
     * @param localRepoFileName  The name of the directory in which the local repository will be created
     * @param originRepoFileName The name of the directory in which the origin repository will be created
     */
    public void configureRepos(Path repoBasePath, String localRepoFileName, String originRepoFileName) throws Exception {
        this.configureRepos(repoBasePath, localRepoFileName, originRepoFileName, true);
    }

    /**
     * Configures the local and origin repositories instantiating the origin repository as a bare repository and making sure the default branch name is set correctly.
     *
     * @param repoBasePath           The base path where the repositories will be created
     * @param localRepoFileName      name of the local repository to be used as the prefix for the folder
     * @param originRepositoryFolder path to the origin repository folder already created
     * @throws IOException        if e.g. creating the directory fails
     * @throws GitAPIException    if e.g. initializing the remote repository fails
     * @throws URISyntaxException if creating a URI from the origin repository folder fails
     */
    public void configureRepos(Path repoBasePath, String localRepoFileName, Path originRepositoryFolder) throws IOException, GitAPIException, URISyntaxException {

        Path localRepoPath = getRepoPath(repoBasePath, localRepoFileName);
        workingCopyGitRepoFile = localRepoPath.toFile();
        workingCopyGitRepo = initialize(workingCopyGitRepoFile, defaultBranch, false);

        bareGitRepoFile = originRepositoryFolder.toFile();
        // Create a bare remote repository.
        bareGitRepo = initialize(bareGitRepoFile, defaultBranch, true);

        workingCopyGitRepo.remoteAdd().setName("origin").setUri(new URIish(String.valueOf(bareGitRepoFile))).call();

        // Modify the HEAD file to contain the correct branch. Otherwise, cloning the repository does not work.
        Repository repository = bareGitRepo.getRepository();
        RefUpdate refUpdate = repository.getRefDatabase().newUpdate(Constants.HEAD, false);
        refUpdate.setForceUpdate(true);
        refUpdate.link("refs/heads/" + defaultBranch);

        // Push a file to the remote repository to create the default branch there.
        // This is needed because the local CI system only considers pushes that update the existing default branch.
        Path filePath = localRepoPath.resolve("test.txt");
        Files.createFile(filePath);
        workingCopyGitRepo.add().addFilepattern("test.txt").call();
        GitService.commit(workingCopyGitRepo).setMessage("Initial commit").call();
        workingCopyGitRepo.push().setRemote("origin").call();
    }

    // This method tries to build a valid, but unique (random) LocalVCRepositoryUri from the given base path and local repository file name.
    private static Path getRepoPath(@NotNull Path repoBasePath, @NotNull String localRepoFileName) {
        var tempPrefix = getTempPrefix(6);
        return repoBasePath.resolve("git").resolve(tempPrefix).resolve(tempPrefix + "-" + localRepoFileName + ".git");
    }

    private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private static String getTempPrefix(int length) {
        Random random = new Random();
        StringBuilder prefix = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(CHARACTERS.length());
            prefix.append(CHARACTERS.charAt(index));
        }
        return prefix.toString();
    }

    public void resetLocalRepo() throws IOException {
        if (workingCopyGitRepo != null) {
            workingCopyGitRepo.close();
        }
        if (workingCopyGitRepoFile != null && workingCopyGitRepoFile.exists()) {
            FileUtils.deleteDirectory(workingCopyGitRepoFile);
        }

        if (bareGitRepo != null) {
            bareGitRepo.close();
        }
        if (bareGitRepoFile != null && bareGitRepoFile.exists()) {
            FileUtils.deleteDirectory(bareGitRepoFile);
        }
    }

    public List<RevCommit> getAllLocalCommits() throws Exception {
        return StreamSupport.stream(workingCopyGitRepo.log().call().spliterator(), false).toList();
    }

    public List<RevCommit> getAllOriginCommits() throws Exception {
        return StreamSupport.stream(bareGitRepo.log().call().spliterator(), false).toList();
    }
}
