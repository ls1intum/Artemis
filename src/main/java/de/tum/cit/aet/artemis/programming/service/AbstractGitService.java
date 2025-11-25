package de.tum.cit.aet.artemis.programming.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
     * @param writeAccess         Whether we write to the repository or not. If true, the method sets the git Repo config for better performance.
     * @return The configured Repository instance.
     * @throws IOException             If an I/O error occurs during repository initialization or configuration.
     * @throws InvalidRefNameException If the provided default branch name is invalid.
     */
    @NonNull
    public static Repository linkRepositoryForExistingGit(Path localPath, LocalVCRepositoryUri remoteRepositoryUri, String defaultBranch, boolean isBare, boolean writeAccess)
            throws IOException, InvalidRefNameException {
        // Configure a JGit repository builder for an already existing repository on disk
        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        if (isBare) {
            // Bare repository: repository lives directly in localPath (no working tree), this is typically used for the "remote" repository
            builder.setBare();
            builder.setGitDir(localPath.toFile());
        }
        else {
            // Non-bare repository: working tree at localPath, metadata in .git/
            builder.setGitDir(localPath.resolve(".git").toFile());
        }

        builder.setInitialBranch(defaultBranch) // used when initializing / linking branches
                .setMustExist(true)              // fail fast if the repository does not exist
                .readEnvironment()               // honor standard GIT_* environment variables
                .findGitDir()                    // keep builder behavior consistent if GIT_DIR is set
                .setup();                        // finalize builder configuration

        try (Repository repository = new Repository(builder, localPath, remoteRepositoryUri)) {
            // Apply safe default Git configuration (GC, symlinks, commit signing, HEAD, etc.)
            // Only modify config if write access to the repository is needed
            if (writeAccess) {
                setRepoConfig(defaultBranch, repository);
            }

            return repository;
        }
    }

    /**
     * Configures essential repository settings for a newly created or existing Git repository.
     * <p>
     * This method adjusts several Git configuration options to ensure predictable,
     * secure, and stable behavior when the repository is used programmatically.
     * Key aspects include:
     * </p>
     *
     * <ul>
     * <li><strong>Garbage collection:</strong> Disables automatic GC, auto-detach,
     * and auto-pack operations to avoid unexpected background maintenance tasks
     * that may interfere with server-side automation or testing workflows.</li>
     *
     * <li><strong>Security hardening:</strong> Explicitly disables symbolic links
     * to prevent potential security vulnerabilities such as remote code
     * execution on systems where symlink handling may be unsafe.</li>
     *
     * <li><strong>Commit signing:</strong> Turns off automatic GPG signing, ensuring
     * consistent commit creation in environments where signing keys may not be
     * available.</li>
     *
     * <li><strong>Branch configuration:</strong> Sets the remote tracking branch
     * and merge reference for the given {@code defaultBranch} so that Git
     * operations (e.g., merges or pulls) behave correctly.</li>
     *
     * <li><strong>HEAD initialization:</strong> For empty repositories, forcefully
     * links {@code HEAD} to the default branch. This is required to ensure the
     * new branch becomes the initial checked-out branch.</li>
     * </ul>
     *
     * <p>
     * After applying all settings, the updated configuration is persisted to disk.
     * </p>
     *
     * @param defaultBranch the name of the default branch (e.g. {@code "main"})
     * @param repository    the JGit {@link Repository} to configure
     * @throws IOException if the configuration cannot be written to disk
     */
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
    @NonNull
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

    @NonNull
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
    public ObjectId getLastCommitHash(@Nullable LocalVCRepositoryUri repoUri) throws EntityNotFoundException {
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

    /**
     * Retrieves the hash of the first commit in a bare Git repository whose commit message contains
     * a given search string. The method iterates commits in chronological order (oldest to newest)
     * to ensure that the earliest matching commit is returned.
     *
     * <p>
     * If no commit message contains the specified string, the method falls back to returning the
     * hash of the very first (oldest) commit in the repository. This guarantees that the method always
     * returns a deterministic commit reference, even when no match is found.
     *
     * <p>
     * The repository is assumed to be a bare Git repository (i.e., without a working tree).
     * The method opens it directly using {@link FileRepositoryBuilder} and performs a {@link RevWalk}
     * to traverse commit history starting from all available branch heads.
     *
     * <p>
     * Algorithmic details:
     * <ul>
     * <li>Commits are sorted in ascending chronological order using {@code RevSort.REVERSE}.</li>
     * <li>The iteration stops immediately when a commit message containing the given substring is found.</li>
     * <li>If no such commit exists, the first (oldest) commit encountered is returned as a fallback.</li>
     * <li>The method operates in O(N) time, where N is the number of commits, but performs efficiently
     * even for hundreds of commits (a few milliseconds for ~200 commits).</li>
     * </ul>
     *
     * @param repository the Git repository (bare) to search within.
     * @param message    the commit message substring to search for (case-sensitive).
     * @return the {@link ObjectId} of the first commit whose message contains {@code message},
     *         or the {@link ObjectId} of the oldest commit if no match is found;
     *         or {@code null} if the repository URI is invalid or inaccessible.
     * @throws EntityNotFoundException if the repository cannot be opened or traversed.
     */
    @Nullable
    public ObjectId getFirstCommitWithMessage(Repository repository, String message) throws EntityNotFoundException {

        try {
            try (RevWalk walk = new RevWalk(repository)) {

                // Sort oldest → newest
                walk.sort(RevSort.COMMIT_TIME_DESC, true);
                walk.sort(RevSort.REVERSE, true);

                // Mark all branch heads as starting points
                for (Ref ref : repository.getRefDatabase().getRefsByPrefix(Constants.R_HEADS)) {
                    ObjectId tip = ref.getObjectId();
                    if (tip != null) {
                        walk.markStart(walk.parseCommit(tip));
                    }
                }

                RevCommit firstCommit = null;

                for (RevCommit commit : walk) {
                    // Remember the first (oldest) commit as fallback
                    if (firstCommit == null) {
                        firstCommit = commit;
                    }

                    String msg = commit.getFullMessage();
                    if (msg != null && msg.contains(message)) {
                        return commit.getId(); // Found a match, return early
                    }
                }

                // Fallback: return the first (oldest) commit if no message matched
                if (firstCommit != null) {
                    return firstCommit.getId();
                }

                return null;
            }
        }
        catch (Exception ex) {
            throw new EntityNotFoundException(
                    "Could not retrieve the commit hash with message '" + message + "' for repository " + repository.getRemoteRepositoryUri() + ": " + ex);
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
    public void deleteLocalRepository(@NonNull Repository repository) throws IOException {
        Path repoPath = repository.getLocalPath();
        // if repository is not closed, it causes weird IO issues when trying to delete the repository again
        // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
        repository.closeBeforeDelete();
        FileUtils.deleteDirectory(repoPath.toFile());
        log.debug("Deleted Repository at {}", repoPath);
    }

    protected abstract LsRemoteCommand lsRemoteCommand();
}
