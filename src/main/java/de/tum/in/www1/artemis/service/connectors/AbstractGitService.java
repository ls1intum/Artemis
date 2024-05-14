package de.tum.in.www1.artemis.service.connectors;

import java.io.ByteArrayOutputStream;
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
import org.eclipse.jgit.transport.sshd.KeyPasswordProvider;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public abstract class AbstractGitService {

    private static final Logger log = LoggerFactory.getLogger(AbstractGitService.class);

    protected TransportConfigCallback sshCallback;

    protected static final int JGIT_TIMEOUT_IN_SECONDS = 5;

    protected static final String REMOTE_NAME = "origin";

    protected AbstractGitService() {
        log.debug("file.encoding={}", Charset.defaultCharset().displayName());
        log.debug("sun.jnu.encoding={}", System.getProperty("sun.jnu.encoding"));
        log.debug("Default Charset={}", Charset.defaultCharset());
        log.debug("Default Charset in Use={}", new OutputStreamWriter(new ByteArrayOutputStream()).getEncoding());
    }

    protected static TransportConfigCallback getSshCallback(SshdSessionFactory sshSessionFactory) {
        return transport -> {
            if (transport instanceof SshTransport sshTransport) {
                transport.setTimeout(JGIT_TIMEOUT_IN_SECONDS);
                sshTransport.setSshSessionFactory(sshSessionFactory);
            }
            else {
                log.error("Cannot use ssh properly because of mismatch of Jgit transport object: {}", transport);
            }
        };
    }

    protected static SshdSessionFactoryBuilder getSshdSessionFactoryBuilder(Optional<String> gitSshPrivateKeyPath, Optional<String> gitSshPrivateKeyPassphrase, URL gitUrl) {
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

        return new SshdSessionFactoryBuilder().setKeyPasswordProvider(keyPasswordProvider -> new KeyPasswordProvider() {

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
        }).setSshDirectory(new java.io.File(gitSshPrivateKeyPath.orElseThrow())).setHomeDirectory(new java.io.File(System.getProperty("user.home")));
    }

    /**
     * Creates a new Repository with the given parameters and saves the Repository's StoredConfig.
     *
     * @param localPath           The local path of the repository.
     * @param remoteRepositoryUri The remote repository uri for the git repository.
     * @param defaultBranch       The default branch of the repository.
     * @param builder             The FileRepositoryBuilder.
     * @return The created Repository.
     * @throws IOException if the configuration file cannot be accessed.
     */
    @NotNull
    protected static Repository createRepository(Path localPath, VcsRepositoryUri remoteRepositoryUri, String defaultBranch, FileRepositoryBuilder builder) throws IOException {
        // Create the JGit repository object
        Repository repository = new Repository(builder, localPath, remoteRepositoryUri);
        // disable auto garbage collection because it can lead to problems (especially with deleting local repositories)
        // see https://stackoverflow.com/questions/45266021/java-jgit-files-delete-fails-to-delete-a-file-but-file-delete-succeeds
        // and https://git-scm.com/docs/git-gc for an explanation of the parameter
        // and https://www.eclipse.org/lists/jgit-dev/msg03734.html
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

        gitRepoConfig.save();
        return repository;
    }

    @NotNull
    protected static Repository openRepositoryFromFileSystem(Path localPath, VcsRepositoryUri remoteRepositoryUri, String defaultBranch)
            throws IOException, InvalidRefNameException {
        final Path gitPath = localPath.resolve(".git");
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.setGitDir(gitPath.toFile()).setInitialBranch(defaultBranch).readEnvironment().findGitDir().setup(); // scan environment GIT_* variables

        Repository repository = createRepository(localPath, remoteRepositoryUri, defaultBranch, builder);

        RefUpdate refUpdate = repository.getRefDatabase().newUpdate(Constants.HEAD, false);
        refUpdate.setForceUpdate(true);
        refUpdate.link("refs/heads/" + defaultBranch);
        return repository;
    }

    /**
     * Get last commit hash from HEAD
     *
     * @param repoUri to get the latest hash from.
     * @return the latestHash of the given repo.
     * @throws EntityNotFoundException if retrieving the latestHash from the git repo failed.
     */
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

    protected abstract String getGitUriAsString(VcsRepositoryUri repoUri) throws URISyntaxException;

    private LsRemoteCommand lsRemoteCommand() {
        return authenticate(Git.lsRemoteRepository());
    }

    protected <C extends GitCommand<?>> C authenticate(TransportCommand<C, ?> command) {
        return command.setTransportConfigCallback(sshCallback);
    }

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
        repository.setContent(null);
        log.debug("Deleted Repository at {}", repoPath);
    }
}
