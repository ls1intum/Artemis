package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.BinaryFileExtensionConfiguration.isBinaryFile;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.FullStartupEvent;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.GitException;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.File;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.dto.CommitInfoDTO;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class GitService extends AbstractGitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    private final ProfileService profileService;

    @Value("${artemis.version-control.local-vcs-repo-path:#{null}}")
    private Path localVCBasePath;

    @Value("${artemis.repo-clone-path}")
    private Path repoClonePath;

    @Value("${artemis.git.name}")
    private String artemisGitName;

    @Value("${artemis.git.email}")
    private String artemisGitEmail;

    @Value("${artemis.version-control.user}")
    protected String gitUser;

    @Value("${artemis.version-control.password}")
    protected String gitPassword;

    private final Map<Path, Path> cloneInProgressOperations = new ConcurrentHashMap<>();

    private final ZipFileService zipFileService;

    private static final String ANONYMIZED_STUDENT_NAME = "student";

    private static final String ANONYMIZED_STUDENT_EMAIL = "";

    public GitService(ProfileService profileService, ZipFileService zipFileService) {
        super();
        this.profileService = profileService;
        this.zipFileService = zipFileService;
    }

    /**
     * initialize the GitService, in particular which authentication mechanism should be used
     * Artemis uses the following order for authentication:
     * 1. ssh key (if available)
     * 2. username + personal access token (if available)
     * 3. username + password
     */
    @EventListener(FullStartupEvent.class)
    public void init() {
        if (useSsh()) {
            log.info("GitService will use ssh keys as authentication method to interact with remote git repositories");
            configureSsh();
        }
        else if (gitToken.isPresent()) {
            log.info("GitService will use username + token as authentication method to interact with remote git repositories");
            CredentialsProvider.setDefault(new UsernamePasswordCredentialsProvider(gitUser, gitToken.get()));
        }
        else {
            log.info("GitService will use username + password as authentication method to interact with remote git repositories");
            CredentialsProvider.setDefault(new UsernamePasswordCredentialsProvider(gitUser, gitPassword));
        }
    }

    @PreDestroy
    @Override
    public void cleanup() {
        super.cleanup();
    }

    /**
     * Get the URI for a {@link VcsRepositoryUri}. This either retrieves the SSH URI, if SSH is used, the HTTP(S) URI, or the path to the repository's folder if the local VCS is
     * used.
     * This method is for internal use (getting the URI for cloning the repository into the Artemis file system).
     * For the local VCS however, the repository is cloned from the folder defined in the environment variable "artemis.version-control.local-vcs-repo-path".
     *
     * @param vcsRepositoryUri the {@link VcsRepositoryUri} for which to get the URI
     * @return the URI (SSH, HTTP(S), or local path)
     * @throws URISyntaxException if SSH is used and the SSH URI could not be retrieved.
     */
    @Override
    protected URI getGitUri(VcsRepositoryUri vcsRepositoryUri) throws URISyntaxException {
        if (profileService.isLocalVCorCIActive()) {
            // Create less generic LocalVCRepositoryUri out of VcsRepositoryUri.
            LocalVCRepositoryUri localVCRepositoryUri = new LocalVCRepositoryUri(vcsRepositoryUri.toString());
            return localVCRepositoryUri.getLocalRepositoryPath(localVCBasePath).toUri();
        }
        return useSsh() ? getSshUri(vcsRepositoryUri, sshUrlTemplate) : vcsRepositoryUri.getURI();
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
    public Repository getOrCheckoutRepositoryForJPlag(ProgrammingExerciseParticipation participation, Path targetPath) throws GitAPIException, InvalidPathException {
        var repoUri = participation.getVcsRepositoryUri();
        String repoFolderName = repoUri.folderNameForRepositoryUri();

        // Replace the exercise name in the repository folder name with the participation ID.
        // This is necessary to be able to refer back to the correct participation after the JPlag detection run.
        String updatedRepoFolderName = repoFolderName.replaceAll("/[a-zA-Z0-9]*-", "/" + participation.getId() + "-");
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
    public Repository getOrCheckoutRepository(VcsRepositoryUri repoUri, boolean pullOnGet, boolean writeAccess) throws GitAPIException {
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
    public Repository getOrCheckoutRepositoryWithTargetPath(VcsRepositoryUri repoUri, Path targetPath, boolean pullOnGet, boolean writeAccess)
            throws GitAPIException, GitException {
        Path localPath = getLocalPathOfRepo(targetPath, repoUri);
        return getOrCheckoutRepositoryWithLocalPath(repoUri, localPath, pullOnGet, writeAccess);
    }

    /**
     * Checkout at the given repository at the given commit hash
     *
     * @param repository the repository to check out the commit in
     * @param commitHash the hash of the commit to check out
     * @return the repository checked out at the given commit
     */
    public Repository checkoutRepositoryAtCommit(Repository repository, String commitHash) {
        try (Git git = new Git(repository)) {
            git.checkout().setName(commitHash).call();
        }
        catch (GitAPIException e) {
            throw new GitException("Could not checkout commit " + commitHash + " in repository located at  " + repository.getLocalPath(), e);
        }
        return repository;
    }

    /**
     * Get the local repository for a given remote repository URI.
     * <p>
     * If the local repo does not exist yet, it will be checked out.
     * After retrieving the repository, the commit for the given hash will be checked out.
     *
     * @param vcsRepositoryUri the url of the remote repository
     * @param commitHash       the hash of the commit to checkout
     * @param pullOnGet        pull from the remote on the checked out repository, if it does not need to be cloned
     * @return the repository if it could be checked out
     * @throws GitAPIException if the repository could not be checked out
     */
    public Repository checkoutRepositoryAtCommit(VcsRepositoryUri vcsRepositoryUri, String commitHash, boolean pullOnGet) throws GitAPIException {
        var repository = getOrCheckoutRepository(vcsRepositoryUri, pullOnGet, false);
        return checkoutRepositoryAtCommit(repository, commitHash);
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
    public Repository getOrCheckoutRepository(VcsRepositoryUri repoUri, boolean pullOnGet, String defaultBranch, boolean writeAccess) throws GitAPIException, GitException {
        Path localPath = getLocalPathOfRepo(repoClonePath, repoUri);
        return getOrCheckoutRepository(repoUri, repoUri, localPath, pullOnGet, defaultBranch, writeAccess);
    }

    public Repository getOrCheckoutRepositoryIntoTargetDirectory(VcsRepositoryUri repoUri, VcsRepositoryUri targetUri, boolean pullOnGet, boolean writeAccess)
            throws GitAPIException, GitException, InvalidPathException {
        Path localPath = getDefaultLocalPathOfRepo(targetUri);
        return getOrCheckoutRepository(repoUri, targetUri, localPath, pullOnGet, writeAccess);
    }

    public Repository getOrCheckoutRepositoryWithLocalPath(VcsRepositoryUri repoUri, Path localPath, boolean pullOnGet, boolean writeAccess)
            throws GitAPIException, GitException, InvalidPathException {
        return getOrCheckoutRepository(repoUri, repoUri, localPath, pullOnGet, writeAccess);
    }

    /**
     * Get the local repository for a given remote repository URI. If the local repo does not exist yet, it will be checked out.
     *
     * @param sourceRepoUri The source remote repository.
     * @param targetRepoUri The target remote repository.
     * @param localPath     The local path to clone the repository to.
     * @param pullOnGet     Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @param writeAccess   Whether we want to write to the repository
     * @return the repository if it could be checked out.
     * @throws GitAPIException      if the repository could not be checked out.
     * @throws GitException         if the same repository is attempted to be cloned multiple times.
     * @throws InvalidPathException if the repository could not be checked out Because it contains unmappable characters.
     */
    private Repository getOrCheckoutRepository(VcsRepositoryUri sourceRepoUri, VcsRepositoryUri targetRepoUri, Path localPath, boolean pullOnGet, boolean writeAccess)
            throws GitAPIException, GitException, InvalidPathException {
        return getOrCheckoutRepository(sourceRepoUri, targetRepoUri, localPath, pullOnGet, defaultBranch, writeAccess);
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
    public Repository getOrCheckoutRepository(VcsRepositoryUri sourceRepoUri, VcsRepositoryUri targetRepoUri, Path localPath, boolean pullOnGet, String defaultBranch,
            boolean writeAccess) throws GitAPIException, GitException, InvalidPathException {
        // First try to just retrieve the git repository from our server, as it might already be checked out.
        // If the sourceRepoUri differs from the targetRepoUri, we attempt to clone the source repo into the target directory
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
                    // cleanup the folder to avoid problems in the future.
                    // 'deleteQuietly' is the same as 'deleteDirectory' but is not throwing an exception, thus we avoid another try-catch block.
                    if (!FileUtils.deleteQuietly(localPath.toFile())) {
                        log.error("Could not delete directory after failed pull: {}", localPath.toAbsolutePath());
                    }
                    throw new GitException(e);
                }
            }
            return repository;
        }
        // If the git repository can't be found on our server, clone it from the remote.
        else {
            waitUntilPathNotBusy(localPath);

            // Clone repository.
            try {
                var gitUriAsString = getGitUriAsString(sourceRepoUri);
                log.debug("Cloning from {} to {}", gitUriAsString, localPath);
                cloneInProgressOperations.put(localPath, localPath);
                // make sure the directory to copy into is empty
                FileUtils.deleteDirectory(localPath.toFile());
                Git git = cloneCommand().setURI(gitUriAsString).setDirectory(localPath.toFile()).call();
                git.close();
            }
            catch (IOException | URISyntaxException | GitAPIException | InvalidPathException e) {
                // cleanup the folder to avoid problems in the future.
                // 'deleteQuietly' is the same as 'deleteDirectory' but is not throwing an exception, thus we avoid another try-catch block.
                if (!FileUtils.deleteQuietly(localPath.toFile())) {
                    log.error("Could not delete directory after failed clone: {}", localPath.toAbsolutePath());
                }
                throw new GitException(e);
            }
            finally {
                // make sure that cloneInProgress is released
                cloneInProgressOperations.remove(localPath);
            }
            return getExistingCheckedOutRepositoryByLocalPath(localPath, targetRepoUri, defaultBranch, writeAccess);
        }
    }

    /**
     * Waits until no clone operation is running for the given path.
     * <p>
     * Retries once a second for up to {@link #JGIT_TIMEOUT_IN_SECONDS} seconds before giving up.
     *
     * @param localPath The path in which a clone operation should be made.
     * @throws CanceledException If the waiting has been interrupted.
     * @throws GitException      If the path is still busy after the maximum number of retries.
     */
    private void waitUntilPathNotBusy(final Path localPath) throws CanceledException, GitException {
        int remainingSeconds = JGIT_TIMEOUT_IN_SECONDS;

        while (cloneInProgressOperations.containsKey(localPath)) {
            log.warn("Clone is already in progress. This will lead to an error. Wait for a second");
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException ex) {
                throw new CanceledException("Waiting for local path to be free for cloning got interrupted.");
            }

            if (remainingSeconds <= 0) {
                throw new GitException("Cannot clone the same repository multiple times");
            }
            else {
                remainingSeconds--;
            }
        }
    }

    public Path getDefaultLocalPathOfRepo(VcsRepositoryUri targetUrl) {
        return getLocalPathOfRepo(repoClonePath, targetUrl);
    }

    /**
     * Creates a local path by specifying a target path and the target url
     *
     * @param targetPath target directory
     * @param targetUrl  url of the repository
     * @return path of the local file system
     */
    public Path getLocalPathOfRepo(Path targetPath, VcsRepositoryUri targetUrl) {
        if (targetUrl == null) {
            return null;
        }
        Path resolvedPath = (targetPath.normalize()).resolve(targetUrl.folderNameForRepositoryUri()).normalize();
        if (!resolvedPath.startsWith(targetPath.normalize())) {
            throw new IllegalArgumentException("Invalid path: " + resolvedPath);
        }
        return resolvedPath;
    }

    /**
     * Get an existing git repository that is checked out on the server. Returns immediately null if the localPath does not exist.
     *
     * @param localPath           to git repo on server.
     * @param remoteRepositoryUri the remote repository uri for the git repository, will be added to the Repository object for later use, can be null
     * @return the git repository in the localPath or **null** if it does not exist on the server.
     */
    public Repository getExistingCheckedOutRepositoryByLocalPath(@NotNull Path localPath, @Nullable VcsRepositoryUri remoteRepositoryUri) {
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
    public Repository getExistingCheckedOutRepositoryByLocalPath(@NotNull Path localPath, @Nullable VcsRepositoryUri remoteRepositoryUri, String defaultBranch,
            boolean writeAccess) {
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
     * @throws GitAPIException if the commit failed.
     */
    public void commitAndPush(Repository repo, String message, boolean emptyCommit, @Nullable User user) throws GitAPIException {
        String name = user != null ? user.getName() : artemisGitName;
        String email = user != null ? user.getEmail() : artemisGitEmail;
        try (Git git = new Git(repo)) {
            GitService.commit(git).setMessage(message).setAllowEmpty(emptyCommit).setCommitter(name, email).call();
            log.debug("commitAndPush -> Push {}", repo.getLocalPath());
            setRemoteUrl(repo);
            pushCommand(git).call();
        }
    }

    /**
     * Stage all files in the repo including new files.
     *
     * @param repo Local Repository Object.
     * @throws GitAPIException if the staging failed.
     */
    public void stageAllChanges(Repository repo) throws GitAPIException {
        try (Git git = new Git(repo)) {
            // stage deleted files: http://stackoverflow.com/a/35601677/4013020
            git.add().setUpdate(true).addFilepattern(".").call();
            // stage new files
            git.add().addFilepattern(".").call();
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
     * Change the remote repository uri to the currently used authentication mechanism (either ssh or https)
     *
     * @param repo the git repository for which the remote url should be change
     */
    private void setRemoteUrl(Repository repo) {
        if (repo == null || repo.getRemoteRepositoryUri() == null) {
            log.warn("Cannot set remoteUrl because it is null!");
            return;
        }
        // Note: we reset the remote url, because it might have changed from https to ssh or ssh to https
        try {
            var existingRemoteUrl = repo.getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, REMOTE_NAME, "url");
            var newRemoteUrl = getGitUriAsString(repo.getRemoteRepositoryUri());
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
     * @return The PullResult which contains FetchResult and MergeResult.
     * @throws GitAPIException if the pull failed.
     */
    public PullResult pull(Repository repo) throws GitAPIException {
        try (Git git = new Git(repo)) {
            log.info("Pull {}", repo.getLocalPath());
            setRemoteUrl(repo);
            return pullCommand(git).call();
        }
    }

    /**
     * Get branch that origin/HEAD points to, useful to handle default branches that are not main
     *
     * @param repo Local Repository Object.
     * @return name of the origin/HEAD branch, e.g. 'main' or null if there is no HEAD
     */
    public String getOriginHead(Repository repo) throws GitAPIException {
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
     * Switch back to the HEAD commit of the default branch.
     *
     * @param repository the repository for which we want to switch to the HEAD commit of the default branch
     * @throws GitAPIException if this operation fails
     */
    public void switchBackToDefaultBranchHead(Repository repository) throws GitAPIException {
        try (Git git = new Git(repository)) {
            git.checkout().setName(defaultBranch).call();
        }
    }

    /**
     * Stager Task #3: Filter late submissions Filter all commits after exercise due date
     *
     * @param repository                Local Repository Object.
     * @param lastValidSubmission       The last valid submission from the database or empty, if not found
     * @param filterLateSubmissionsDate the date after which all submissions should be filtered out (may be null)
     */
    public void filterLateSubmissions(Repository repository, Optional<Submission> lastValidSubmission, ZonedDateTime filterLateSubmissionsDate) {
        if (filterLateSubmissionsDate == null) {
            // No date set in client and exercise has no due date
            return;
        }

        try (Git git = new Git(repository)) {
            String commitHash;

            if (lastValidSubmission.isPresent()) {
                log.debug("Last valid submission for participation {} is {}", lastValidSubmission.get().getParticipation().getId(), lastValidSubmission.get());
                ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) lastValidSubmission.get();
                commitHash = programmingSubmission.getCommitHash();
            }
            else {
                log.debug("Last valid submission is not present for participation");
                // Get last commit before due date
                Instant since = Instant.EPOCH;
                Instant until = filterLateSubmissionsDate.toInstant();
                RevFilter between = CommitTimeRevFilter.between(since, until);
                Iterable<RevCommit> commits = git.log().setRevFilter(between).call();
                RevCommit latestCommitBeforeDueDate = commits.iterator().next();
                commitHash = latestCommitBeforeDueDate.getId().getName();
            }
            log.debug("Last commit hash is {}", commitHash);

            reset(repository, commitHash);
        }
        catch (GitAPIException ex) {
            log.warn("Cannot filter the repo {} due to the following exception: {}", repository.getLocalPath(), ex.getMessage());
        }
        finally {
            // if repo is not closed, it causes weird IO issues when trying to delete the repo again
            // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
            repository.close();
        }
    }

    /**
     * Stager Task #6: Combine all commits after last instructor commit
     *
     * @param repository             Local Repository Object.
     * @param programmingExercise    ProgrammingExercise associated with this repo.
     * @param overwriteDefaultBranch If false keeps the default branch and creates squash commit in separate branch, if true squashes the default branch
     */
    public void combineAllStudentCommits(Repository repository, ProgrammingExercise programmingExercise, boolean overwriteDefaultBranch) {
        try (Git studentGit = new Git(repository)) {
            setRemoteUrl(repository);
            // Get last commit hash from template repo
            ObjectId latestHash = getLastCommitHash(programmingExercise.getVcsTemplateRepositoryUri());

            if (latestHash == null) {
                // Template Repository is somehow empty. Should never happen
                log.debug("Cannot find a commit in the template repo for: {}", repository.getLocalPath());
                return;
            }

            // checkout own local "diff" branch to keep main as is
            if (!overwriteDefaultBranch) {
                studentGit.checkout().setCreateBranch(true).setName("diff").call();
            }

            studentGit.reset().setMode(ResetCommand.ResetType.SOFT).setRef(latestHash.getName()).call();
            studentGit.add().addFilepattern(".").call();
            var optionalStudent = ((StudentParticipation) repository.getParticipation()).getStudents().stream().findFirst();
            var name = optionalStudent.map(User::getName).orElse(artemisGitName);
            var email = optionalStudent.map(User::getEmail).orElse(artemisGitEmail);
            GitService.commit(studentGit).setMessage("All student changes in one commit").setCommitter(name, email).call();
        }
        catch (EntityNotFoundException | GitAPIException | JGitInternalException ex) {
            log.warn("Cannot reset the repo {} due to the following exception: {}", repository.getLocalPath(), ex.getMessage());
        }
        finally {
            // if repo is not closed, it causes weird IO issues when trying to delete the repo again
            // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
            repository.close();
        }
    }

    /**
     * Removes all author information from the commits on the currently active branch.
     * Also removes all remotes and FETCH_HEAD since they contain data about the student.
     * Also deletes the .git/logs folder to prevent restoring commits from reflogs
     *
     * @param repository          Local Repository Object.
     * @param programmingExercise ProgrammingExercise associated with this repo.
     */
    public void anonymizeStudentCommits(Repository repository, ProgrammingExercise programmingExercise) {
        try (Git studentGit = new Git(repository)) {
            setRemoteUrl(repository);
            String copyBranchName = "copy";
            String headName = "HEAD";

            // Get last commit hash from template repo
            ObjectId latestHash = getLastCommitHash(programmingExercise.getVcsTemplateRepositoryUri());

            if (latestHash == null) {
                // Template Repository is somehow empty. Should never happen
                log.debug("Cannot find a commit in the template repo for: {}", repository.getLocalPath());
                return;
            }

            // Create copy branch
            Ref copyBranch = studentGit.branchCreate().setName(copyBranchName).call();
            // Reset main branch back to template
            studentGit.reset().setMode(ResetCommand.ResetType.HARD).setRef(ObjectId.toString(latestHash)).call();

            // Get list of all student commits, that is all commits up to the last template commit
            Iterable<RevCommit> commits = studentGit.log().add(copyBranch.getObjectId()).call();
            List<RevCommit> commitList = StreamSupport.stream(commits.spliterator(), false).takeWhile(ref -> !ref.equals(latestHash))
                    .collect(Collectors.toCollection(ArrayList::new));
            // Sort them oldest to newest
            Collections.reverse(commitList);
            // Cherry-Pick all commits back into the main branch and immediately commit amend anonymized author information
            for (RevCommit commit : commitList) {
                ObjectId head = studentGit.getRepository().resolve(headName);
                studentGit.cherryPick().include(commit).call();
                // Only commit amend if head changed; cherry-picking empty commits does nothing
                if (!head.equals(studentGit.getRepository().resolve(headName))) {
                    PersonIdent authorIdent = commit.getAuthorIdent();
                    PersonIdent fakeIdent = new PersonIdent(ANONYMIZED_STUDENT_NAME, ANONYMIZED_STUDENT_EMAIL, authorIdent.getWhenAsInstant(), authorIdent.getZoneId());
                    GitService.commit(studentGit).setAmend(true).setAuthor(fakeIdent).setCommitter(fakeIdent).setMessage(commit.getFullMessage()).call();
                }
            }
            // Delete copy branch
            studentGit.branchDelete().setBranchNames(copyBranchName).setForce(true).call();

            // Delete all remotes
            this.removeRemotes(studentGit);

            // Delete .git/logs/ folder to delete git reflogs
            Path logsPath = Path.of(repository.getDirectory().getPath(), "logs");
            FileUtils.deleteDirectory(logsPath.toFile());

            // Delete FETCH_HEAD containing the url of the last fetch
            Path fetchHeadPath = Path.of(repository.getDirectory().getPath(), "FETCH_HEAD");
            Files.deleteIfExists(fetchHeadPath);
        }
        catch (EntityNotFoundException | GitAPIException | JGitInternalException | IOException ex) {
            log.warn("Cannot anonymize the repo {} due to the following exception: {}", repository.getLocalPath(), ex.getMessage());
        }
        finally {
            // if repo is not closed, it causes weird IO issues when trying to delete the repo again
            // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
            repository.close();
        }
    }

    /**
     * Removes all remote configurations from the given Git repository.
     * This includes both the remote configurations and the remote tracking branches.
     *
     * @param repository The Git repository from which to remove the remotes.
     * @throws IOException     If an I/O error occurs when accessing the repository.
     * @throws GitAPIException If an error occurs in the JGit library while removing the remotes.
     */
    private void removeRemotes(Git repository) throws IOException, GitAPIException {
        // Delete all remotes
        for (RemoteConfig remote : repository.remoteList().call()) {
            repository.remoteRemove().setRemoteName(remote.getName()).call();
            // Manually delete remote tracking branches since JGit apparently fails to do so
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
     * Removes all remotes from a given repository.
     *
     * @param repository The repository whose remotes to delete.
     */
    public void removeRemotesFromRepository(Repository repository) {
        try (Git gitRepo = new Git(repository)) {
            this.removeRemotes(gitRepo);
        }
        catch (EntityNotFoundException | GitAPIException | JGitInternalException | IOException ex) {
            log.warn("Cannot remove the remotes of the repo {} due to the following exception: {}", repository.getLocalPath(), ex.getMessage());
        }
        finally {
            repository.close();
        }
    }

    /**
     * Retrieves a bare JGit repository based on a remote repository URI. This method is functional only when LocalVC is active.
     * It uses the default branch, also see {@link #getBareRepository(VcsRepositoryUri, String)} for more details.
     *
     * @param repositoryUri The URI of the remote VCS repository, not null.
     * @param writeAccess   Whether we write to the repository or not. If true, the git config will be set.
     * @return The initialized bare Repository instance.
     * @throws GitException If the repository cannot be created due to I/O errors or invalid reference names.
     */
    @NotNull
    public Repository getBareRepository(VcsRepositoryUri repositoryUri, boolean writeAccess) {
        return getBareRepository(repositoryUri, defaultBranch, writeAccess);
    }

    /**
     * Retrieves a bare JGit repository based on a remote repository URI. This method is functional only when LocalVC is active.
     * It translates a remote repository URI into a local repository path, attempting to create a repository at this location.
     * This method delegates the creation of the repository to {@code linkRepositoryForExistingGit}, which sets up the repository without a working
     * directory (bare repository).
     * <p>
     * It handles exceptions related to repository creation by throwing a {@code GitException}, providing a more specific error context.
     * Note: This method requires that LocalVC is actively managing the local version control environment to operate correctly.
     *
     * @param repositoryUri The URI of the remote VCS repository, not null.
     * @param branch        The branch to be used for the bare repository, typically the default branch.
     * @param writeAccess   Whether we write to the repository or not. If true, the git config will be set.
     * @return The initialized bare Repository instance.
     * @throws GitException If the repository cannot be created due to I/O errors or invalid reference names.
     */
    public Repository getBareRepository(VcsRepositoryUri repositoryUri, String branch, boolean writeAccess) {
        var localRepoUri = new LocalVCRepositoryUri(repositoryUri.toString());
        var localPath = localRepoUri.getLocalRepositoryPath(localVCBasePath);
        try {
            return linkRepositoryForExistingGit(localPath, repositoryUri, branch, true, writeAccess);
        }
        catch (IOException | InvalidRefNameException e) {
            log.error("Could not create the bare repository with uri {}", repositoryUri, e);
            throw new GitException("Could not create the bare repository", e);
        }
    }

    /**
     * Retrieves an existing bare JGit repository based on a remote repository URI. This method is functional only when LocalVC is active.
     * It checks if the repository already exists in the local file system and returns it if available.
     * If the repository does not exist, it attempts to create it using the provided branch.
     *
     * @param repositoryUri The URI of the remote VCS repository, not null.
     * @param branch        The branch to be used for the bare repository, typically the default branch.
     * @return The initialized bare Repository instance.
     * @throws GitException If the repository cannot be created due to I/O errors or invalid reference names.
     */
    public Repository getExistingBareRepository(VcsRepositoryUri repositoryUri, String branch) {
        var localRepoUri = new LocalVCRepositoryUri(repositoryUri.toString());
        var localPath = localRepoUri.getLocalRepositoryPath(localVCBasePath);
        try {
            return getExistingBareRepository(localPath, repositoryUri, branch);
        }
        catch (IOException | InvalidRefNameException e) {
            log.error("Could not create the bare repository with uri {}", repositoryUri, e);
            throw new GitException("Could not create the bare repository", e);
        }
    }

    /**
     * Creates a new bare Git repository at the specified target location,
     * containing a single commit that includes all files from the source repository.
     * <p>
     * The history of the source repository is not preserved; instead, a new commit is created
     * with a fresh tree built from the source repository's latest state. The commit's author and
     * committer information is taken from the first commit of the source repository.
     * <p>
     * This method avoids cloning the source repository and directly works with its object database for performance reasons.
     *
     * @param sourceRepoUri the URI of the source bare repository to copy from
     * @param targetRepoUri the URI where the new bare repository will be created
     * @param sourceBranch  the name of the branch to copy (e.g., "main" or "master")
     * @return a Repository object representing the newly created bare repository
     * @throws IOException if there is an error accessing the repositories or creating the new commit
     */
    public Repository copyBareRepositoryWithoutHistory(VcsRepositoryUri sourceRepoUri, VcsRepositoryUri targetRepoUri, String sourceBranch) throws IOException {
        log.debug("copy bare repository without history from {} to {} for source branch {}", sourceRepoUri, targetRepoUri, sourceBranch);
        Repository sourceRepo = getExistingBareRepository(sourceRepoUri, sourceBranch);

        logCommits(sourceRepoUri, sourceBranch, sourceRepo);

        // Initialize new bare repository
        var localTargetRepoUri = new LocalVCRepositoryUri(targetRepoUri.toString());
        var localTargetPath = localTargetRepoUri.getLocalRepositoryPath(localVCBasePath);
        try (org.eclipse.jgit.lib.Repository targetRepo = FileRepositoryBuilder.create(localTargetPath.toFile())) {

            targetRepo.create(true); // true for bare
            ObjectInserter inserter = targetRepo.newObjectInserter();

            // Get the HEAD tree of the source
            ObjectId commitId = sourceRepo.resolve("refs/heads/" + sourceBranch + "^{commit}");
            if (commitId == null) {
                throw new IOException("Branch " + sourceBranch + " not found in " + sourceRepoUri);
            }

            RevWalk walk = new RevWalk(sourceRepo);
            RevCommit headCommit = walk.parseCommit(commitId);
            walk.markStart(headCommit);

            RevTree headTree = headCommit.getTree();

            // Get PersonIdent from the very first commit
            ObjectId branchHead = sourceRepo.resolve("refs/heads/" + sourceBranch);
            // TODO: consider to have a back up here, e.g. the first instructor of the course
            PersonIdent personIdent = getFirstCommitPersonIdent(sourceRepo, branchHead);

            // Walk the tree, insert blobs into target repo, and build a new tree
            ObjectId newTreeId = buildCleanTreeFromSource(sourceRepo, inserter, headTree);
            log.debug("found newTreeId {} for target repository {}", newTreeId, targetRepoUri);
            inserter.flush();

            // Create commit with the clean tree
            CommitBuilder commitBuilder = new CommitBuilder();
            commitBuilder.setTreeId(newTreeId);
            commitBuilder.setMessage("Set up template for exercise");

            // Set author and committer information based on the first commit in the source repo
            commitBuilder.setAuthor(personIdent);
            commitBuilder.setCommitter(personIdent);
            ObjectId newCommitId = inserter.insert(commitBuilder);
            inserter.flush();

            // Update refs/heads/main in new bare repo
            RefUpdate refUpdate = targetRepo.updateRef("refs/heads/" + sourceBranch);
            refUpdate.setNewObjectId(newCommitId);
            refUpdate.setForceUpdate(true);
            refUpdate.update();
            return getBareRepository(targetRepoUri, true);
        }
    }

    /**
     * Creates a new bare Git repository at the specified target location, copying all commits
     * and history from the source repository.
     * <p>
     * This method efficiently duplicates the entire commit history from the source to the target
     * repository by directly transferring Git objects (commits, trees, and blobs) without checking
     * out any working tree. It is designed for bare repositories, ensuring that the complete
     * history is preserved in the new repository.
     *
     * @param sourceRepoUri the URI of the source bare repository to copy from
     * @param targetRepoUri the URI where the new bare repository will be created
     * @param sourceBranch  the name of the branch to copy (e.g., "main" or "master")
     * @return a Repository object representing the newly created bare repository
     * @throws IOException if there is an error accessing the repositories or creating the new commit
     */
    public Repository copyBareRepositoryWithHistory(VcsRepositoryUri sourceRepoUri, VcsRepositoryUri targetRepoUri, String sourceBranch) throws IOException {
        log.debug("Copying full history from {} to {} for branch {}", sourceRepoUri, targetRepoUri, sourceBranch);
        Repository sourceRepo = getExistingBareRepository(sourceRepoUri, sourceBranch);

        logCommits(sourceRepoUri, sourceBranch, sourceRepo);

        // Resolve the HEAD commit of the branch
        ObjectId headCommitId = sourceRepo.resolve("refs/heads/" + sourceBranch + "^{commit}");
        if (headCommitId == null) {
            throw new IOException("Source branch " + sourceBranch + " not found in " + sourceRepoUri);
        }

        // Create new bare repository
        var localTargetRepoUri = new LocalVCRepositoryUri(targetRepoUri.toString());
        var localTargetPath = localTargetRepoUri.getLocalRepositoryPath(localVCBasePath);
        try (org.eclipse.jgit.lib.Repository targetRepo = FileRepositoryBuilder.create(localTargetPath.toFile())) {
            targetRepo.create(true); // bare = true

            try (ObjectInserter inserter = targetRepo.newObjectInserter(); RevWalk revWalk = new RevWalk(sourceRepo)) {

                Set<ObjectId> copiedObjects = new HashSet<>();
                Deque<ObjectId> toProcess = new ArrayDeque<>();
                toProcess.add(headCommitId);

                while (!toProcess.isEmpty()) {
                    ObjectId current = toProcess.poll();
                    if (!copiedObjects.add(current)) {
                        continue; // already processed
                    }

                    ObjectLoader loader = sourceRepo.open(current);
                    inserter.insert(loader.getType(), loader.getSize(), loader.openStream());

                    // If this is a commit, enqueue parents and tree
                    if (loader.getType() == Constants.OBJ_COMMIT) {
                        RevCommit commit = revWalk.parseCommit(current);
                        toProcess.add(commit.getTree().getId());
                        for (RevCommit parent : commit.getParents()) {
                            toProcess.add(parent.getId());
                        }
                    }

                    // If this is a tree, enqueue its entries (subtrees and blobs)
                    if (loader.getType() == Constants.OBJ_TREE) {
                        try (TreeWalk treeWalk = new TreeWalk(sourceRepo)) {
                            treeWalk.addTree(current);
                            treeWalk.setRecursive(false);
                            while (treeWalk.next()) {
                                toProcess.add(treeWalk.getObjectId(0));
                            }
                        }
                    }
                }

                inserter.flush();

                // Update target HEAD ref
                RefUpdate refUpdate = targetRepo.updateRef("refs/heads/" + sourceBranch);
                refUpdate.setNewObjectId(headCommitId);
                refUpdate.setForceUpdate(true);
                RefUpdate.Result result = refUpdate.update();
                log.debug("RefUpdate result: {}", result);
            }

            return getBareRepository(targetRepoUri, true);
        }
    }

    private static void logCommits(VcsRepositoryUri sourceRepoUri, String sourceBranch, Repository sourceRepo) throws IOException {
        if (log.isDebugEnabled()) {
            // Log how many commits the source repository has
            try (RevWalk walk = new RevWalk(sourceRepo)) {
                ObjectId debugCommitId = sourceRepo.resolve("refs/heads/" + sourceBranch + "^{commit}");
                if (debugCommitId == null) {
                    log.error("Source repo [{}] has no head commit in branch [{}]", sourceRepoUri, sourceBranch);
                }
                RevCommit headCommit = walk.parseCommit(debugCommitId);
                walk.markStart(headCommit);
                int commitCount = 0;
                for (RevCommit ignored : walk) {
                    commitCount++;
                }
                log.debug("Source repository {} has {} commits", sourceRepoUri, commitCount);
                if (commitCount == 0) {
                    log.error("Source repository {} is empty, no commits to copy. This operation will fail", sourceRepoUri);
                }
            }
        }
    }

    /**
     * Retrieves the PersonIdent (author) from the first (root) commit of the specified branch.
     * <p>
     * This method walks through the commit history of the provided branch and returns
     * the PersonIdent (author) of the commit that has no parents (i.e., the very first commit).
     * <p>
     * Note: If the branch is empty or no commits are found, an IOException is thrown.
     *
     * @param repo       the JGit repository object to read from
     * @param branchHead the ObjectId representing the branch head (e.g., resolve("refs/heads/main"))
     * @return the PersonIdent of the author of the first commit in the branch
     * @throws IOException if the first commit cannot be found or a repository error occurs
     */
    private PersonIdent getFirstCommitPersonIdent(Repository repo, ObjectId branchHead) throws IOException {
        try (RevWalk walk = new RevWalk(repo)) {
            walk.markStart(walk.parseCommit(branchHead));

            for (RevCommit commit : walk) {
                if (commit.getParentCount() == 0) {
                    return commit.getAuthorIdent();
                }
            }
        }
        throw new IOException("First commit not found");
    }

    /**
     * Builds a clean tree from the source repository's tree, copying blobs and subtrees.
     *
     * @param sourceRepo The source repository from which to copy the tree.
     * @param inserter   The ObjectInserter to insert objects into the target repository.
     * @param sourceTree The source tree to copy from.
     * @return The ObjectId of the newly created clean tree in the target repository.
     * @throws IOException If an I/O error occurs during the copying process.
     */
    private ObjectId buildCleanTreeFromSource(Repository sourceRepo, ObjectInserter inserter, RevTree sourceTree) throws IOException {
        TreeWalk treeWalk = new TreeWalk(sourceRepo);
        treeWalk.addTree(sourceTree);
        treeWalk.setRecursive(false);

        TreeFormatter treeFormatter = new TreeFormatter();

        while (treeWalk.next()) {
            ObjectId objectId = treeWalk.getObjectId(0);
            FileMode mode = treeWalk.getFileMode(0);
            String name = treeWalk.getNameString();

            if (mode == FileMode.TREE) {
                // Recursively copy subtrees
                RevTree subTree = new RevWalk(sourceRepo).parseTree(objectId);
                ObjectId newSubTreeId = buildCleanTreeFromSource(sourceRepo, inserter, subTree);
                treeFormatter.append(name, FileMode.TREE, newSubTreeId);
            }
            else {
                // Read blob from source and insert into target
                ObjectLoader loader = sourceRepo.open(objectId);
                ObjectId newBlobId = inserter.insert(Constants.OBJ_BLOB, loader.getBytes());
                treeFormatter.append(name, mode, newBlobId);
            }
        }

        return inserter.insert(treeFormatter);
    }

    private static class FileAndDirectoryFilter implements IOFileFilter {

        private static final String GIT_DIRECTORY_NAME = ".git";

        @Override
        public boolean accept(java.io.File file) {
            return !GIT_DIRECTORY_NAME.equals(file.getName());
        }

        @Override
        public boolean accept(java.io.File directory, String fileName) {
            return !GIT_DIRECTORY_NAME.equals(directory.getName());
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
        FileAndDirectoryFilter filter = new FileAndDirectoryFilter();
        Iterator<java.io.File> itr = FileUtils.iterateFilesAndDirs(repo.getLocalPath().toFile(), filter, filter);
        Map<File, FileType> files = new HashMap<>();

        while (itr.hasNext()) {
            File nextFile = new File(itr.next(), repo);
            Path nextPath = nextFile.toPath();

            if (Files.isSymbolicLink(nextPath)) {
                log.warn("Found a symlink {} in the git repository {}. Do not allow access!", nextPath, repo);
                continue;
            }

            if (omitBinaries && nextFile.isFile() && isBinaryFile(nextFile.getName())) {
                log.debug("Omitting binary file: {}", nextFile);
                continue;
            }

            files.put(nextFile, nextFile.isFile() ? FileType.FILE : FileType.FOLDER);
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
    @NotNull
    public Collection<File> getFiles(Repository repo) {
        FileAndDirectoryFilter filter = new FileAndDirectoryFilter();
        Iterator<java.io.File> itr = FileUtils.iterateFiles(repo.getLocalPath().toFile(), filter, filter);
        Collection<File> files = new ArrayList<>();

        while (itr.hasNext()) {
            files.add(new File(itr.next(), repo));
        }

        return files;
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
     * Deletes a local repository folder.
     *
     * @param repository Local Repository Object.
     * @throws IOException if the deletion of the repository failed.
     */
    @Override
    public void deleteLocalRepository(Repository repository) throws IOException {
        super.deleteLocalRepository(repository);
    }

    /**
     * Deletes a local repository folder for a repoUri.
     *
     * @param repoUri url of the repository.
     */
    public void deleteLocalRepository(VcsRepositoryUri repoUri) {
        try {
            if (repoUri != null && repositoryAlreadyExists(repoUri)) {
                // We need to close the possibly still open repository otherwise an IOException will be thrown on Windows
                Repository repo = getOrCheckoutRepository(repoUri, false, false);
                deleteLocalRepository(repo);
            }
        }
        catch (IOException | GitAPIException e) {
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
     * Get the content of a git repository that contains a participation, as zip or directory.
     *
     * @param repo            Local Repository Object.
     * @param repositoryDir   path where the repo is located on disk
     * @param hideStudentName option to hide the student name for the zip file or directory
     * @param zipOutput       If true the method returns a zip file otherwise a directory.
     * @return path to zip file or directory.
     * @throws IOException if the zipping or copying process failed.
     */
    public Path getRepositoryWithParticipation(Repository repo, String repositoryDir, boolean hideStudentName, boolean zipOutput) throws IOException, UncheckedIOException {
        var exercise = repo.getParticipation().getProgrammingExercise();
        var courseShortName = exercise.getCourseViaExerciseGroupOrCourseMember().getShortName();
        var participation = (ProgrammingExerciseStudentParticipation) repo.getParticipation();

        String repoName = FileUtil.sanitizeFilename(courseShortName + "-" + exercise.getTitle() + "-" + participation.getId());
        if (hideStudentName) {
            repoName += "-student-submission.git";
        }
        else {
            // The zip filename is either the student login, team short name or some default string.
            var studentTeamOrDefault = Objects.requireNonNullElse(participation.getParticipantIdentifier(), "student-submission" + repo.getParticipation().getId());

            repoName += "-" + studentTeamOrDefault;
        }
        repoName = participation.addPracticePrefixIfTestRun(repoName);

        if (zipOutput) {
            return zipFiles(repo.getLocalPath(), repoName, repositoryDir, null);
        }
        else {
            Path targetDir = Path.of(repositoryDir, repoName);

            FileUtils.copyDirectory(repo.getLocalPath().toFile(), targetDir.toFile());
            return targetDir;
        }

    }

    /**
     * Zips the contents of a folder, files are filtered according to the contentFilter.
     * Content filtering is added with the intention of optionally excluding ".git" directory from the result.
     *
     * @param contentRootPath the root path of the content to zip
     * @param zipFilename     the name of the zipped file
     * @param zipDir          path of folder where the zip should be located on disk
     * @param contentFilter   path filter to exclude some files, can be null to include everything
     * @return path to the zip file
     * @throws IOException if the zipping process failed.
     */
    public Path zipFiles(Path contentRootPath, String zipFilename, String zipDir, @Nullable Predicate<Path> contentFilter) throws IOException, UncheckedIOException {
        // Strip slashes from name
        var zipFilenameWithoutSlash = zipFilename.replaceAll("\\s", "");

        if (!zipFilenameWithoutSlash.endsWith(".zip")) {
            zipFilenameWithoutSlash += ".zip";
        }

        Path zipFilePath = Path.of(zipDir, zipFilenameWithoutSlash);
        Files.createDirectories(Path.of(zipDir));
        return zipFileService.createZipFileWithFolderContent(zipFilePath, contentRootPath, contentFilter);
    }

    /**
     * Checks if repo was already checked out and is present on disk
     *
     * @param repoUri URL of the remote repository.
     * @return True if repo exists on disk
     */
    public boolean repositoryAlreadyExists(VcsRepositoryUri repoUri) {
        Path localPath = getDefaultLocalPathOfRepo(repoUri);
        return Files.exists(localPath);
    }

    /**
     * Stashes not submitted/committed changes of the repo.
     *
     * @param repo student repo of a participation in a programming exercise
     * @throws GitAPIException if the git operation does not work
     */
    public void stashChanges(Repository repo) throws GitAPIException {
        try (Git git = new Git(repo)) {
            git.stashCreate().call();
        }
    }

    private PullCommand pullCommand(Git git) {
        return authenticate(git.pull());
    }

    private PushCommand pushCommand(Git git) {
        return authenticate(git.push());
    }

    private FetchCommand fetchCommand(Git git) {
        return authenticate(git.fetch());
    }

    private LsRemoteCommand lsRemoteCommand(Git git) {
        return authenticate(git.lsRemote());
    }

    protected <C extends GitCommand<?>> C authenticate(TransportCommand<C, ?> command) {
        return command.setTransportConfigCallback(sshCallback);
    }

    /**
     * Checkout a repository and get the git log for a given repository uri.
     *
     * @param vcsRepositoryUri the repository uri for which the git log should be retrieved
     * @return a list of commit info DTOs containing author, timestamp, commit message, and hash
     * @throws GitAPIException if an error occurs while retrieving the git log
     */
    public List<CommitInfoDTO> getCommitInfos(VcsRepositoryUri vcsRepositoryUri) throws GitAPIException {
        List<CommitInfoDTO> commitInfos = new ArrayList<>();

        if (profileService.isLocalVCActive()) {
            log.debug("Using local VCS for getting commit info on repo {}", vcsRepositoryUri);
            try (var repo = getBareRepository(vcsRepositoryUri, false); var git = new Git(repo)) {
                getCommitInfo(git, commitInfos);
            }
        }
        else {
            log.debug("Checking out repo {} to get commit info", vcsRepositoryUri);
            try (var repo = getOrCheckoutRepository(vcsRepositoryUri, true, false); var git = new Git(repo)) {
                getCommitInfo(git, commitInfos);
            }
        }

        return commitInfos;
    }

    private void getCommitInfo(Git git, List<CommitInfoDTO> commitInfos) throws GitAPIException {
        Iterable<RevCommit> commits = git.log().call();
        commits.forEach(commit -> {
            var commitInfo = CommitInfoDTO.of(commit);
            commitInfos.add(commitInfo);
        });
    }
}
