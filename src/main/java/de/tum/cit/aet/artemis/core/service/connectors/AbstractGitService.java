package de.tum.cit.aet.artemis.core.service.connectors;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshConfigStore;
import org.eclipse.jgit.transport.SshConstants;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.KeyPasswordProvider;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;

public abstract class AbstractGitService {

    private static final Logger log = LoggerFactory.getLogger(AbstractGitService.class);

    private JGitKeyCache jgitKeyCache;

    protected TransportConfigCallback sshCallback;

    private SshdSessionFactory sshdSessionFactory;

    protected static final int JGIT_TIMEOUT_IN_SECONDS = 5;

    protected static final String REMOTE_NAME = "origin";

    @Value("${artemis.version-control.url}")
    protected URL gitUrl;

    @Value("${artemis.version-control.token:#{null}}")
    protected Optional<String> gitToken;

    @Value("${artemis.version-control.ssh-private-key-folder-path:#{null}}")
    protected Optional<String> gitSshPrivateKeyPath;

    @Value("${artemis.version-control.ssh-private-key-password:#{null}}")
    protected Optional<String> gitSshPrivateKeyPassphrase;

    @Value("${artemis.version-control.ssh-template-clone-url:#{null}}")
    protected Optional<String> sshUrlTemplate;

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

    protected AbstractGitService() {
        log.debug("file.encoding={}", Charset.defaultCharset().displayName());
        log.debug("sun.jnu.encoding={}", System.getProperty("sun.jnu.encoding"));
        log.debug("Default Charset={}", Charset.defaultCharset());
        log.debug("Default Charset in Use={}", new OutputStreamWriter(new ByteArrayOutputStream()).getEncoding());
    }

    protected boolean useSsh() {
        return gitSshPrivateKeyPath.isPresent() && sshUrlTemplate.isPresent();
        // password is optional and will only be applied if the ssh private key was encrypted using a password
    }

    /**
     * Configures the SSH settings for the JGit SSH session factory.
     */
    protected void configureSsh() {
        CredentialsProvider.setDefault(new CustomCredentialsProvider());
        final var sshSessionFactoryBuilder = getSshdSessionFactoryBuilder(gitSshPrivateKeyPath, gitSshPrivateKeyPassphrase, gitUrl);
        jgitKeyCache = new JGitKeyCache();
        sshdSessionFactory = sshSessionFactoryBuilder.build(jgitKeyCache);
        sshCallback = transport -> {
            if (transport instanceof SshTransport sshTransport) {
                transport.setTimeout(JGIT_TIMEOUT_IN_SECONDS);
                sshTransport.setSshSessionFactory(sshdSessionFactory);
            }
            else {
                log.error("Cannot use ssh properly because of mismatch of Jgit transport object: {}", transport);
            }
        };
    }

    protected static SshdSessionFactoryBuilder getSshdSessionFactoryBuilder(Optional<String> gitSshPrivateKeyPath, Optional<String> gitSshPrivateKeyPassphrase, URL gitUrl) {
        // @formatter:off
        return new SshdSessionFactoryBuilder()
            .setKeyPasswordProvider(keyPasswordProvider -> new CustomKeyPasswordProvider(gitSshPrivateKeyPath, gitSshPrivateKeyPassphrase))
            .setConfigStoreFactory((homeDir, configFile, localUserName) -> new CustomSshConfigStore(gitUrl))
            .setSshDirectory(new File(gitSshPrivateKeyPath.orElseThrow()))
            .setHomeDirectory(new java.io.File(System.getProperty("user.home")));
            // @formatter:on
    }

