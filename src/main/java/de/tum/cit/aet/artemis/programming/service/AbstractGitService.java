package de.tum.cit.aet.artemis.programming.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Optional;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

public abstract class AbstractGitService {

    private static final Logger log = LoggerFactory.getLogger(AbstractGitService.class);

    protected static final int JGIT_TIMEOUT_IN_SECONDS = 5;

    protected static final String REMOTE_NAME = "origin";

    @Value("${artemis.version-control.url}")
    protected URI localVCBaseUri;

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

    protected AbstractGitService() {
        log.debug("file.encoding={}", Charset.defaultCharset().displayName());
        log.debug("sun.jnu.encoding={}", System.getProperty("sun.jnu.encoding"));
        log.debug("Default Charset={}", Charset.defaultCharset());
        log.debug("Default Charset in Use={}", new OutputStreamWriter(new ByteArrayOutputStream()).getEncoding());
    }

    /**
     * Creates a JGit repository instance using the provided local path and remote repository URI with specific configurations.
     * This method sets up the repository to avoid auto garbage collection and disables the use of symbolic links to enhance security.
     * It also makes sure that the default branch and HEAD are correctly configured.
     * Works for both, local checkout repositories and bare repositories (without working directory).
     * The method disables automatic garbage collection of the repository to prevent potential issues with file deletion
     * as discussed in multiple resources. It also avoids the use of symbolic links for security reasons,
     * following best practices against remote code execution vulnerabilities.
     * References:
     * <ul>
     * <li><a href="https://stackoverflow.com/questions/45266021/java-jgit-files-delete-fails-to-delete-a-file-but-file-delete-succeeds">JGit
     * Files.delete() fails to delete a file, but file.delete() succeeds</a></li>
     * <li><a href="https://git-scm.com/docs/git-gc">Git garbage collection</a></li>
     * <li><a href="https://www.eclipse.org/lists/jgit-dev/msg03734.html">How to completely disable auto GC in JGit</a></li>
     * </ul>
     *
     * @param localPath           The path to the local repository directory, not null.
     * @param remoteRepositoryUri The URI of the remote repository, not null.
     * @param defaultBranch       The name of the default branch to be checked out, not null.
     * @param isBare              Whether the repository is a bare repository (without working directory)
     * @param writeAccess         Whether we write to the repository or not. If true, we set the git Repo config for better performance.
     * @return The configured Repository instance.
     * @throws IOException             If an I/O error occurs during repository initialization or configuration.
     * @throws InvalidRefNameException If the provided default branch name is invalid.
     */
    @NotNull
    public static Repository linkRepositoryForExistingGit(Path localPath, LocalVCRepositoryUri remoteRepositoryUri, String defaultBranch, boolean isBare, boolean writeAccess)
            throws IOException, InvalidRefNameException {
        // Open the repository from the filesystem
        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        if (isBare) {
            builder.setBare();
            builder.setGitDir(localPath.toFile());
        }
        else {
            builder.setGitDir(localPath.resolve(".git").toFile());
        }
        builder.setInitialBranch(defaultBranch).setMustExist(true).readEnvironment().findGitDir().setup(); // scan environment GIT_* variables

        try (Repository repository = new Repository(builder, localPath, remoteRepositoryUri)) {
            // Read JavaDoc for more information
            if (writeAccess) {
                setRepoConfig(defaultBranch, repository);
            }

            return repository;
        }
    }

    private static void setRepoConfig(String defaultBranch, Repository repository) throws IOException {
        StoredConfig gitRepoConfig = repository.getConfig();
        gitRepoConfig.setInt(ConfigConstants.CONFIG_GC_SECTION, null, ConfigConstants.CONFIG_KEY_AUTO, 0);
        gitRepoConfig.setBoolean(ConfigConstants.CONFIG_GC_SECTION, null, ConfigConstants.CONFIG_KEY_AUTODETACH, false);
        gitRepoConfig.setInt(ConfigConstants.CONFIG_GC_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOPACKLIMIT, 0);
        gitRepoConfig.setBoolean(ConfigConstants.CONFIG_RECEIVE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOGC, false);

        // disable symlinks to avoid security issues such as remote code execution
        gitRepoConfig.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_SYMLINKS, false);
        gitRepoConfig.setBoolean(ConfigConstants.CONFIG_COMMIT_SECTION, null, ConfigConstants.CONFIG_KEY_GPGSIGN, false);
        gitRepoConfig.setString(ConfigConstants.CONFIG_BRANCH_SECTION, defaultBranch, ConfigConstants.CONFIG_REMOTE_SECTION, REMOTE_NAME);
        gitRepoConfig.setString(ConfigConstants.CONFIG_BRANCH_SECTION, defaultBranch, ConfigConstants.CONFIG_MERGE_SECTION, "refs/heads/" + defaultBranch);

