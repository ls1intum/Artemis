package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshConfigStore;
import org.eclipse.jgit.transport.SshConstants;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.GitException;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.service.AbstractGitService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

@Profile(PROFILE_BUILDAGENT)
@Lazy(false)
@Service
public class BuildJobGitService extends AbstractGitService {

    private static final Logger log = LoggerFactory.getLogger(BuildJobGitService.class);

    @Value("${artemis.version-control.build-agent-git-username}")
    private String buildAgentGitUsername;

    @Value("${artemis.version-control.build-agent-git-password}")
    private String buildAgentGitPassword;

    @Value("${artemis.version-control.build-agent-use-ssh:false}")
    private boolean useSshForBuildAgent;

    @Value("${artemis.version-control.ssh-private-key-folder-path:#{null}}")
    private Optional<String> gitSshPrivateKeyPath;

    @Value("${artemis.version-control.ssh-template-clone-url:#{null}}")
    private Optional<String> sshUrlTemplate;

    private CredentialsProvider credentialsProvider;

    private JGitKeyCache jgitKeyCache;

    protected TransportConfigCallback sshCallback;

    private SshdSessionFactory sshdSessionFactory;

    /**
     * initialize the BuildJobGitService, in particular which authentication mechanism should be used
     * Artemis uses the following order for authentication:
     * 1. ssh key (if available)
     * 2. username + personal access token (if available)
     * 3. username + password
     * EventListener cannot be used here, as the bean is lazy
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     */
    @PostConstruct
    public void init() {
        if (useSsh()) {
            log.info("BuildJobGitService will use ssh keys as authentication method to interact with remote git repositories.");
            if (sshUrlTemplate.isEmpty()) {
                throw new RuntimeException("No SSH url template was set but should use SSH for build agent authentication.");
            }
            if (gitSshPrivateKeyPath.isEmpty()) {
                throw new RuntimeException("No SSH private key folder was set but should use SSH for build agent authentication.");
            }
            configureSsh();
        }
    }

    protected boolean useSsh() {
        return useSshForBuildAgent;
    }

    /**
     * Configures the SSH settings for the JGit SSH session factory.
     */
    protected void configureSsh() {
        CredentialsProvider.setDefault(new CustomCredentialsProvider());
        final var sshSessionFactoryBuilder = getSshdSessionFactoryBuilder(gitSshPrivateKeyPath, localVCBaseUri);
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

    protected static SshdSessionFactoryBuilder getSshdSessionFactoryBuilder(Optional<String> gitSshPrivateKeyPath, URI gitUri) {
        // @formatter:off
        return new SshdSessionFactoryBuilder()
            .setConfigStoreFactory((homeDir, configFile, localUserName) -> new CustomSshConfigStore(gitUri))
            .setSshDirectory(Path.of(gitSshPrivateKeyPath.orElseThrow()).toFile())
            .setHomeDirectory(Path.of(System.getProperty("user.home")).toFile());
        // @formatter:on
    }

    @PreDestroy
    public void cleanup() {
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

    record CustomSshConfigStore(URI gitUri) implements SshConfigStore {

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
                    if (hostName.equals(gitUri.getHost())) {
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

    /**
     * Get the URI for a {@link LocalVCRepositoryUri}. This either retrieves the SSH URI, if SSH is used, the HTTP(S) URI, or the path to the repository's folder if the local VCS
     * is
     * used.
     * This method is for internal use (getting the URI for cloning the repository into the Artemis file system).
     * For the local VCS however, the repository is cloned from the folder defined in the environment variable "artemis.version-control.local-vcs-repo-path".
     *
     * @param localVCRepositoryUri the {@link LocalVCRepositoryUri} for which to get the URI
     * @return the URI (SSH, HTTP(S), or local path)
     * @throws URISyntaxException if SSH is used and the SSH URI could not be retrieved.
     */
    @Override
    protected URI getGitUri(LocalVCRepositoryUri localVCRepositoryUri) throws URISyntaxException {
        return useSsh() ? getSshUri(localVCRepositoryUri, sshUrlTemplate) : localVCRepositoryUri.getURI();
    }

    /**
     * Checkout at the given repository at the given commit hash
     *
     * @param repository the repository to check out the commit in
     * @param commitHash the hash of the commit to check out
     */
    public void checkoutRepositoryAtCommit(Repository repository, String commitHash) {
        try (Git git = new Git(repository)) {
            git.checkout().setName(commitHash).call();
        }
        catch (GitAPIException e) {
            throw new GitException("Could not checkout commit " + commitHash + " in repository located at  " + repository.getLocalPath(), e);
        }
    }

    /**
     * Clone a repository from the given URI to the given local path
     *
     * @param repoUri   the URI of the repository to clone
     * @param localPath the local path to clone the repository to
     * @return the cloned repository
     * @throws GitAPIException      if the repository could not be cloned
     * @throws GitException         if the repository could not be cloned
     * @throws InvalidPathException if the local path is invalid
     * @throws IOException          if the local path could not be deleted
     * @throws URISyntaxException   if the URI is invalid
     */
    public Repository cloneRepository(LocalVCRepositoryUri repoUri, Path localPath) throws GitAPIException, GitException, InvalidPathException, IOException, URISyntaxException {
        var gitUriAsString = getGitUriAsString(repoUri);
        log.debug("Cloning from {} to {}", gitUriAsString, localPath);
        // make sure the directory to copy into is empty (the operation only executes a delete if the directory exists)
        FileUtils.deleteDirectory(localPath.toFile());
        CloneCommand cloneCommand = cloneCommand().setURI(gitUriAsString).setDirectory(localPath.toFile());
        try (Git ignored = cloneCommand.call()) {
            return getExistingCheckedOutRepositoryByLocalPath(localPath, repoUri, defaultBranch);
        }
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
    public Repository getExistingCheckedOutRepositoryByLocalPath(@NotNull Path localPath, @Nullable LocalVCRepositoryUri remoteRepositoryUri, String defaultBranch) {
        try {
            return openCheckedOutRepositoryFromFileSystem(localPath, remoteRepositoryUri, defaultBranch);
        }
        catch (IOException | InvalidRefNameException ex) {
            log.warn("Cannot get existing checkout out repository by local path: {}", ex.getMessage());
            return null;
        }
    }

    protected <C extends GitCommand<?>> C authenticate(TransportCommand<C, ?> command) {
        if (useSsh()) {
            return command.setTransportConfigCallback(sshCallback);
        }
        else {
            return command.setCredentialsProvider(getCachedCredentialsProvider());
        }
    }

    private CredentialsProvider getCachedCredentialsProvider() {
        if (credentialsProvider == null) {
            credentialsProvider = new UsernamePasswordCredentialsProvider(buildAgentGitUsername, buildAgentGitPassword);
        }
        return credentialsProvider;
    }

    @Override
    protected LsRemoteCommand lsRemoteCommand() {
        return authenticate(Git.lsRemoteRepository());
    }

    private CloneCommand cloneCommand() {
        return authenticate(Git.cloneRepository());
    }
}
