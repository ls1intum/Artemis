package de.tum.cit.aet.artemis.service.connectors.localci.buildagent;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.GitException;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.service.connectors.AbstractGitService;

@Profile(PROFILE_BUILDAGENT)
@Service
public class BuildJobGitService extends AbstractGitService {

    private static final Logger log = LoggerFactory.getLogger(BuildJobGitService.class);

    @Value("${artemis.version-control.build-agent-git-username}")
    private String buildAgentGitUsername;

    @Value("${artemis.version-control.build-agent-git-password}")
    private String buildAgentGitPassword;

    private CredentialsProvider credentialsProvider;

    /**
     * initialize the BuildJobGitService, in particular which authentication mechanism should be used
     * Artemis uses the following order for authentication:
     * 1. ssh key (if available)
     * 2. username + personal access token (if available)
     * 3. username + password
     */
    @PostConstruct
    public void init() {
        if (useSsh()) {
            log.info("BuildJobGitService will use ssh keys as authentication method to interact with remote git repositories");
            configureSsh();
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
        return useSsh() ? getSshUri(vcsRepositoryUri, sshUrlTemplate) : vcsRepositoryUri.getURI();
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
    public Repository cloneRepository(VcsRepositoryUri repoUri, Path localPath) throws GitAPIException, GitException, InvalidPathException, IOException, URISyntaxException {
        var gitUriAsString = getGitUriAsString(repoUri);
        log.debug("Cloning from {} to {}", gitUriAsString, localPath);
        // make sure the directory to copy into is empty (the operation only executes a delete if the directory exists)
        FileUtils.deleteDirectory(localPath.toFile());
        Git git = null;
        try {
            CloneCommand cloneCommand = cloneCommand().setURI(gitUriAsString).setDirectory(localPath.toFile());
            git = cloneCommand.call();
            return getExistingCheckedOutRepositoryByLocalPath(localPath, repoUri, defaultBranch);
        }
        finally {
            if (git != null) {
                git.close();
            }
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
    public Repository getExistingCheckedOutRepositoryByLocalPath(@NotNull Path localPath, @Nullable VcsRepositoryUri remoteRepositoryUri, String defaultBranch) {
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
}