        // Important for new / empty repositories so the default branch is set correctly.
        RefUpdate refUpdate = repository.getRefDatabase().newUpdate(Constants.HEAD, false);
        refUpdate.setForceUpdate(true);
        refUpdate.link("refs/heads/" + defaultBranch);

        gitRepoConfig.save();
    }

    /**
     * Opens an existing bare repository from the filesystem. Avoids write operations for git settings necessary for new repositories
     * and ensures that the repository is set up correctly with the specified default branch.
     *
     * @param localPath         The path to the local repository directory, not null.
     * @param bareRepositoryUri The URI of the bare repository, not null.
     * @param defaultBranch     The name of the default branch to be checked out, not null.
     * @return The configured Repository instance.
     * @throws IOException             If an I/O error occurs during repository initialization or configuration.
     * @throws InvalidRefNameException If the provided default branch name is invalid.
     */
    @NotNull
    public static Repository getExistingBareRepository(Path localPath, LocalVCRepositoryUri bareRepositoryUri, String defaultBranch) throws IOException, InvalidRefNameException {
        // Open the repository from the filesystem
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.setBare();
        builder.setGitDir(localPath.toFile());
        builder.setInitialBranch(defaultBranch).setMustExist(true).readEnvironment().findGitDir().setup(); // scan environment GIT_* variables

        try (Repository repository = new Repository(builder, localPath, bareRepositoryUri)) {
            return repository;
        }
    }

    @NotNull
    protected static Repository openCheckedOutRepositoryFromFileSystem(Path localPath, LocalVCRepositoryUri remoteRepositoryUri, String defaultBranch)
            throws IOException, InvalidRefNameException {

        return linkRepositoryForExistingGit(localPath, remoteRepositoryUri, defaultBranch, false, false);
    }

    /**
     * Get last commit hash from HEAD
     *
     * @param repoUri to get the latest hash from.
     * @return the latestHash of the given repo.
     * @throws EntityNotFoundException if retrieving the latestHash from the git repo failed.
     */
    @Nullable
    public ObjectId getLastCommitHash(LocalVCRepositoryUri repoUri) throws EntityNotFoundException {
        if (repoUri == null || repoUri.getURI() == null) {
            return null;
        }
        // Get HEAD ref of repo without cloning it locally
        try {
            log.debug("getLastCommitHash {}", repoUri);
            var headRef = lsRemoteCommand().setRemote(getGitUriAsString(repoUri)).callAsMap().get(Constants.HEAD);

            if (headRef == null) {
                return null;
            }

            return headRef.getObjectId();
        }
        catch (GitAPIException | URISyntaxException ex) {
            throw new EntityNotFoundException("Could not retrieve the last commit hash for repoUri " + repoUri + " due to the following exception: " + ex);
        }
    }

    protected String getGitUriAsString(LocalVCRepositoryUri vcsRepositoryUri) throws URISyntaxException {
        return getGitUri(vcsRepositoryUri).toString();
    }

    protected abstract URI getGitUri(LocalVCRepositoryUri vcsRepositoryUri) throws URISyntaxException;

    protected static URI getSshUri(LocalVCRepositoryUri vcsRepositoryUri, Optional<String> sshUrlTemplate) throws URISyntaxException {
        URI templateUri = new URI(sshUrlTemplate.orElseThrow());
        // Example: ssh://git@artemis.tum.de:2222/se2021w07h02/se2021w07h02-ga27yox.git
        final var repositoryUri = vcsRepositoryUri.getURI();
        final var path = repositoryUri.getPath();
        return new URI(templateUri.getScheme(), templateUri.getUserInfo(), templateUri.getHost(), templateUri.getPort(), path, null, repositoryUri.getFragment());
    }

    /**
     * Deletes a local repository folder.
     *
     * @param repository Local Repository Object.
     * @throws IOException if the deletion of the repository failed.
     */
    public void deleteLocalRepository(Repository repository) throws IOException {
        Path repoPath = repository.getLocalPath();
        // if repository is not closed, it causes weird IO issues when trying to delete the repository again
        // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
        repository.closeBeforeDelete();
        FileUtils.deleteDirectory(repoPath.toFile());
        log.debug("Deleted Repository at {}", repoPath);
    }

    protected abstract LsRemoteCommand lsRemoteCommand();
}
