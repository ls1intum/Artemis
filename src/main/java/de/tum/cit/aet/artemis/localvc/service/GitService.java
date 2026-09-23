package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.BinaryFileExtensionConfiguration.isBinaryFile;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefLeaseSpec;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.programming.domain.File;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.exception.GitException;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class GitService extends AbstractGitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    /** The exercise name inside a repository folder name, which a JPlag checkout replaces with the participation id. */
    private static final Pattern EXERCISE_NAME_IN_FOLDER_NAME = Pattern.compile("/[a-zA-Z0-9]*-");

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCBasePath;

    @Value("${artemis.repo-clone-path}")
    private Path repoClonePath;

    @Value("${artemis.git.name}")
    private String artemisGitName;

    @Value("${artemis.git.email}")
    private String artemisGitEmail;

    private final RepositoryCheckoutLocks checkoutLocks = new RepositoryCheckoutLocks();

    /**
     * Returns the checked-out repository's current local HEAD. Unlike {@link #getLastCommitHash(LocalVCRepositoryUri)}, this reads the exact working copy after checkout/pull and
     * after a local commit, avoiding a second remote read that could observe an unrelated concurrent push.
     *
     * @param repository the checked-out repository
     * @return the local HEAD commit hash, or {@code null} when the repository has no HEAD yet
     * @throws IOException if HEAD cannot be resolved
     */
    public String getLocalHeadHash(Repository repository) throws IOException {
        ObjectId head = repository.resolve(Constants.HEAD);
        return head == null ? null : head.name();
    }

    /**
     * Get the URI for a {@link LocalVCRepositoryUri}. This either retrieves the SSH URI, if SSH is used, the HTTP(S) URI, or the path to the repository's folder if the local VCS
     * is
     * used.
     * This method is for internal use (getting the URI for cloning the repository into the Artemis file system).
     * For the local VCS however, the repository is cloned from the folder defined in the environment variable "artemis.version-control.local-vcs-repo-path".
     *
     * @param vcsRepositoryUri the {@link LocalVCRepositoryUri} for which to get the URI
     * @return the URI (SSH, HTTP(S), or local path)
     */
    @Override
    protected URI getGitUri(@NonNull LocalVCRepositoryUri vcsRepositoryUri) {
        return vcsRepositoryUri.getLocalRepositoryPath(localVCBasePath).toUri();
    }

    /**
     * Get the local repository for a given participation. If the local repo does not exist yet, it will be checked out.
     * Saves the local repo in the default path.
     *
     * @param participation Participation the remote repository belongs to.
     * @param writeAccess   whether we want to write to the repository
     * @return the repository if it could be checked out
     * @throws GitAPIException if the repository could not be checked out.
     */
    public Repository getOrCheckoutRepository(ProgrammingExerciseParticipation participation, boolean writeAccess) throws GitAPIException {
        return getOrCheckoutRepository(participation, repoClonePath, writeAccess);
    }

    /**
     * Get the local repository for a given participation. If the local repo does not exist yet, it will be checked out.
     * Saves the local repo in the default path.
     *
     * @param participation Participation the remote repository belongs to.
     * @param targetPath    path where the repo is located on disk
     * @param writeAccess   whether we want to write to the repository
     * @return the repository if it could be checked out
     * @throws GitAPIException if the repository could not be checked out.
     * @throws GitException    if the same repository is attempted to be cloned multiple times.
     */
    public Repository getOrCheckoutRepository(ProgrammingExerciseParticipation participation, Path targetPath, boolean writeAccess) throws GitAPIException, GitException {
        var repoUri = participation.getVcsRepositoryUri();
        Repository repository = getOrCheckoutRepositoryWithTargetPath(repoUri, targetPath, true, writeAccess);
        repository.setParticipation(participation);
        return repository;
    }

    /**
     * Get the local repository for a given participation.
     * If the local repo does not exist yet, it will be checked out.
     * <p>
     * This method will include the participation ID in the local path of the repository so
     * JPlag can refer back to the correct participation.
     *
     * @param participation Participation the remote repository belongs to.
     * @param targetPath    path where the repo is located on disk
     * @return the repository if it could be checked out
     * @throws GitAPIException      if the repository could not be checked out.
     * @throws InvalidPathException if the repository could not be checked out Because it contains unmappable characters.
     */
    public Repository getOrCheckoutRepositoryForJPlag(@NonNull ProgrammingExerciseParticipation participation, Path targetPath) throws GitAPIException, InvalidPathException {
        var repoUri = participation.getVcsRepositoryUri();
        String repoFolderName = repoUri.folderNameForRepositoryUri();

        // Replace the exercise name in the repository folder name with the participation ID.
        // This is necessary to be able to refer back to the correct participation after the JPlag detection run.
        String updatedRepoFolderName = EXERCISE_NAME_IN_FOLDER_NAME.matcher(repoFolderName).replaceAll("/" + participation.getId() + "-");
        // the repo-folder name might start with a separator, e.g. "/studentOriginRepo1234567890 which is treated as absolute path which is wrong
        if (updatedRepoFolderName.startsWith(FileSystems.getDefault().getSeparator())) {
            updatedRepoFolderName = updatedRepoFolderName.substring(1);
        }
        Path localPath = targetPath.resolve(updatedRepoFolderName);

        Repository repository = getOrCheckoutRepositoryWithLocalPath(repoUri, localPath, true, false);
        repository.setParticipation(participation);

        return repository;
    }

    /**
     * Get the local repository for a given remote repository URI. If the local repo does not exist yet, it will be checked out.
     * Saves the repo in the default path
     *
     * @param repoUri     The remote repository.
     * @param pullOnGet   Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @param writeAccess Whether we want to write to the repository
     * @return the repository if it could be checked out.
     * @throws GitAPIException if the repository could not be checked out.
     */
    public Repository getOrCheckoutRepository(LocalVCRepositoryUri repoUri, boolean pullOnGet, boolean writeAccess) throws GitAPIException {
        return getOrCheckoutRepositoryWithTargetPath(repoUri, repoClonePath, pullOnGet, writeAccess);
    }

    /**
     * Get the local repository for a given remote repository URI. If the local repo does not exist yet, it will be checked out.
     *
     * @param repoUri     The remote repository.
     * @param targetPath  path where the repo is located on disk
     * @param pullOnGet   Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @param writeAccess whether we want to write to the repository
     * @return the repository if it could be checked out.
     * @throws GitAPIException if the repository could not be checked out.
     * @throws GitException    if the same repository is attempted to be cloned multiple times.
     */
    public Repository getOrCheckoutRepositoryWithTargetPath(LocalVCRepositoryUri repoUri, Path targetPath, boolean pullOnGet, boolean writeAccess)
            throws GitAPIException, GitException {
        Path localPath = getLocalPathOfRepo(targetPath, repoUri);
        return getOrCheckoutRepositoryWithLocalPath(repoUri, localPath, pullOnGet, writeAccess);
    }

    /**
     * Get the local repository for a given remote repository URI. If the local repo does not exist yet, it will be checked out.
     *
     * @param repoUri       The remote repository.
     * @param pullOnGet     Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @param defaultBranch The default branch of the target repository.
     * @param writeAccess   whether we want to write to the repository
     * @return the repository if it could be checked out.
     * @throws GitAPIException if the repository could not be checked out.
     * @throws GitException    if the same repository is attempted to be cloned multiple times.
     */
    public Repository getOrCheckoutRepository(LocalVCRepositoryUri repoUri, boolean pullOnGet, String defaultBranch, boolean writeAccess) throws GitAPIException, GitException {
        Path localPath = getLocalPathOfRepo(repoClonePath, repoUri);
        return getOrCheckoutRepository(repoUri, repoUri, localPath, pullOnGet, defaultBranch, writeAccess);
    }

    /**
     * Get the local repository for a given remote repository URI. If the local repo does not exist yet, it will be checked out.
     *
     * @param repoUri     The source and target remote repository.
     * @param localPath   The local path to clone the repository to.
     * @param pullOnGet   Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @param writeAccess whether we want to write to the repository
     * @return the repository if it could be checked out.
     * @throws GitAPIException      if the repository could not be checked out.
     * @throws GitException         if the same repository is attempted to be cloned multiple times.
     * @throws InvalidPathException if the repository could not be checked out Because it contains unmappable characters.
     */
    public Repository getOrCheckoutRepositoryWithLocalPath(LocalVCRepositoryUri repoUri, Path localPath, boolean pullOnGet, boolean writeAccess)
            throws GitAPIException, GitException, InvalidPathException {
        return getOrCheckoutRepository(repoUri, repoUri, localPath, pullOnGet, defaultBranch, writeAccess);
    }

    /**
     * Get the local repository for a given remote repository URI. If the local repo does not exist yet, it will be checked out.
     *
     * @param sourceRepoUri The source remote repository.
     * @param targetRepoUri The target remote repository.
     * @param localPath     The local path to clone the repository to.
     * @param pullOnGet     Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @param defaultBranch The default branch of the target repository
     * @param writeAccess   whether we want to write to the repository
     * @return the repository if it could be checked out.
     * @throws GitAPIException      if the repository could not be checked out.
     * @throws GitException         if the same repository is attempted to be cloned multiple times.
     * @throws InvalidPathException if the repository could not be checked out Because it contains unmappable characters.
     */
    public Repository getOrCheckoutRepository(LocalVCRepositoryUri sourceRepoUri, LocalVCRepositoryUri targetRepoUri, Path localPath, boolean pullOnGet, String defaultBranch,
            boolean writeAccess) throws GitAPIException, GitException, InvalidPathException {
        return checkoutRepository(sourceRepoUri, targetRepoUri, localPath, pullOnGet, defaultBranch, writeAccess, false);
    }

    /**
     * Prepares an authoring checkout whose first clone must use the selected branch, never a fallback to remote HEAD.
     *
     * @param repoUri   the repository to clone
     * @param localPath caller-owned working directory
     * @param branch    selected exercise branch
     * @return the checkout on the requested branch
     * @throws GitAPIException when checkout or pulling fails
     */
    public Repository getOrCheckoutRepositoryOnBranch(LocalVCRepositoryUri repoUri, Path localPath, String branch) throws GitAPIException {
        return checkoutRepository(repoUri, repoUri, localPath, true, branch, false, true);
    }

    private Repository checkoutRepository(LocalVCRepositoryUri sourceRepoUri, LocalVCRepositoryUri targetRepoUri, Path localPath, boolean pullOnGet, String defaultBranch,
            boolean writeAccess, boolean selectCloneBranch) throws GitAPIException {
        // Repository GETs also pull. Serialize preparation of each local working copy, otherwise overlapping reads can
        // race JGit's index lock and the failed-pull cleanup can delete the other reader's checkout.
        try (var ignored = checkoutLocks.acquire(localPath, JGIT_TIMEOUT_IN_SECONDS)) {
            return prepareRepository(sourceRepoUri, targetRepoUri, localPath, pullOnGet, defaultBranch, writeAccess, selectCloneBranch);
        }
    }

    private Repository prepareRepository(LocalVCRepositoryUri sourceRepoUri, LocalVCRepositoryUri targetRepoUri, Path localPath, boolean pullOnGet, String defaultBranch,
            boolean writeAccess, boolean selectCloneBranch) throws GitAPIException {
        // First try to just retrieve the git repository from our server, as it might already be checked out.
        // If the sourceRepoUri differs from the targetRepoUri, we attempt to clone the source repo into the target directory
        deleteIncompleteWorkingCopy(localPath);
        Repository repository = getExistingCheckedOutRepositoryByLocalPath(localPath, targetRepoUri, defaultBranch, writeAccess);

        // Note: in case the actual git repository in the file system is corrupt (e.g. by accident), we will get an exception here
        // the exception will then delete the folder, so that the next attempt would be successful.
        if (repository != null) {
            if (pullOnGet) {
                try {
                    pull(repository);
                }
                catch (JGitInternalException | NoHeadException | TransportException e) {
                    // E.g., LockFailedException
                    // The repository must be closed before deleting its directory: open pack file handles prevent the
                    // deletion on network file systems (NFS), which would leave a corrupt working copy behind that breaks
                    // every subsequent access. Same pattern as AbstractGitService.deleteLocalRepository.
                    repository.closeBeforeDelete();
                    // cleanup the folder to avoid problems in the future.
                    // 'deleteQuietly' is the same as 'deleteDirectory' but is not throwing an exception, thus we avoid another try-catch block.
                    // 'deleteQuietly' also returns false when there was nothing to delete, so only report an error if the directory is actually left behind.
                    if (!FileUtils.deleteQuietly(localPath.toFile()) && Files.exists(localPath)) {
                        log.error("Could not delete directory after failed pull: {}", localPath.toAbsolutePath());
                    }
                    throw new GitException(e);
                }
            }
            return repository;
        }
        // If the git repository can't be found on our server, clone it from the remote.
        else {
            // Clone repository.
            try {
                var gitUriAsString = getGitUriAsString(sourceRepoUri);
                log.debug("Cloning from {} to {}", gitUriAsString, localPath);
                // make sure the directory to copy into is empty
                FileUtils.deleteDirectory(localPath.toFile());
                CloneCommand clone = cloneCommand().setURI(gitUriAsString).setDirectory(localPath.toFile());
                if (selectCloneBranch) {
                    clone.setBranch(defaultBranch);
                }
                try (Git ignored = clone.call()) {
                    // Git instance automatically closed by try-with-resources
                }
            }
            catch (IOException | URISyntaxException | GitAPIException | InvalidPathException e) {
                // cleanup the folder to avoid problems in the future.
                // 'deleteQuietly' is the same as 'deleteDirectory' but is not throwing an exception, thus we avoid another try-catch block.
                // 'deleteQuietly' also returns false when there was nothing to delete (JGit's CloneCommand already cleans up
                // its own directory on failure), so only report an error if the directory is actually left behind.
                if (!FileUtils.deleteQuietly(localPath.toFile()) && Files.exists(localPath)) {
                    log.error("Could not delete directory after failed clone: {}", localPath.toAbsolutePath());
                }
                throw new GitException(e);
            }
            return getExistingCheckedOutRepositoryByLocalPath(localPath, targetRepoUri, defaultBranch, writeAccess);
        }
    }

    public Path getDefaultLocalCheckOutPathOfRepo(LocalVCRepositoryUri targetUrl) {
        return getLocalPathOfRepo(repoClonePath, targetUrl);
    }

    /**
     * Creates a local path by specifying a target path and the target url, this is typically used to resolve the check out path for local repositories
     *
     * @param targetPath target directory
     * @param targetUrl  url of the repository
     * @return path of the local file system
     * @throws EntityNotFoundException if targetUrl is null, i.e. the repository does not exist (e.g. because it was never created for the participation)
     */
    public Path getLocalPathOfRepo(Path targetPath, LocalVCRepositoryUri targetUrl) {
        if (targetUrl == null) {
            throw new EntityNotFoundException("Cannot resolve the local repository path because the repository URI is null. The repository most likely does not exist.");
        }
        Path resolvedPath = (targetPath.normalize()).resolve(targetUrl.folderNameForRepositoryUri()).normalize();
        if (!resolvedPath.startsWith(targetPath.normalize())) {
            throw new IllegalArgumentException("Invalid path: " + resolvedPath);
        }
        return resolvedPath;
    }

    /**
     * Deletes a working copy whose git directory has no HEAD or no config, so that it is cloned again. A clone always writes both, but a deletion that failed half-way
     * (e.g. on a network file system, where files that are still open cannot be removed) can leave such a directory behind. It still opens as a repository, yet every
     * git operation on it fails, e.g. with "Cannot check out from unborn branch".
     *
     * @param localPath the path of the working copy
     */
    private void deleteIncompleteWorkingCopy(Path localPath) {
        if (cloneInProgressOperations.containsKey(localPath)) {
            // A clone in progress writes HEAD and config last, so its git directory looks incomplete until it is done
            return;
        }
        Path gitDirectory = localPath.resolve(".git");
        if (Files.isDirectory(gitDirectory) && (Files.notExists(gitDirectory.resolve(Constants.HEAD)) || Files.notExists(gitDirectory.resolve(Constants.CONFIG)))) {
            log.warn("Deleting the incomplete working copy {} so that it is cloned again", localPath);
            if (!FileUtils.deleteQuietly(localPath.toFile()) && Files.exists(localPath)) {
                log.error("Could not delete the incomplete working copy {}", localPath.toAbsolutePath());
            }
        }
    }

    /**
     * Get an existing git repository that is checked out on the server. Returns immediately null if the localPath does not exist.
     *
     * @param localPath           to git repo on server.
     * @param remoteRepositoryUri the remote repository uri for the git repository, will be added to the Repository object for later use, can be null
     * @return the git repository in the localPath or **null** if it does not exist on the server.
     */
    @Nullable
    public Repository getExistingCheckedOutRepositoryByLocalPath(@NonNull Path localPath, LocalVCRepositoryUri remoteRepositoryUri) {
        return getExistingCheckedOutRepositoryByLocalPath(localPath, remoteRepositoryUri, defaultBranch, false);
    }

    /**
     * Get an existing git repository that is checked out on the server. Returns immediately null if the localPath does not exist.
     *
     * @param localPath           to git repo on server.
     * @param remoteRepositoryUri the remote repository uri for the git repository, will be added to the Repository object for later use, can be null
     * @param defaultBranch       the name of the branch that should be used as default branch
     * @param writeAccess         whether the repository should be opened with write access
     * @return the git repository in the localPath or **null** if it does not exist on the server.
     */
    @Nullable
    public Repository getExistingCheckedOutRepositoryByLocalPath(@NonNull Path localPath, LocalVCRepositoryUri remoteRepositoryUri, String defaultBranch, boolean writeAccess) {
        try {
            if (!Files.exists(localPath)) {
                return null;
            }
            // Try to retrieve the git repository from our server.
            return linkRepositoryForExistingGit(localPath, remoteRepositoryUri, defaultBranch, false, writeAccess);
        }
        catch (IOException | InvalidRefNameException ex) {
            log.warn("Cannot get existing checkout out repository by local path: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Commits with the given message into the repository.
     *
     * @param repo    Local Repository Object.
     * @param message Commit Message
     * @throws GitAPIException if the commit failed.
     */
    public void commit(Repository repo, String message) throws GitAPIException {
        try (Git git = new Git(repo)) {
            GitService.commit(git).setMessage(message).setAllowEmpty(true).setCommitter(artemisGitName, artemisGitEmail).call();
        }
    }

    /**
     * Creates a CommitCommand and sets signing to false. Egit uses the local git configuration and if signing of
     * commits is enabled, tests will fail because it will not be able to actually sign the commit.
     * This method makes sure that signing is disabled and commits work on systems regardless of the local git configuration.
     *
     * @param git Git Repository Object.
     * @return CommitCommand with signing set to false.
     */
    public static CommitCommand commit(Git git) {
        return git.commit().setSign(false);
    }

    /**
     * Commits with the given message into the repository and pushes it to the remote.
     *
     * @param repo        Local Repository Object.
     * @param message     Commit Message
     * @param emptyCommit whether the git service should also produce an empty commit
     * @param user        The user who should initiate the commit. If the user is null, the artemis user will be used
     * @return the id of the commit that was created
     * @throws GitAPIException if the commit failed.
     */
    public String commitAndPush(Repository repo, String message, boolean emptyCommit, @Nullable User user) throws GitAPIException {
        String name = user != null ? user.getName() : artemisGitName;
        String email = user != null ? user.getEmail() : artemisGitEmail;
        try (Git git = new Git(repo)) {
            RevCommit commit = GitService.commit(git).setMessage(message).setAllowEmpty(emptyCommit).setCommitter(name, email).call();
            log.debug("commitAndPush -> Push {}", repo.getLocalPath());
            setRemoteUrl(repo);
            pushCommand(git).call();
            // Returned rather than discarded so callers can identify the commit they just created. Reading the repository head
            // afterwards is not equivalent: the online editor shares one working copy per repository, so a concurrent commit
            // to the same repository can move the head between the commit and the read.
            return commit.getName();
        }
    }

    /**
     * Creates a commit from the staged changes without pushing it.
     *
     * @param repo    the repository containing the staged changes
     * @param message the commit message
     * @param user    the commit author, or {@code null} for the Artemis service user
     * @return the created commit hash
     * @throws GitAPIException if the commit fails
     */
    public String commitStagedChanges(Repository repo, String message, @Nullable User user) throws GitAPIException {
        String name = user != null ? user.getName() : artemisGitName;
        String email = user != null ? user.getEmail() : artemisGitEmail;
        try (Git git = new Git(repo)) {
            RevCommit commit = GitService.commit(git).setMessage(message).setCommitter(name, email).call();
            return commit.getName();
        }
    }

    /**
     * Pushes {@code commitHash} only if {@code branch} still points at {@code expectedRemoteHead}.
     *
     * @param repo               the repository containing the commit
     * @param commitHash         the commit to push
     * @param branch             the target branch
     * @param expectedRemoteHead the required current remote head
     * @throws GitAPIException if the push fails or the lease is rejected
     */
    public void pushCommitWithLease(Repository repo, String commitHash, String branch, String expectedRemoteHead) throws GitAPIException {
        if (StringUtils.isBlank(expectedRemoteHead)) {
            throw new TransportException("Refusing to push " + branch + " without an expected remote HEAD lease");
        }
        try (Git git = new Git(repo)) {
            setRemoteUrl(repo);
            String remoteRef = "refs/heads/" + branch;
            Iterable<PushResult> pushResults = pushCommand(git).setRefSpecs(new RefSpec(commitHash + ":" + remoteRef))
                    .setRefLeaseSpecs(new RefLeaseSpec(remoteRef, expectedRemoteHead)).call();
            assertPushAccepted(pushResults, remoteRef);
        }
    }

    /**
     * Hard-resets the working copy to a previous commit hash and force-pushes that state onto the given branch only if the remote branch still points at the expected current
     * commit. This is the compensation primitive for Hyperion's multi-repository persist and adaptation revert paths.
     * <p>
     * A force push is required because moving a branch back to an ancestor commit is a non-fast-forward update. The ref lease is the repository-level compare-and-swap guard: if
     * another editor pushed to the branch after the caller captured {@code expectedCurrentHash}, the remote rejects the update instead of clobbering that work.
     *
     * @param repo                the local repository whose default branch is reverted
     * @param commitHash          the pre-persist/pre-adaptation commit hash to reset the branch back to
     * @param expectedCurrentHash the commit hash the remote branch must still point at before the force push is accepted
     * @param branch              the short name of the branch to force-push (the default branch)
     * @throws GitAPIException if resetting or force-pushing fails
     */
    public void resetToCommitAndForcePush(Repository repo, String commitHash, String expectedCurrentHash, String branch) throws GitAPIException {
        if (StringUtils.isBlank(expectedCurrentHash)) {
            throw new TransportException("Refusing to force-push " + branch + " without an expected remote HEAD lease");
        }
        boolean pushed = false;
        try (Git git = new Git(repo)) {
            setRemoteUrl(repo);
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(commitHash).call();
            String remoteRef = "refs/heads/" + branch;
            log.debug("resetToCommitAndForcePush -> Force-push {} back to {} on branch {} if remote still is {}", repo.getLocalPath(), commitHash, branch, expectedCurrentHash);
            Iterable<PushResult> pushResults = pushCommand(git).setForce(true).setRefSpecs(new RefSpec("HEAD:" + remoteRef))
                    .setRefLeaseSpecs(new RefLeaseSpec(remoteRef, expectedCurrentHash)).call();
            assertPushAccepted(pushResults, remoteRef);
            pushed = true;
        }
        finally {
            if (!pushed) {
                resetToOriginHead(repo);
            }
        }
    }

    private static void assertPushAccepted(Iterable<PushResult> pushResults, String remoteRef) throws TransportException {
        boolean foundTargetRef = false;
        for (PushResult pushResult : pushResults) {
            for (RemoteRefUpdate update : pushResult.getRemoteUpdates()) {
                if (!remoteRef.equals(update.getRemoteName())) {
                    continue;
                }
                foundTargetRef = true;
                if (update.getStatus() != RemoteRefUpdate.Status.OK && update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
                    throw new TransportException("Leased push of " + remoteRef + " was rejected with status " + update.getStatus() + ": " + update.getMessage());
                }
            }
        }
        if (!foundTargetRef) {
            throw new TransportException("Push result did not contain an update for " + remoteRef);
        }
    }

    /**
     * Stages all changes in the repository, including new, modified, and deleted files.
     *
     * <p>
     * The method first updates the index for deleted files (using {@code setUpdate(true)})
     * and then stages new or modified files. It returns {@code true} if any files were staged
     * (i.e., if there were changes in the working tree that were added to the index),
     * or {@code false} if the repository was already clean.
     *
     * <p>
     * This operation is safe for both normal and bare repositories.
     *
     * @param repo the local {@link Repository} to stage changes in.
     * @return {@code true} if any changes were staged, {@code false} if nothing changed.
     * @throws GitAPIException if staging fails.
     */
    public boolean stageAllChanges(Repository repo) throws GitAPIException {
        try (Git git = new Git(repo)) {
            // Check repository status before staging
            Status preStatus = git.status().call();
            boolean hasChanges = !preStatus.isClean();

            if (hasChanges) {
                // Stage deleted files
                git.add().setUpdate(true).addFilepattern(".").call();

                // Stage new and modified files
                git.add().addFilepattern(".").call();
            }

            // Return true only if there were any changes to stage
            return hasChanges;
        }
    }

    /**
     * Resets local repository to ref.
     *
     * @param repo Local Repository Object.
     * @param ref  the ref to reset to, e.g. "origin/main"
     * @throws GitAPIException if the reset failed.
     */
    public void reset(Repository repo, String ref) throws GitAPIException {
        try (Git git = new Git(repo)) {
            setRemoteUrl(repo);
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(ref).call();
        }
    }

    /**
     * git fetch
     *
     * @param repo Local Repository Object.
     * @throws GitAPIException if the fetch failed.
     */
    public void fetchAll(Repository repo) throws GitAPIException {
        try (Git git = new Git(repo)) {
            log.debug("Fetch {}", repo.getLocalPath());
            setRemoteUrl(repo);
            fetchCommand(git).setForceUpdate(true).setRemoveDeletedRefs(true).call();
        }
    }

    /**
     * Updates the remote URL of a repository to match the currently configured authentication mechanism.
     * <p>
     * This method ensures that the "origin" remote URL is synchronized with the repository's
     * configured remote URI. This is necessary because the authentication mechanism (SSH vs HTTPS)
     * may change during runtime, and the local repository configuration needs to reflect this change
     * for subsequent git operations (fetch, pull, push) to work correctly.
     * </p>
     *
     * @param repo the git repository for which the remote URL should be updated; if null or without
     *                 a remote URI configured, the method returns without making changes
     */
    public void setRemoteUrl(Repository repo) {
        if (repo == null || repo.getRemoteRepositoryUri() == null) {
            log.warn("Cannot set remoteUrl because it is null!");
            return;
        }
        try {
            var existingRemoteUrl = repo.getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, REMOTE_NAME, "url");
            var newRemoteUrl = getGitUriAsString(repo.getRemoteRepositoryUri());
            // Only update if the URL has actually changed (e.g., switched from HTTPS to SSH or vice versa)
            if (!Objects.equals(newRemoteUrl, existingRemoteUrl)) {
                log.info("Replace existing remote url {} with new remote url {}", existingRemoteUrl, newRemoteUrl);
                repo.getConfig().setString(ConfigConstants.CONFIG_REMOTE_SECTION, REMOTE_NAME, "url", newRemoteUrl);
                log.info("New remote url: {}", repo.getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, REMOTE_NAME, "url"));
            }
        }
        catch (Exception e) {
            log.warn("Cannot set the remote url", e);
        }
    }

    /**
     * Pulls from remote repository. Does not throw any exceptions when pulling, e.g. CheckoutConflictException or WrongRepositoryStateException.
     *
     * @param repo Local Repository Object.
     */
    public void pullIgnoreConflicts(Repository repo) {
        try (Git git = new Git(repo)) {
            log.debug("Pull ignore conflicts {}", repo.getLocalPath());
            setRemoteUrl(repo);
            pullCommand(git).call();
        }
        catch (GitAPIException ex) {
            log.error("Cannot pull the repo {}", repo.getLocalPath(), ex);
            // TODO: we should send this error to the client and let the user handle it there, e.g. by choosing to reset the repository
        }
    }

    /**
     * Pulls from remote repository.
     *
     * @param repo Local Repository Object.
     * @throws GitAPIException if the pull failed.
     */
    private void pull(Repository repo) throws GitAPIException {
        try (Git git = new Git(repo)) {
            log.info("Pull {}", repo.getLocalPath());
            setRemoteUrl(repo);
            pullCommand(git).call();
        }
    }

    /**
     * Get branch that origin/HEAD points to, useful to handle default branches that are not main
     *
     * @param repo Local Repository Object.
     * @return name of the origin/HEAD branch, e.g. 'main' or null if there is no HEAD
     */
    private String getOriginHead(Repository repo) throws GitAPIException {
        Ref originHeadRef;
        try (Git git = new Git(repo)) {
            originHeadRef = lsRemoteCommand(git).callAsMap().get(Constants.HEAD);
        }

        // Empty Git repos don't have HEAD
        if (originHeadRef == null) {
            return null;
        }

        String fullName = originHeadRef.getTarget().getName();
        return StringUtils.substringAfterLast(fullName, "/");
    }

    /**
     * Hard reset local repository to origin/HEAD.
     *
     * @param repo Local Repository Object.
     */
    public void resetToOriginHead(Repository repo) {
        try {
            fetchAll(repo);
            var originHead = getOriginHead(repo);

            if (originHead == null) {
                log.error("Cannot hard reset the repo {} to origin/HEAD because it is empty.", repo.getLocalPath());
                return;
            }

            reset(repo, "origin/" + originHead);
        }
        catch (GitAPIException | JGitInternalException ex) {
            log.error("Cannot fetch/hard reset the repo {} with url {} to origin/HEAD due to the following exception", repo.getLocalPath(), repo.getRemoteRepositoryUri(), ex);
        }
    }

    /**
     * Removes all remote configurations and their associated tracking branches from a Git repository.
     * <p>
     * This method performs a complete cleanup of remote-related data by:
     * <ol>
     * <li>Removing each remote configuration (e.g., "origin") from the repository</li>
     * <li>Deleting all remote tracking branches (refs under "refs/remotes/") for each remote</li>
     * </ol>
     * This is particularly useful when anonymizing repositories for export, as remote URLs
     * may contain sensitive information about the repository's origin or student identity.
     * </p>
     * <p>
     * Note: JGit's {@code remoteRemove()} does not automatically clean up remote tracking branches,
     * so this method manually deletes them to ensure complete removal.
     * </p>
     *
     * @param repository the JGit Git instance wrapping the repository to clean
     * @throws IOException     if an I/O error occurs when accessing the ref database
     * @throws GitAPIException if a JGit operation fails while listing or removing remotes
     */
    public void removeRemotes(Git repository) throws IOException, GitAPIException {
        for (RemoteConfig remote : repository.remoteList().call()) {
            // Remove the remote configuration (e.g., [remote "origin"] section in .git/config)
            repository.remoteRemove().setRemoteName(remote.getName()).call();

            // JGit does not automatically delete remote tracking branches when removing a remote,
            // so we must manually find and delete all refs under refs/remotes/<remote-name>/
            for (Ref ref : repository.getRepository().getRefDatabase().getRefs()) {
                if (ref.getName().startsWith("refs/remotes/" + remote.getName())) {
                    RefUpdate update = repository.getRepository().updateRef(ref.getName());
                    update.setForceUpdate(true);
                    update.delete();
                }
            }
        }
    }

    /**
     * Convenience method that removes all remotes from a repository and handles resource cleanup.
     * <p>
     * This is a wrapper around {@link #removeRemotes(Git)} that:
     * <ul>
     * <li>Creates a Git instance from the repository</li>
     * <li>Removes all remotes and their tracking branches</li>
     * <li>Handles exceptions gracefully (logs warnings instead of throwing)</li>
     * <li>Ensures the repository is properly closed afterward</li>
     * </ul>
     * </p>
     *
     * @param repository the repository from which to remove all remotes
     * @see #removeRemotes(Git)
     */
    public void removeRemotesFromRepository(Repository repository) {
        try (Git gitRepo = new Git(repository)) {
            removeRemotes(gitRepo);
        }
        catch (EntityNotFoundException | GitAPIException | JGitInternalException | IOException ex) {
            log.warn("Cannot remove the remotes of the repo {} due to the following exception: {}", repository.getLocalPath(), ex.getMessage());
        }
        finally {
            repository.close();
        }
    }

    /**
     * Returns all files and directories within the working copy of the given repository in a map, excluding symbolic links.
     * This method performs a file scan and filters out symbolic links.
     * It only supports checked-out repositories (not bare ones)
     *
     * @param repo         The repository to scan for files and directories.
     * @param omitBinaries do not include binaries to reduce payload size
     * @return A {@link Map} where each key is a {@link File} object representing a file or directory, and each value is
     *         the corresponding {@link FileType} (FILE or FOLDER). The map excludes symbolic links.
     */
    public Map<File, FileType> listFilesAndFolders(Repository repo, boolean omitBinaries) {
        Map<File, FileType> files = new HashMap<>();
        Path workingTree = repo.getLocalPath();
        if (workingTree == null) {
            log.warn("Working tree path of repository {} is not available", repo.getRemoteRepositoryUri());
            return files;
        }
        if (!Files.exists(workingTree)) {
            log.warn("Working tree {} does not exist for repository {}", workingTree, repo.getRemoteRepositoryUri());
            return files;
        }

        try {
            Files.walkFileTree(workingTree, new SimpleFileVisitor<>() {

                @Override
                public @NonNull FileVisitResult preVisitDirectory(@NonNull Path dir, @NonNull BasicFileAttributes attrs) {
                    if (dir.equals(workingTree)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (".git".equals(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (Files.isSymbolicLink(dir)) {
                        log.warn("Found a symlink {} in the git repository {}. Do not allow access!", dir, repo);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    files.put(new File(dir.toFile(), repo), FileType.FOLDER);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NonNull FileVisitResult visitFile(@NonNull Path path, @NonNull BasicFileAttributes attrs) {
                    if (Files.isSymbolicLink(path)) {
                        log.warn("Found a symlink {} in the git repository {}. Do not allow access!", path, repo);
                        return FileVisitResult.CONTINUE;
                    }
                    if (omitBinaries && isBinaryFile(path.getFileName().toString())) {
                        log.debug("Omitting binary file: {}", path);
                        return FileVisitResult.CONTINUE;
                    }
                    files.put(new File(path.toFile(), repo), FileType.FILE);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException exception) {
            log.error("Failed to list files for repository {}: {}", repo.getRemoteRepositoryUri(), exception.getMessage());
        }

        return files;
    }

    public Map<File, FileType> listFilesAndFolders(Repository repo) {
        return listFilesAndFolders(repo, false);
    }

    /**
     * List all files in the repository. In an empty git repo, this method returns en empty list.
     *
     * @param repo Local Repository Object.
     * @return Collection of File objects
     */
    @NonNull
    public Collection<File> getFiles(Repository repo) {
        return listFilesAndFolders(repo, false).entrySet().stream().filter(entry -> entry.getValue() == FileType.FILE).map(Map.Entry::getKey).toList();
    }

    /**
     * Get a specific file by name. Makes sure the file is actually part of the repository.
     *
     * @param repo     Local Repository Object.
     * @param filename String of the filename (including path)
     * @return The File object
     */
    public Optional<File> getFileByName(Repository repo, String filename) {
        // Makes sure the requested file is part of the scanned list of files.
        // Ensures that it is not possible to do bad things like filename="../../passwd"

        for (File file : listFilesAndFolders(repo).keySet()) {
            if (file.toString().equals(filename)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if no differences exist between the working-tree, the index, and the current HEAD.
     *
     * @param repo Local Repository Object.
     * @return True if the status is clean
     * @throws GitAPIException if the state of the repository could not be retrieved.
     */
    public boolean isWorkingCopyClean(Repository repo) throws GitAPIException {
        try (Git git = new Git(repo)) {
            Status status = git.status().call();
            return status.isClean();
        }
    }

    /**
     * Deletes a local repository folder for a repoUri.
     *
     * @param repoUri url of the repository.
     */
    public void deleteLocalRepository(LocalVCRepositoryUri repoUri) {
        try {
            if (repoUri != null && checkedOutRepositoryAlreadyExists(repoUri)) {
                // We need to close the possibly still open repository otherwise an IOException will be thrown on Windows
                Path localPath = getLocalPathOfRepo(repoClonePath, repoUri);
                Repository repo = getExistingCheckedOutRepositoryByLocalPath(localPath, repoUri);
                if (repo != null) {
                    deleteLocalRepository(repo);
                }
            }
        }
        catch (IOException e) {
            log.error("Error while deleting local repository", e);
        }
    }

    /**
     * delete the folder in the file system that contains all repositories for the given programming exercise
     *
     * @param programmingExercise contains the project key which is used as the folder name
     */
    public void deleteLocalProgrammingExerciseReposFolder(ProgrammingExercise programmingExercise) {
        var folderPath = repoClonePath.resolve(programmingExercise.getProjectKey());
        try {
            FileUtils.deleteDirectory(folderPath.toFile());
        }
        catch (IOException ex) {
            log.error("Exception during deleteLocalProgrammingExerciseReposFolder", ex);
            // cleanup the folder to avoid problems in the future.
            // 'deleteQuietly' is the same as 'deleteDirectory' but is not throwing an exception, thus we avoid a try-catch block.
            FileUtils.deleteQuietly(folderPath.toFile());
        }
    }

    /**
     * Checks if repo was already checked out and is present on disk
     *
     * @param repoUri URL of the remote repository.
     * @return True if repo exists on disk
     */
    private boolean checkedOutRepositoryAlreadyExists(LocalVCRepositoryUri repoUri) {
        Path localCheckedOutRepoPath = getDefaultLocalCheckOutPathOfRepo(repoUri);
        return Files.exists(localCheckedOutRepoPath);
    }

    private PullCommand pullCommand(Git git) {
        return git.pull();
    }

    private PushCommand pushCommand(Git git) {
        return git.push();
    }

    private FetchCommand fetchCommand(Git git) {
        return git.fetch();
    }

    protected LsRemoteCommand lsRemoteCommand(Git git) {
        return git.lsRemote();
    }

    @Override
    protected LsRemoteCommand lsRemoteCommand() {
        return Git.lsRemoteRepository();
    }

    protected CloneCommand cloneCommand() {
        return Git.cloneRepository();
    }
}
