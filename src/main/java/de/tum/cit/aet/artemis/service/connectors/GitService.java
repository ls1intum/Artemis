package de.tum.cit.aet.artemis.service.connectors;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
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
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.File;
import de.tum.cit.aet.artemis.domain.FileType;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.domain.Repository;
import de.tum.cit.aet.artemis.domain.Submission;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exception.GitException;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.service.ProfileService;
import de.tum.cit.aet.artemis.service.ZipFileService;
import de.tum.cit.aet.artemis.service.connectors.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.web.rest.dto.CommitInfoDTO;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

@Profile(PROFILE_CORE)
@Service
public class GitService extends AbstractGitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    private final ProfileService profileService;

    @Value("${artemis.version-control.local-vcs-repo-path:#{null}}")
    private String localVCBasePath;

    @Value("${artemis.repo-clone-path}")
    private String repoClonePath;

    @Value("${artemis.git.name}")
    private String artemisGitName;

    @Value("${artemis.git.email}")
    private String artemisGitEmail;

    @Value("${artemis.version-control.user}")
    protected String gitUser;

    @Value("${artemis.version-control.password}")
    protected String gitPassword;

    // TODO: clean up properly in multi node environments
    private final Map<Path, Repository> cachedRepositories = new ConcurrentHashMap<>();

    // TODO: clean up when exercise or participation is deleted
    private final Map<Path, Repository> cachedBareRepositories = new ConcurrentHashMap<>();

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
    @PostConstruct
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
     * For GitLab, the URI is the same internally as the one that is used by the students to clone the repository using their local Git client.
     * For the local VCS however, the repository is cloned from the folder defined in the environment variable "artemis.version-control.local-vcs-repo-path".
     *
     * @param vcsRepositoryUri the {@link VcsRepositoryUri} for which to get the URI
     * @return the URI (SSH, HTTP(S), or local path)
     * @throws URISyntaxException if SSH is used and the SSH URI could not be retrieved.
     */
    @Override
    protected URI getGitUri(VcsRepositoryUri vcsRepositoryUri) throws URISyntaxException {
        if (profileService.isLocalVcsCiActive()) {
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
     * @return the repository if it could be checked out
     * @throws GitAPIException if the repository could not be checked out.
     */
    public Repository getOrCheckoutRepository(ProgrammingExerciseParticipation participation) throws GitAPIException {
        return getOrCheckoutRepository(participation, repoClonePath);
    }

    /**
     * Get the local repository for a given participation. If the local repo does not exist yet, it will be checked out.
     * Saves the local repo in the default path.
     *
     * @param participation Participation the remote repository belongs to.
     * @param targetPath    path where the repo is located on disk
     * @return the repository if it could be checked out
     * @throws GitAPIException if the repository could not be checked out.
     * @throws GitException    if the same repository is attempted to be cloned multiple times.
     */
    public Repository getOrCheckoutRepository(ProgrammingExerciseParticipation participation, String targetPath) throws GitAPIException, GitException {
        var repoUri = participation.getVcsRepositoryUri();
        Repository repository = getOrCheckoutRepository(repoUri, targetPath, true);
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
    public Repository getOrCheckoutRepositoryForJPlag(ProgrammingExerciseParticipation participation, String targetPath) throws GitAPIException, InvalidPathException {
        var repoUri = participation.getVcsRepositoryUri();
        String repoFolderName = repoUri.folderNameForRepositoryUri();

        // Replace the exercise name in the repository folder name with the participation ID.
        // This is necessary to be able to refer back to the correct participation after the JPlag detection run.
        String updatedRepoFolderName = repoFolderName.replaceAll("/[a-zA-Z0-9]*-", "/" + participation.getId() + "-");
        Path localPath = Path.of(targetPath, updatedRepoFolderName);

        Repository repository = getOrCheckoutRepository(repoUri, localPath, true);
        repository.setParticipation(participation);

        return repository;
    }

    /**
     * Get the local repository for a given remote repository URI. If the local repo does not exist yet, it will be checked out.
     * Saves the repo in the default path
     *
     * @param repoUri   The remote repository.
     * @param pullOnGet Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @return the repository if it could be checked out.
     * @throws GitAPIException if the repository could not be checked out.
     */
    public Repository getOrCheckoutRepository(VcsRepositoryUri repoUri, boolean pullOnGet) throws GitAPIException {
        return getOrCheckoutRepository(repoUri, repoClonePath, pullOnGet);
    }

    /**
     * Get the local repository for a given remote repository URI. If the local repo does not exist yet, it will be checked out.
     *
     * @param repoUri    The remote repository.
     * @param targetPath path where the repo is located on disk
     * @param pullOnGet  Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @return the repository if it could be checked out.
     * @throws GitAPIException if the repository could not be checked out.
     * @throws GitException    if the same repository is attempted to be cloned multiple times.
     */
    public Repository getOrCheckoutRepository(VcsRepositoryUri repoUri, String targetPath, boolean pullOnGet) throws GitAPIException, GitException {
        Path localPath = getLocalPathOfRepo(targetPath, repoUri);
        return getOrCheckoutRepository(repoUri, localPath, pullOnGet);
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
        var repository = getOrCheckoutRepository(vcsRepositoryUri, pullOnGet);
        return checkoutRepositoryAtCommit(repository, commitHash);
    }

    /**
     * Get the local repository for a given remote repository URI. If the local repo does not exist yet, it will be checked out.
     *
     * @param repoUri       The remote repository.
     * @param pullOnGet     Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @param defaultBranch The default branch of the target repository.
     * @return the repository if it could be checked out.
     * @throws GitAPIException if the repository could not be checked out.
     * @throws GitException    if the same repository is attempted to be cloned multiple times.
     */
    public Repository getOrCheckoutRepository(VcsRepositoryUri repoUri, boolean pullOnGet, String defaultBranch) throws GitAPIException, GitException {
        Path localPath = getLocalPathOfRepo(repoClonePath, repoUri);
        return getOrCheckoutRepository(repoUri, repoUri, localPath, pullOnGet, defaultBranch);
    }

    public Repository getOrCheckoutRepositoryIntoTargetDirectory(VcsRepositoryUri repoUri, VcsRepositoryUri targetUrl, boolean pullOnGet)
            throws GitAPIException, GitException, InvalidPathException {
        Path localPath = getDefaultLocalPathOfRepo(targetUrl);
        return getOrCheckoutRepository(repoUri, targetUrl, localPath, pullOnGet);
    }

    public Repository getOrCheckoutRepository(VcsRepositoryUri repoUri, Path localPath, boolean pullOnGet) throws GitAPIException, GitException, InvalidPathException {
        return getOrCheckoutRepository(repoUri, repoUri, localPath, pullOnGet);
    }

    /**
     * Get the local repository for a given remote repository URI. If the local repo does not exist yet, it will be checked out.
     *
     * @param sourceRepoUri The source remote repository.
     * @param targetRepoUri The target remote repository.
     * @param localPath     The local path to clone the repository to.
     * @param pullOnGet     Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @return the repository if it could be checked out.
     * @throws GitAPIException      if the repository could not be checked out.
     * @throws GitException         if the same repository is attempted to be cloned multiple times.
     * @throws InvalidPathException if the repository could not be checked out Because it contains unmappable characters.
     */
    public Repository getOrCheckoutRepository(VcsRepositoryUri sourceRepoUri, VcsRepositoryUri targetRepoUri, Path localPath, boolean pullOnGet)
            throws GitAPIException, GitException, InvalidPathException {
        return getOrCheckoutRepository(sourceRepoUri, targetRepoUri, localPath, pullOnGet, defaultBranch);
    }

    /**
     * Get the local repository for a given remote repository URI. If the local repo does not exist yet, it will be checked out.
     *
     * @param sourceRepoUri The source remote repository.
     * @param targetRepoUri The target remote repository.
     * @param localPath     The local path to clone the repository to.
     * @param pullOnGet     Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @param defaultBranch The default branch of the target repository
     * @return the repository if it could be checked out.
     * @throws GitAPIException      if the repository could not be checked out.
     * @throws GitException         if the same repository is attempted to be cloned multiple times.
     * @throws InvalidPathException if the repository could not be checked out Because it contains unmappable characters.
     */
    public Repository getOrCheckoutRepository(VcsRepositoryUri sourceRepoUri, VcsRepositoryUri targetRepoUri, Path localPath, boolean pullOnGet, String defaultBranch)
            throws GitAPIException, GitException, InvalidPathException {
        // First try to just retrieve the git repository from our server, as it might already be checked out.
        // If the sourceRepoUri differs from the targetRepoUri, we attempt to clone the source repo into the target directory
        Repository repository = getExistingCheckedOutRepositoryByLocalPath(localPath, targetRepoUri, defaultBranch);

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
                log.info("Cloning from {} to {}", gitUriAsString, localPath);
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
            return getExistingCheckedOutRepositoryByLocalPath(localPath, targetRepoUri, defaultBranch);
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

    /**
     * Checks whether the repository is cached.
     * This method does only support repositories that use the repoClonePath which is set in the application-artemis.yml file!
     *
     * @param repositoryUri the url of the repository
     * @return returns true if the repository is already cached
     */
    public boolean isRepositoryCached(VcsRepositoryUri repositoryUri) {
        Path localPath = getLocalPathOfRepo(repoClonePath, repositoryUri);
        if (localPath == null) {
            return false;
        }
        // Check if the repository is already cached in the server's session.
        return cachedRepositories.containsKey(localPath);
    }

    /**
     * Combine all commits of the given repository into one.
     *
     * @param repoUri of the repository to combine.
     * @throws GitAPIException If the checkout fails
     */
    public void combineAllCommitsOfRepositoryIntoOne(VcsRepositoryUri repoUri) throws GitAPIException {
        Repository exerciseRepository = getOrCheckoutRepository(repoUri, true);
        combineAllCommitsIntoInitialCommit(exerciseRepository);
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
    public Path getLocalPathOfRepo(String targetPath, VcsRepositoryUri targetUrl) {
        if (targetUrl == null) {
            return null;
        }
        return Path.of(targetPath.replaceAll("^\\." + Pattern.quote(java.io.File.separator), ""), targetUrl.folderNameForRepositoryUri());
    }

    /**
     * Get an existing git repository that is checked out on the server. Returns immediately null if the localPath does not exist. Will first try to retrieve a cached repository
     * from cachedRepositories. Side effect: This method caches retrieved repositories in a HashMap, so continuous retrievals can be avoided (reduces load).
     *
     * @param localPath           to git repo on server.
     * @param remoteRepositoryUri the remote repository uri for the git repository, will be added to the Repository object for later use, can be null
     * @return the git repository in the localPath or **null** if it does not exist on the server.
     */
    public Repository getExistingCheckedOutRepositoryByLocalPath(@NotNull Path localPath, @Nullable VcsRepositoryUri remoteRepositoryUri) {
        return getExistingCheckedOutRepositoryByLocalPath(localPath, remoteRepositoryUri, defaultBranch);
    }

    /**
     * Get an existing git repository that is checked out on the server. Returns immediately null if the localPath does not exist. Will first try to retrieve a cached repository
     * from cachedRepositories. Side effect: This method caches retrieved repositories in a HashMap, so continuous retrievals can be avoided (reduces load).
     *
     * @param localPath           to git repo on server.
     * @param remoteRepositoryUri the remote repository uri for the git repository, will be added to the Repository object for later use, can be null
     * @param defaultBranch       the name of the branch that should be used as default branch
     * @return the git repository in the localPath or **null** if it does not exist on the server.
     */
    public Repository getExistingCheckedOutRepositoryByLocalPath(@NotNull Path localPath, @Nullable VcsRepositoryUri remoteRepositoryUri, String defaultBranch) {
        try {
            // Check if there is a folder with the provided path of the git repository.
            if (!Files.exists(localPath)) {
                // In this case we should remove the repository if cached, because it can't exist anymore.
                cachedRepositories.remove(localPath);
                return null;
            }

            // Check if the repository is already cached in the server's session.
            Repository cachedRepository = cachedRepositories.get(localPath);
            if (cachedRepository != null) {
                return cachedRepository;
            }
            // Else try to retrieve the git repository from our server. It could e.g. be the case that the folder is there, but there is no .git folder in it!
            Repository repository = linkRepositoryForExistingGit(localPath, remoteRepositoryUri, defaultBranch, false);

            // Cache the JGit repository object for later use: avoids the expensive re-opening of local repositories
            cachedRepositories.put(localPath, repository);
            return repository;
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
     * The remote uri of the target repo is still the uri of the source repo.
     * We need to change it to the uri of the target repo.
     * The content to be copied then gets pushed to the new repo.
     *
     * @param targetRepo    Local target repo
     * @param targetRepoUri URI of targets repo
     * @param oldBranch     default branch that was used when the exercise was created (might differ from the default branch of a participation)
     * @throws GitAPIException if the repo could not be pushed
     */
    public void pushSourceToTargetRepo(Repository targetRepo, VcsRepositoryUri targetRepoUri, String oldBranch) throws GitAPIException {
        try (Git git = new Git(targetRepo)) {
            // overwrite the old remote uri with the target uri
            git.remoteSetUrl().setRemoteName(REMOTE_NAME).setRemoteUri(new URIish(getGitUriAsString(targetRepoUri))).call();
            log.debug("pushSourceToTargetRepo -> Push {}", targetRepoUri.getURI());

            if (!defaultBranch.equals(oldBranch)) {
                targetRepo.getConfig().unsetSection(ConfigConstants.CONFIG_BRANCH_SECTION, oldBranch);
                git.branchRename().setNewName(defaultBranch).setOldName(oldBranch).call();
            }

            // push the source content to the new remote
            pushCommand(git).call();
        }
        catch (URISyntaxException e) {
            log.error("Error while pushing to remote target: ", e);
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
                Date since = Date.from(Instant.EPOCH);
                Date until = Date.from(filterLateSubmissionsDate.toInstant());
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
     * @param repository          Local Repository Object.
     * @param programmingExercise ProgrammingExercise associated with this repo.
     * @param overwriteMain       If false keeps main and creates squash commit in separate branch, if true squashes main
     */
    public void combineAllStudentCommits(Repository repository, ProgrammingExercise programmingExercise, boolean overwriteMain) {
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
            if (!overwriteMain) {
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
                    PersonIdent fakeIdent = new PersonIdent(ANONYMIZED_STUDENT_NAME, ANONYMIZED_STUDENT_EMAIL, authorIdent.getWhen(), authorIdent.getTimeZone());
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
     * It translates a remote repository URI into a local repository path, attempting to create a repository at this location.
     *
     * @param repositoryUri The URI of the remote VCS repository, not null.
     * @return The initialized bare Repository instance.
     * @throws GitException If the repository cannot be created due to I/O errors or invalid reference names.
     *
     *                          <p>
     *                          This method delegates the creation of the repository to {@code linkRepositoryForExistingGit}, which sets up the repository
     *                          without a working directory (bare repository). It handles exceptions related to repository creation by throwing
     *                          a {@code GitException}, providing a more specific error context.
     *                          </p>
     *
     *                          <p>
     *                          Note: This method requires that LocalVC is actively managing the local version control environment to operate correctly.
     *                          </p>
     */
    public Repository getBareRepository(VcsRepositoryUri repositoryUri) {
        var localRepoUri = new LocalVCRepositoryUri(repositoryUri.toString());
        var localPath = localRepoUri.getLocalRepositoryPath(localVCBasePath);
        // Check if the repository is already cached in the server's session.
        Repository cachedRepository = cachedBareRepositories.get(localPath);
        if (cachedRepository != null) {
            return cachedRepository;
        }
        try {
            var repository = linkRepositoryForExistingGit(localPath, repositoryUri, defaultBranch, true);
            cachedBareRepositories.put(localPath, repository);
            return repository;
        }
        catch (IOException | InvalidRefNameException e) {
            log.error("Could not create the bare repository with uri {}", repositoryUri, e);
            throw new GitException("Could not create the bare repository", e);
        }
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
     * Lists all files and directories within the given repository, excluding symbolic links.
     * This method performs a file scan and filters out symbolic links.
     * It supports bare and checked-out repositories.
     * <p>
     * Note: This method does not handle changes to the repository content between invocations. If files change
     * after the initial caching, the cache does not automatically refresh, which may lead to stale data.
     *
     * @param repo The repository to scan for files and directories.
     * @return A {@link Map} where each key is a {@link File} object representing a file or directory, and each value is
     *         the corresponding {@link FileType} (FILE or FOLDER). The map excludes symbolic links.
     */
    public Map<File, FileType> listFilesAndFolders(Repository repo) {
        FileAndDirectoryFilter filter = new FileAndDirectoryFilter();

        Iterator<java.io.File> itr = FileUtils.iterateFilesAndDirs(repo.getLocalPath().toFile(), filter, filter);
        Map<File, FileType> files = new HashMap<>();

        while (itr.hasNext()) {
            File nextFile = new File(itr.next(), repo);
            Path nextPath = nextFile.toPath();

            // filter out symlinks
            if (Files.isSymbolicLink(nextPath)) {
                log.warn("Found a symlink {} in the git repository {}. Do not allow access!", nextPath, repo);
                continue;
            }

            files.put(nextFile, nextFile.isFile() ? FileType.FILE : FileType.FOLDER);
        }
        return files;
    }

    /**
     * List all files in the repository. In an empty git repo, this method returns 0.
     *
     * @param repo Local Repository Object.
     * @return Collection of File objects
     */
    @NotNull
    public Collection<File> listFiles(Repository repo) {
        // Check if list of files is already cached
        if (repo.getFiles() == null) {
            FileAndDirectoryFilter filter = new FileAndDirectoryFilter();
            Iterator<java.io.File> itr = FileUtils.iterateFiles(repo.getLocalPath().toFile(), filter, filter);
            Collection<File> files = new ArrayList<>();

            while (itr.hasNext()) {
                files.add(new File(itr.next(), repo));
            }

            // Cache the list of files
            // Avoid expensive rescanning
            repo.setFiles(files);
        }
        return repo.getFiles();
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
    public boolean isClean(Repository repo) throws GitAPIException {
        try (Git git = new Git(repo)) {
            Status status = git.status().call();
            return status.isClean();
        }
    }

    /**
     * Combines all commits in the selected repo into the first commit, keeping its commit message. Executes a hard reset to remote before the combine to avoid conflicts.
     *
     * @param repo to combine commits for
     * @throws GitAPIException       on io errors or git exceptions.
     * @throws IllegalStateException if there is no commit in the git repository.
     */
    public void combineAllCommitsIntoInitialCommit(Repository repo) throws IllegalStateException, GitAPIException {
        try (Git git = new Git(repo)) {
            resetToOriginHead(repo);
            List<RevCommit> commits = StreamSupport.stream(git.log().call().spliterator(), false).toList();
            RevCommit firstCommit = commits.getLast();
            // If there is a first commit, combine all other commits into it.
            if (firstCommit != null) {
                git.reset().setMode(ResetCommand.ResetType.SOFT).setRef(firstCommit.getId().getName()).call();
                git.add().addFilepattern(".").call();
                GitService.commit(git).setAmend(true).setMessage(firstCommit.getFullMessage()).call();
                log.debug("combineAllCommitsIntoInitialCommit -> Push {}", repo.getLocalPath());
                pushCommand(git).setForce(true).call();
            }
            else {
                // Normally there always has to be a commit, so we throw an error in case none can be found.
                throw new IllegalStateException();
            }
        }
        // This exception occurs when there was no change to the repo and a commit is done, so it is ignored.
        catch (JGitInternalException ex) {
            log.debug("Did not combine the repository {} as there were no changes to commit. Exception: {}", repo, ex.getMessage());
        }
        catch (GitAPIException ex) {
            log.error("Could not combine repository {} due to exception:", repo, ex);
            throw ex;
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
        cachedRepositories.remove(repository.getLocalPath());
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
                Repository repo = getOrCheckoutRepository(repoUri, false);
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
        var folderPath = Path.of(repoClonePath, programmingExercise.getProjectKey());
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

        String repoName = FileService.sanitizeFilename(courseShortName + "-" + exercise.getTitle() + "-" + participation.getId());
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

        if (profileService.isLocalVcsActive()) {
            log.debug("Using local VCS for getting commit info on repo {}", vcsRepositoryUri);
            try (var repo = getBareRepository(vcsRepositoryUri); var git = new Git(repo)) {
                getCommitInfo(git, commitInfos);
            }
        }
        else {
            log.debug("Checking out repo {} to get commit info", vcsRepositoryUri);
            try (var repo = getOrCheckoutRepository(vcsRepositoryUri, true); var git = new Git(repo)) {
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

    public void clearCachedRepositories() {
        cachedRepositories.clear();
        cachedBareRepositories.clear();
    }
}
