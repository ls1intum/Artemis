package de.tum.in.www1.artemis.service.connectors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.KeyPasswordProvider;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.GitException;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.ZipFileService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class GitService {

    private final Logger log = LoggerFactory.getLogger(GitService.class);

    private final Environment environment;

    @Value("${artemis.version-control.url}")
    private URL gitUrl;

    @Value("${artemis.local-git-server-path}")
    private String localGitPath;

    @Value("${artemis.version-control.user}")
    private String gitUser;

    @Value("${artemis.version-control.password}")
    private String gitPassword;

    @Value("${artemis.version-control.token:#{null}}")
    private Optional<String> gitToken;

    @Value("${artemis.version-control.ssh-private-key-folder-path:#{null}}")
    private Optional<String> gitSshPrivateKeyPath;

    @Value("${artemis.version-control.ssh-private-key-password:#{null}}")
    private Optional<String> gitSshPrivateKeyPassphrase;

    @Value("${artemis.version-control.ssh-template-clone-url:#{null}}")
    private Optional<String> sshUrlTemplate;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    @Value("${artemis.repo-clone-path}")
    private String repoClonePath;

    @Value("${artemis.git.name}")
    private String artemisGitName;

    @Value("${artemis.git.email}")
    private String artemisGitEmail;

    private final Map<Path, Repository> cachedRepositories = new ConcurrentHashMap<>();

    private final Map<Path, Path> cloneInProgressOperations = new ConcurrentHashMap<>();

    private final FileService fileService;

    private final ZipFileService zipFileService;

    private TransportConfigCallback sshCallback;

    private static final int JGIT_TIMEOUT_IN_SECONDS = 5;

    private static final String ANONYMIZED_STUDENT_NAME = "student";

    private static final String ANONYMIZED_STUDENT_EMAIL = "";

    private static final String REMOTE_NAME = "origin";

    public GitService(Environment environment, FileService fileService, ZipFileService zipFileService) {
        log.info("file.encoding={}", System.getProperty("file.encoding"));
        log.info("sun.jnu.encoding={}", System.getProperty("sun.jnu.encoding"));
        log.info("Default Charset={}", Charset.defaultCharset());
        log.info("Default Charset in Use={}", new OutputStreamWriter(new ByteArrayOutputStream()).getEncoding());
        this.environment = environment;
        this.fileService = fileService;
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

    private void configureSsh() {

        var credentialsProvider = new CredentialsProvider() {

            @Override
            public boolean isInteractive() {
                return false;
            }

            @Override
            public boolean supports(CredentialItem... items) {
                return true;
            }

            // Note: the following method allows us to store known hosts
            @Override
            public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
                for (CredentialItem item : items) {
                    if (item instanceof CredentialItem.YesNoType yesNoItem) {
                        yesNoItem.setValue(true);
                    }
                }
                return true;
            }
        };

        CredentialsProvider.setDefault(credentialsProvider);

        var sshSessionFactory = new SshdSessionFactoryBuilder().setKeyPasswordProvider(keyPasswordProvider -> new KeyPasswordProvider() {

            @Override
            public char[] getPassphrase(URIish uri, int attempt) {
                // Example: /Users/artemis/.ssh/artemis/id_rsa contains /Users/artemis/.ssh/artemis
                if (gitSshPrivateKeyPath.isPresent() && uri.getPath().contains(gitSshPrivateKeyPath.get())) {
                    return gitSshPrivateKeyPassphrase.get().toCharArray();
                }
                else {
                    return null;
                }
            }

            @Override
            public void setAttempts(int maxNumberOfAttempts) {
            }

            @Override
            public boolean keyLoaded(URIish uri, int attempt, Exception error) {
                return false;
            }
        }).setConfigStoreFactory((homeDir, configFile, localUserName) -> new SshConfigStore() {

            @Override
            public HostConfig lookup(String hostName, int port, String userName) {
                return new HostConfig() {

                    @Override
                    public String getValue(String key) {
                        return null;
                    }

                    @Override
                    public List<String> getValues(String key) {
                        return Collections.emptyList();
                    }

                    @Override
                    public Map<String, String> getOptions() {
                        log.debug("getOptions: {}:{}", hostName, port);
                        if (hostName.equals(gitUrl.getHost())) {
                            return Collections.singletonMap(SshConstants.STRICT_HOST_KEY_CHECKING, SshConstants.NO);
                        }
                        else {
                            return Collections.emptyMap();
                        }
                    }

                    @Override
                    public Map<String, List<String>> getMultiValuedOptions() {
                        return Collections.emptyMap();
                    }
                };
            }

            @Override
            public HostConfig lookupDefault(String hostName, int port, String userName) {
                return lookup(hostName, port, userName);
            }
        }).setSshDirectory(new java.io.File(gitSshPrivateKeyPath.get())).setHomeDirectory(new java.io.File(System.getProperty("user.home"))).build(new JGitKeyCache());

        sshCallback = transport -> {
            if (transport instanceof SshTransport sshTransport) {
                transport.setTimeout(JGIT_TIMEOUT_IN_SECONDS);
                sshTransport.setSshSessionFactory(sshSessionFactory);
            }
            else {
                log.error("Cannot use ssh properly because of mismatch of Jgit transport object: {}", transport);
            }
        };
    }

    private boolean useSsh() {
        return gitSshPrivateKeyPath.isPresent() && sshUrlTemplate.isPresent();
        // password is optional and will only be applied if the ssh private key was encrypted using a password
    }

    private String getGitUriAsString(VcsRepositoryUrl vcsRepositoryUrl) throws URISyntaxException {
        return getGitUri(vcsRepositoryUrl).toString();
    }

    private URI getGitUri(VcsRepositoryUrl vcsRepositoryUrl) throws URISyntaxException {
        // If the "localgit" profile is active the repository is cloned from the folder defined in artemis.local-git-server-path.
        if (Arrays.asList(this.environment.getActiveProfiles()).contains("localgit")) {
            String vcsRepositoryFolderPath = vcsRepositoryUrl.folderNameForRepositoryUrl();
            return new URI(localGitPath + vcsRepositoryFolderPath);
        }
        return useSsh() ? getSshUri(vcsRepositoryUrl) : vcsRepositoryUrl.getURI();
    }

    private URI getSshUri(VcsRepositoryUrl vcsRepositoryUrl) throws URISyntaxException {
        URI templateUri = new URI(sshUrlTemplate.get());
        // Example Bitbucket: ssh://git@bitbucket.ase.in.tum.de:7999/se2021w07h02/se2021w07h02-ga27yox.git
        // Example Gitlab: ssh://git@gitlab.ase.in.tum.de:2222/se2021w07h02/se2021w07h02-ga27yox.git
        final var repositoryUri = vcsRepositoryUrl.getURI();
        // Bitbucket repository urls (until now mainly used with username and password authentication) include "/scm" in the url, which cannot be used in ssh urls,
        // therefore we need to replace it here
        final var path = repositoryUri.getPath().replace("/scm", "");
        return new URI(templateUri.getScheme(), templateUri.getUserInfo(), templateUri.getHost(), templateUri.getPort(), path, null, repositoryUri.getFragment());
    }

    /**
     * Get the local repository for a given participation. If the local repo does not exist yet, it will be checked out.
     * Saves the local repo in the default path.
     *
     * @param participation Participation the remote repository belongs to.
     * @return the repository if it could be checked out
     * @throws GitAPIException      if the repository could not be checked out.
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
     * @throws GitAPIException      if the repository could not be checked out.
     * @throws GitException         if the same repository is attempted to be cloned multiple times.
     */
    public Repository getOrCheckoutRepository(ProgrammingExerciseParticipation participation, String targetPath) throws GitAPIException, GitException {
        var repoUrl = participation.getVcsRepositoryUrl();
        Repository repository = getOrCheckoutRepository(repoUrl, targetPath, true);
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
        var repoUrl = participation.getVcsRepositoryUrl();
        String repoFolderName = repoUrl.folderNameForRepositoryUrl();

        // Replace the exercise name in the repository folder name with the participation ID.
        // This is necessary to be able to refer back to the correct participation after the JPlag detection run.
        String updatedRepoFolderName = repoFolderName.replaceAll("/[a-zA-Z0-9]*-", "/" + participation.getId() + "-");
        Path localPath = Path.of(targetPath, updatedRepoFolderName);

        Repository repository = getOrCheckoutRepository(repoUrl, localPath, true);
        repository.setParticipation(participation);

        return repository;
    }

    /**
     * Get the local repository for a given remote repository URL. If the local repo does not exist yet, it will be checked out.
     * Saves the repo in the default path
     *
     * @param repoUrl   The remote repository.
     * @param pullOnGet Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @return the repository if it could be checked out.
     * @throws GitAPIException      if the repository could not be checked out.
     */
    public Repository getOrCheckoutRepository(VcsRepositoryUrl repoUrl, boolean pullOnGet) throws GitAPIException {
        return getOrCheckoutRepository(repoUrl, repoClonePath, pullOnGet);
    }

    /**
     * Get the local repository for a given remote repository URL. If the local repo does not exist yet, it will be checked out.
     *
     * @param repoUrl    The remote repository.
     * @param targetPath path where the repo is located on disk
     * @param pullOnGet  Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @return the repository if it could be checked out.
     * @throws GitAPIException      if the repository could not be checked out.
     * @throws GitException         if the same repository is attempted to be cloned multiple times.
     */
    public Repository getOrCheckoutRepository(VcsRepositoryUrl repoUrl, String targetPath, boolean pullOnGet) throws GitAPIException, GitException {
        Path localPath = getLocalPathOfRepo(targetPath, repoUrl);
        return getOrCheckoutRepository(repoUrl, localPath, pullOnGet);
    }

    /**
     * Get the local repository for a given remote repository URL. If the local repo does not exist yet, it will be checked out.
     *
     * @param repoUrl       The remote repository.
     * @param pullOnGet     Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @param defaultBranch The default branch of the target repository.
     * @return the repository if it could be checked out.
     * @throws GitAPIException      if the repository could not be checked out.
     * @throws GitException         if the same repository is attempted to be cloned multiple times.
     */
    public Repository getOrCheckoutRepository(VcsRepositoryUrl repoUrl, boolean pullOnGet, String defaultBranch) throws GitAPIException, GitException {
        Path localPath = getLocalPathOfRepo(repoClonePath, repoUrl);
        return getOrCheckoutRepository(repoUrl, repoUrl, localPath, pullOnGet, defaultBranch);
    }

    public Repository getOrCheckoutRepositoryIntoTargetDirectory(VcsRepositoryUrl repoUrl, VcsRepositoryUrl targetUrl, boolean pullOnGet)
            throws GitAPIException, GitException, InvalidPathException {
        Path localPath = getDefaultLocalPathOfRepo(targetUrl);
        return getOrCheckoutRepository(repoUrl, targetUrl, localPath, pullOnGet);
    }

    public Repository getOrCheckoutRepository(VcsRepositoryUrl repoUrl, Path localPath, boolean pullOnGet) throws GitAPIException, GitException, InvalidPathException {
        return getOrCheckoutRepository(repoUrl, repoUrl, localPath, pullOnGet);
    }

    /**
     * Get the local repository for a given remote repository URL. If the local repo does not exist yet, it will be checked out.
     *
     * @param sourceRepoUrl The source remote repository.
     * @param targetRepoUrl The target remote repository.
     * @param localPath     The local path to clone the repository to.
     * @param pullOnGet     Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @return the repository if it could be checked out.
     * @throws GitAPIException      if the repository could not be checked out.
     * @throws GitException         if the same repository is attempted to be cloned multiple times.
     * @throws InvalidPathException if the repository could not be checked out Because it contains unmappable characters.
     */
    public Repository getOrCheckoutRepository(VcsRepositoryUrl sourceRepoUrl, VcsRepositoryUrl targetRepoUrl, Path localPath, boolean pullOnGet)
            throws GitAPIException, GitException, InvalidPathException {
        return getOrCheckoutRepository(sourceRepoUrl, targetRepoUrl, localPath, pullOnGet, defaultBranch);
    }

    /**
     * Get the local repository for a given remote repository URL. If the local repo does not exist yet, it will be checked out.
     *
     * @param sourceRepoUrl The source remote repository.
     * @param targetRepoUrl The target remote repository.
     * @param localPath     The local path to clone the repository to.
     * @param pullOnGet     Pull from the remote on the checked out repository, if it does not need to be cloned.
     * @param defaultBranch The default branch of the target repository
     * @return the repository if it could be checked out.
     * @throws GitAPIException      if the repository could not be checked out.
     * @throws GitException         if the same repository is attempted to be cloned multiple times.
     * @throws InvalidPathException if the repository could not be checked out Because it contains unmappable characters.
     */
    public Repository getOrCheckoutRepository(VcsRepositoryUrl sourceRepoUrl, VcsRepositoryUrl targetRepoUrl, Path localPath, boolean pullOnGet, String defaultBranch)
            throws GitAPIException, GitException, InvalidPathException {

        // First try to just retrieve the git repository from our server, as it might already be checked out.
        // If the sourceRepoUrl differs from the targetRepoUrl, we attempt to clone the source repo into the target directory
        Repository repository = getExistingCheckedOutRepositoryByLocalPath(localPath, targetRepoUrl, defaultBranch);

        // Note: in case the actual git repository in the file system is corrupt (e.g. by accident), we will get an exception here
        // the exception will then delete the folder, so that the next attempt would be successful.
        if (repository != null) {
            if (pullOnGet) {
                pull(repository);
            }
            return repository;
        }
        // If the git repository can't be found on our server, clone it from the remote.
        else {
            waitUntilPathNotBusy(localPath);

            // Clone repository.
            try {
                var gitUriAsString = getGitUriAsString(sourceRepoUrl);
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
                FileUtils.deleteQuietly(localPath.toFile());
                throw new GitException(e);
            }
            finally {
                // make sure that cloneInProgress is released
                cloneInProgressOperations.remove(localPath);
            }
            return getExistingCheckedOutRepositoryByLocalPath(localPath, targetRepoUrl, defaultBranch);
        }
    }

    /**
     * Waits until no clone operation is running for the given path.
     *
     * Retries once a second for up to {@link #JGIT_TIMEOUT_IN_SECONDS} seconds before giving up.
     *
     * @param localPath The path in which a clone operation should be made.
     * @throws CanceledException If the waiting has been interrupted.
     * @throws GitException If the path is still busy after the maximum number of retries.
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
     * @param repositoryUrl the url of the repository
     * @return returns true if the repository is already cached
     */
    public boolean isRepositoryCached(VcsRepositoryUrl repositoryUrl) {
        Path localPath = getLocalPathOfRepo(repoClonePath, repositoryUrl);
        // Check if the repository is already cached in the server's session.
        return cachedRepositories.containsKey(localPath);
    }

    /**
     * Combine all commits of the given repository into one.
     *
     * @param repoUrl of the repository to combine.
     * @throws GitAPIException      If the checkout fails
     */
    public void combineAllCommitsOfRepositoryIntoOne(VcsRepositoryUrl repoUrl) throws GitAPIException {
        Repository exerciseRepository = getOrCheckoutRepository(repoUrl, true);
        combineAllCommitsIntoInitialCommit(exerciseRepository);
    }

    public Path getDefaultLocalPathOfRepo(VcsRepositoryUrl targetUrl) {
        return getLocalPathOfRepo(repoClonePath, targetUrl);
    }

    /**
     * Creates a local path by specifying a target path and the target url
     *
     * @param targetPath target directory
     * @param targetUrl  url of the repository
     * @return path of the local file system
     */
    public Path getLocalPathOfRepo(String targetPath, VcsRepositoryUrl targetUrl) {
        return Path.of(targetPath.replaceAll("^\\." + Pattern.quote(java.io.File.separator), ""), targetUrl.folderNameForRepositoryUrl());
    }

    /**
     * Get an existing git repository that is checked out on the server. Returns immediately null if the localPath does not exist. Will first try to retrieve a cached repository
     * from cachedRepositories. Side effect: This method caches retrieved repositories in a HashMap, so continuous retrievals can be avoided (reduces load).
     *
     * @param localPath           to git repo on server.
     * @param remoteRepositoryUrl the remote repository url for the git repository, will be added to the Repository object for later use, can be null
     * @return the git repository in the localPath or **null** if it does not exist on the server.
     */
    public Repository getExistingCheckedOutRepositoryByLocalPath(@NotNull Path localPath, @Nullable VcsRepositoryUrl remoteRepositoryUrl) {
        return getExistingCheckedOutRepositoryByLocalPath(localPath, remoteRepositoryUrl, defaultBranch);
    }

    /**
     * Get an existing git repository that is checked out on the server. Returns immediately null if the localPath does not exist. Will first try to retrieve a cached repository
     * from cachedRepositories. Side effect: This method caches retrieved repositories in a HashMap, so continuous retrievals can be avoided (reduces load).
     *
     * @param localPath           to git repo on server.
     * @param remoteRepositoryUrl the remote repository url for the git repository, will be added to the Repository object for later use, can be null
     * @param defaultBranch       the name of the branch that should be used as default branch
     * @return the git repository in the localPath or **null** if it does not exist on the server.
     */
    public Repository getExistingCheckedOutRepositoryByLocalPath(@NotNull Path localPath, @Nullable VcsRepositoryUrl remoteRepositoryUrl, String defaultBranch) {
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

            // Open the repository from the filesystem
            final Path gitPath = localPath.resolve(".git");
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            builder.setGitDir(gitPath.toFile()).setInitialBranch(defaultBranch).readEnvironment().findGitDir().setup(); // scan environment GIT_* variables

            // Create the JGit repository object
            Repository repository = new Repository(builder, localPath, remoteRepositoryUrl);
            // disable auto garbage collection because it can lead to problems (especially with deleting local repositories)
            // see https://stackoverflow.com/questions/45266021/java-jgit-files-delete-fails-to-delete-a-file-but-file-delete-succeeds
            // and https://git-scm.com/docs/git-gc for an explanation of the parameter
            StoredConfig gitRepoConfig = repository.getConfig();
            gitRepoConfig.setInt(ConfigConstants.CONFIG_GC_SECTION, null, ConfigConstants.CONFIG_KEY_AUTO, 0);
            gitRepoConfig.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_SYMLINKS, false);
            gitRepoConfig.setString(ConfigConstants.CONFIG_BRANCH_SECTION, defaultBranch, ConfigConstants.CONFIG_REMOTE_SECTION, REMOTE_NAME);
            gitRepoConfig.setString(ConfigConstants.CONFIG_BRANCH_SECTION, defaultBranch, ConfigConstants.CONFIG_MERGE_SECTION, "refs/heads/" + defaultBranch);

            // disable symlinks to avoid security issues such as remote code execution
            gitRepoConfig.save();

            RefUpdate refUpdate = repository.getRefDatabase().newUpdate(Constants.HEAD, false);
            refUpdate.setForceUpdate(true);
            refUpdate.link("refs/heads/" + defaultBranch);

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
            git.commit().setMessage(message).setAllowEmpty(true).setCommitter(artemisGitName, artemisGitEmail).call();
        }
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
            git.commit().setMessage(message).setAllowEmpty(emptyCommit).setCommitter(name, email).call();
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
     * @param targetRepoUrl URI of targets repo
     * @throws GitAPIException if the repo could not be pushed
     */
    public void pushSourceToTargetRepo(Repository targetRepo, VcsRepositoryUrl targetRepoUrl) throws GitAPIException {
        try (Git git = new Git(targetRepo)) {
            // overwrite the old remote uri with the target uri
            git.remoteSetUrl().setRemoteName(REMOTE_NAME).setRemoteUri(new URIish(getGitUriAsString(targetRepoUrl))).call();
            log.debug("pushSourceToTargetRepo -> Push {}", targetRepoUrl.getURI());

            String oldBranch = git.getRepository().getBranch();
            if (!defaultBranch.equals(oldBranch)) {
                git.branchRename().setNewName(defaultBranch).setOldName(oldBranch).call();
            }

            // push the source content to the new remote
            pushCommand(git).call();
        }
        catch (URISyntaxException | IOException e) {
            log.error("Error while pushing to remote target: ", e);
        }
    }

    /**
     * The remote uri of the target repo is still the uri of the source repo.
     * We need to change it to the uri of the target repo.
     * The content to be copied then gets pushed to the new repo.
     *
     * @param targetRepo    Local target repo
     * @param targetRepoUrl URI of targets repo
     * @param oldBranch     default branch that was used when the exercise was created (might differ from the default branch of a participation)
     * @throws GitAPIException if the repo could not be pushed
     */
    public void pushSourceToTargetRepo(Repository targetRepo, VcsRepositoryUrl targetRepoUrl, String oldBranch) throws GitAPIException {
        try (Git git = new Git(targetRepo)) {
            // overwrite the old remote uri with the target uri
            git.remoteSetUrl().setRemoteName(REMOTE_NAME).setRemoteUri(new URIish(getGitUriAsString(targetRepoUrl))).call();
            log.debug("pushSourceToTargetRepo -> Push {}", targetRepoUrl.getURI());

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
     * Change the remote repository url to the currently used authentication mechanism (either ssh or https)
     *
     * @param repo the git repository for which the remote url should be change
     */
    private void setRemoteUrl(Repository repo) {
        if (repo == null || repo.getRemoteRepositoryUrl() == null) {
            log.warn("Cannot set remoteUrl because it is null!");
            return;
        }
        // Note: we reset the remote url, because it might have changed from https to ssh or ssh to https.
        // When using the "localgit" profile it might have also changed because the folder the repository is saved at locally is set as the remote url during cloning.
        try {
            var existingRemoteUrl = repo.getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, REMOTE_NAME, "url");
            var newRemoteUrl = getGitUriAsString(repo.getRemoteRepositoryUrl());
//            if (Arrays.asList(this.environment.getActiveProfiles()).contains("localgit")) {
//                newRemoteUrl = repo.getRemoteRepositoryUrl().toString();
//            }
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
            // flush cache of files
            repo.setContent(null);
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
            // flush cache of files
            repo.setContent(null);
            log.debug("Pull {}", repo.getLocalPath());
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
            log.error("Cannot fetch/hard reset the repo {} with url {} to origin/HEAD due to the following exception", repo.getLocalPath(), repo.getRemoteRepositoryUrl(), ex);
        }
    }

    /**
     * Get last commit hash from HEAD
     *
     * @param repoUrl to get the latest hash from.
     * @return the latestHash of the given repo.
     * @throws EntityNotFoundException if retrieving the latestHash from the git repo failed.
     */
    public ObjectId getLastCommitHash(VcsRepositoryUrl repoUrl) throws EntityNotFoundException {
        if (repoUrl == null || repoUrl.getURI() == null) {
            return null;
        }
        // Get HEAD ref of repo without cloning it locally
        try {
            log.debug("getLastCommitHash {}", repoUrl);
            var headRef = lsRemoteCommand().setRemote(getGitUriAsString(repoUrl)).callAsMap().get(Constants.HEAD);

            if (headRef == null) {
                return null;
            }

            return headRef.getObjectId();
        }
        catch (GitAPIException | URISyntaxException ex) {
            throw new EntityNotFoundException("Could not retrieve the last commit hash for repoUrl " + repoUrl + " due to the following exception: " + ex);
        }
    }

    /**
     * Stager Task #3: Filter late submissions Filter all commits after exercise due date
     *
     * @param repository                Local Repository Object.
     * @param lastValidSubmission       The last valid submission from the database or empty, if not found
     * @param filterLateSubmissionsDate the date after which all submissions should be filtered out (may be null)
     */
    // TODO: remove transactional
    @Transactional(readOnly = true)
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
                // Get last commit before deadline
                Date since = Date.from(Instant.EPOCH);
                Date until = Date.from(filterLateSubmissionsDate.toInstant());
                RevFilter between = CommitTimeRevFilter.between(since, until);
                Iterable<RevCommit> commits = git.log().setRevFilter(between).call();
                RevCommit latestCommitBeforeDeadline = commits.iterator().next();
                commitHash = latestCommitBeforeDeadline.getId().getName();
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
            ObjectId latestHash = getLastCommitHash(programmingExercise.getVcsTemplateRepositoryUrl());

            if (latestHash == null) {
                // Template Repository is somehow empty. Should never happen
                log.debug("Cannot find a commit in the template repo for: {}", repository.getLocalPath());
                return;
            }

            // flush cache of files
            repository.setContent(null);

            // checkout own local "diff" branch to keep main as is
            if (!overwriteMain) {
                studentGit.checkout().setCreateBranch(true).setName("diff").call();
            }

            studentGit.reset().setMode(ResetCommand.ResetType.SOFT).setRef(latestHash.getName()).call();
            studentGit.add().addFilepattern(".").call();
            var optionalStudent = ((StudentParticipation) repository.getParticipation()).getStudents().stream().findFirst();
            var name = optionalStudent.map(User::getName).orElse(artemisGitName);
            var email = optionalStudent.map(User::getEmail).orElse(artemisGitEmail);
            studentGit.commit().setMessage("All student changes in one commit").setCommitter(name, email).call();
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
     * Also removes all remotes since they contain data about the student.
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
            ObjectId latestHash = getLastCommitHash(programmingExercise.getVcsTemplateRepositoryUrl());

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
                    studentGit.commit().setAmend(true).setAuthor(fakeIdent).setCommitter(fakeIdent).setMessage(commit.getFullMessage()).call();
                }
            }
            // Delete copy branch
            studentGit.branchDelete().setBranchNames(copyBranchName).setForce(true).call();

            // Delete all remotes
            for (RemoteConfig remote : studentGit.remoteList().call()) {
                studentGit.remoteRemove().setRemoteName(remote.getName()).call();
                // Manually delete remote tracking branches since JGit apparently fails to do so
                for (Ref ref : studentGit.getRepository().getRefDatabase().getRefs()) {
                    if (ref.getName().startsWith("refs/remotes/" + remote.getName())) {
                        RefUpdate update = studentGit.getRepository().updateRef(ref.getName());
                        update.setForceUpdate(true);
                        update.delete();
                    }
                }
            }

            // Delete .git/logs/ folder to delete git reflogs
            Path logsPath = Path.of(repository.getDirectory().getPath(), "logs");
            FileUtils.deleteDirectory(logsPath.toFile());
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
     * List all files and folders in the repository
     *
     * @param repo Local Repository Object.
     * @return Collection of File objects
     */
    public Map<File, FileType> listFilesAndFolders(Repository repo) {
        // Check if list of files is already cached
        if (repo.getContent() == null) {
            Iterator<java.io.File> itr = FileUtils.iterateFilesAndDirs(repo.getLocalPath().toFile(), HiddenFileFilter.VISIBLE, HiddenFileFilter.VISIBLE);
            Map<File, FileType> files = new HashMap<>();

            while (itr.hasNext()) {
                File nextFile = new File(itr.next(), repo);
                Path nextPath = nextFile.toPath();

                // filter out symlinks
                if (Files.isSymbolicLink(nextPath)) {
                    log.warn("Found a symlink {} in the git repository {}. Do not allow access!", nextPath, repo);
                    continue;
                }

                // Files starting with a '.' are not marked as hidden in Windows. WE must exclude these
                if (nextFile.getName().charAt(0) != '.') {
                    files.put(nextFile, nextFile.isFile() ? FileType.FILE : FileType.FOLDER);
                }
            }

            // TODO: rene: idea: ask in setup to include hidden files? only allow for tutors and instructors?
            // Current problem: .swiftlint.yml gets filtered out
            /*
             * Uncomment to show hidden files // Filter for hidden config files, e.g. '.swiftlint.yml' Iterator<java.io.File> hiddenFiles =
             * FileUtils.iterateFilesAndDirs(repo.getLocalPath().toFile(), HiddenFileFilter.HIDDEN, HiddenFileFilter.HIDDEN); while (hiddenFiles.hasNext()) { File nextFile = new
             * File(hiddenFiles.next(), repo); if (nextFile.isFile() && nextFile.getName().contains(".swiftlint")) { files.put(nextFile, FileType.FILE); } }
             */

            // Cache the list of files
            // Avoid expensive rescanning
            repo.setContent(files);
        }
        return repo.getContent();
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
            Iterator<java.io.File> itr = FileUtils.iterateFiles(repo.getLocalPath().toFile(), HiddenFileFilter.VISIBLE, HiddenFileFilter.VISIBLE);
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
            RevCommit firstCommit = commits.get(commits.size() - 1);
            // If there is a first commit, combine all other commits into it.
            if (firstCommit != null) {
                git.reset().setMode(ResetCommand.ResetType.SOFT).setRef(firstCommit.getId().getName()).call();
                git.add().addFilepattern(".").call();
                git.commit().setAmend(true).setMessage(firstCommit.getFullMessage()).call();
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
            log.error("Could not combine repository {} due to exception: {}", repo, ex);
            throw ex;
        }
    }

    /**
     * Deletes a local repository folder.
     *
     * @param repository Local Repository Object.
     * @throws IOException if the deletion of the repository failed.
     */
    public void deleteLocalRepository(Repository repository) throws IOException {
        Path repoPath = repository.getLocalPath();
        cachedRepositories.remove(repoPath);
        // if repository is not closed, it causes weird IO issues when trying to delete the repository again
        // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
        repository.closeBeforeDelete();
        FileUtils.deleteDirectory(repoPath.toFile());
        repository.setContent(null);
        log.debug("Deleted Repository at {}", repoPath);
    }

    /**
     * Deletes a local repository folder for a repoUrl.
     *
     * @param repoUrl url of the repository.
     */
    public void deleteLocalRepository(VcsRepositoryUrl repoUrl) {
        try {
            if (repoUrl != null && repositoryAlreadyExists(repoUrl)) {
                // We need to close the possibly still open repository otherwise an IOException will be thrown on Windows
                Repository repo = getOrCheckoutRepository(repoUrl, false);
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
     * Zip the content of a git repository that contains a participation.
     *
     * @param repo            Local Repository Object.
     * @param repositoryDir   path where the repo is located on disk
     * @param hideStudentName option to hide the student name for the zip file
     * @return path to zip file.
     * @throws IOException if the zipping process failed.
     */
    public Path zipRepositoryWithParticipation(Repository repo, String repositoryDir, boolean hideStudentName) throws IOException, UncheckedIOException {
        var exercise = repo.getParticipation().getProgrammingExercise();
        var courseShortName = exercise.getCourseViaExerciseGroupOrCourseMember().getShortName();
        var participation = (ProgrammingExerciseStudentParticipation) repo.getParticipation();

        // The zip filename is either the student login, team short name or some default string.
        var studentTeamOrDefault = Objects.requireNonNullElse(participation.getParticipantIdentifier(), "student-submission" + repo.getParticipation().getId());

        String zipRepoName = fileService.removeIllegalCharacters(courseShortName + "-" + exercise.getTitle() + "-" + participation.getId());
        if (hideStudentName) {
            zipRepoName += "-student-submission.git.zip";
        }
        else {
            zipRepoName += "-" + studentTeamOrDefault + ".zip";
        }
        return zipRepository(repo, zipRepoName, repositoryDir, null);
    }

    /**
     * Zips the contents of a git repository, files are filtered according to the contentFilter.
     * Content filtering is added with the intention of optionally excluding ".git" directory from the result.
     *
     * @param repository    The repository
     * @param zipFilename   the name of the zipped file
     * @param repositoryDir path where the repo is located on disk
     * @param contentFilter path filter to exclude some files, can be null to include everything
     * @return path to the zip file
     * @throws IOException if the zipping process failed.
     */
    public Path zipRepository(Repository repository, String zipFilename, String repositoryDir, @Nullable Predicate<Path> contentFilter) throws IOException, UncheckedIOException {
        // Strip slashes from name
        var zipFilenameWithoutSlash = zipFilename.replaceAll("\\s", "");

        if (!zipFilenameWithoutSlash.endsWith(".zip")) {
            zipFilenameWithoutSlash += ".zip";
        }

        Path zipFilePath = Path.of(repositoryDir, zipFilenameWithoutSlash);
        Files.createDirectories(Path.of(repositoryDir));
        return zipFileService.createZipFileWithFolderContent(zipFilePath, repository.getLocalPath(), contentFilter);
    }

    /**
     * Checks if repo was already checked out and is present on disk
     *
     * @param repoUrl URL of the remote repository.
     * @return True if repo exists on disk
     */
    public boolean repositoryAlreadyExists(VcsRepositoryUrl repoUrl) {
        Path localPath = getDefaultLocalPathOfRepo(repoUrl);
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

    private CloneCommand cloneCommand() {
        return authenticate(Git.cloneRepository());
    }

    private FetchCommand fetchCommand(Git git) {
        return authenticate(git.fetch());
    }

    private LsRemoteCommand lsRemoteCommand(Git git) {
        return authenticate(git.lsRemote());
    }

    private LsRemoteCommand lsRemoteCommand() {
        return authenticate(Git.lsRemoteRepository());
    }

    public <C extends GitCommand<?>> C authenticate(TransportCommand<C, ?> command) {
        return command.setTransportConfigCallback(sshCallback);
    }
}