    /**
     * Creates a JGit repository instance using the provided local path and remote repository URI with specific configurations.
     * This method sets up the repository to avoid auto garbage collection and disables the use of symbolic links to enhance security.
     * It also makes sure that the default branch and HEAD are correctly configured.
     * Works for both, local checkout repositories and bare repositories (without working directory).
     *
     * @param localPath           The path to the local repository directory, not null.
     * @param remoteRepositoryUri The URI of the remote repository, not null.
     * @param defaultBranch       The name of the default branch to be checked out, not null.
     * @param isBare              Whether the repository is a bare repository (without working directory)
     * @return The configured Repository instance.
     * @throws IOException             If an I/O error occurs during repository initialization or configuration.
     * @throws InvalidRefNameException If the provided default branch name is invalid.
     *
     *                                     <p>
     *                                     The method disables automatic garbage collection of the repository to prevent potential issues with file deletion
     *                                     as discussed in multiple resources. It also avoids the use of symbolic links for security reasons,
     *                                     following best practices against remote code execution vulnerabilities.
     *                                     </p>
     *
     *                                     <p>
     *                                     References:
     *                                     <ul>
     *                                     <li>https://stackoverflow.com/questions/45266021/java-jgit-files-delete-fails-to-delete-a-file-but-file-delete-succeeds</li>
     *                                     <li>https://git-scm.com/docs/git-gc</li>
     *                                     <li>https://www.eclipse.org/lists/jgit-dev/msg03734.html</li>
     *                                     </ul>
     *                                     </p>
     */
    @NotNull
    public static Repository linkRepositoryForExistingGit(Path localPath, VcsRepositoryUri remoteRepositoryUri, String defaultBranch, boolean isBare)
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

            return repository;
        }
    }

    @NotNull
    protected static Repository openCheckedOutRepositoryFromFileSystem(Path localPath, VcsRepositoryUri remoteRepositoryUri, String defaultBranch)
            throws IOException, InvalidRefNameException {

        return linkRepositoryForExistingGit(localPath, remoteRepositoryUri, defaultBranch, false);
    }

    /**
     * Get last commit hash from HEAD
     *
     * @param repoUri to get the latest hash from.
     * @return the latestHash of the given repo.
     * @throws EntityNotFoundException if retrieving the latestHash from the git repo failed.
     */
    @Nullable
    public ObjectId getLastCommitHash(VcsRepositoryUri repoUri) throws EntityNotFoundException {
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

    protected String getGitUriAsString(VcsRepositoryUri vcsRepositoryUri) throws URISyntaxException {
        return getGitUri(vcsRepositoryUri).toString();
    }

    protected abstract URI getGitUri(VcsRepositoryUri vcsRepositoryUri) throws URISyntaxException;

    private LsRemoteCommand lsRemoteCommand() {
        return authenticate(Git.lsRemoteRepository());
    }

    protected abstract <C extends GitCommand<?>> C authenticate(TransportCommand<C, ?> command);

    protected CloneCommand cloneCommand() {
        return authenticate(Git.cloneRepository());
    }

    protected static URI getSshUri(VcsRepositoryUri vcsRepositoryUri, Optional<String> sshUrlTemplate) throws URISyntaxException {
        URI templateUri = new URI(sshUrlTemplate.orElseThrow());
        // Example Gitlab: ssh://git@gitlab.ase.in.tum.de:2222/se2021w07h02/se2021w07h02-ga27yox.git
        final var repositoryUri = vcsRepositoryUri.getURI();
        final var path = repositoryUri.getPath().replace("/scm", "");
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

    protected void cleanup() {
        if (useSsh()) {
            jgitKeyCache.close();
            sshdSessionFactory.close();
        }
    }

    static class CustomCredentialsProvider extends CredentialsProvider {

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
    }

    static class CustomKeyPasswordProvider implements KeyPasswordProvider {

        Optional<String> gitSshPrivateKeyPath;

        Optional<String> gitSshPrivateKeyPassphrase;

        public CustomKeyPasswordProvider(Optional<String> gitSshPrivateKeyPath, Optional<String> gitSshPrivateKeyPassphrase) {
            this.gitSshPrivateKeyPath = gitSshPrivateKeyPath;
            this.gitSshPrivateKeyPassphrase = gitSshPrivateKeyPassphrase;
        }

        @Override
        public char[] getPassphrase(URIish uri, int attempt) {
            // Example: /Users/artemis/.ssh/artemis/id_rsa contains /Users/artemis/.ssh/artemis
            if (gitSshPrivateKeyPath.isPresent() && gitSshPrivateKeyPassphrase.isPresent() && uri.getPath().contains(gitSshPrivateKeyPath.get())) {
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
    }

    static class CustomSshConfigStore implements SshConfigStore {

        URL gitUrl;

        public CustomSshConfigStore(URL gitUrl) {
            this.gitUrl = gitUrl;
        }

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
    }
}
