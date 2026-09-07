package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.sshd.git.GitLocationResolver;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.Repository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.localci.service.BuildJobCloneTokenService;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCForbiddenException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCInternalException;
import de.tum.cit.aet.artemis.localvc.service.ssh.SshConstants;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

@Profile(PROFILE_LOCALVC)
@Lazy
@Service
public class SshGitLocationResolverService implements GitLocationResolver {

    private static final Logger log = LoggerFactory.getLogger(SshGitLocationResolverService.class);

    @Value("${artemis.version-control.url}")
    private URI localVCBaseUri;

    private final LocalVCServletService localVCServletService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    // Optional: a node running LocalVC with Jenkins has no local CI and therefore no build jobs
    private final Optional<DistributedDataAccessService> distributedDataAccessService;

    private final Optional<BuildJobCloneTokenService> buildJobCloneTokenService;

    public SshGitLocationResolverService(LocalVCServletService localVCServletService, ProgrammingExerciseRepository programmingExerciseRepository,
            Optional<DistributedDataAccessService> distributedDataAccessService, Optional<BuildJobCloneTokenService> buildJobCloneTokenService) {
        this.localVCServletService = localVCServletService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.distributedDataAccessService = distributedDataAccessService;
        this.buildJobCloneTokenService = buildJobCloneTokenService;
    }

    @Override
    public Path resolveRootDirectory(String command, String[] args, ServerSession session, FileSystem fs) throws IOException {
        // Note: we need to double check read / write access based

        String repositoryPath = args[1];
        // We need to remove the '/git/' in the beginning
        if (repositoryPath.startsWith("/git/")) {
            repositoryPath = repositoryPath.substring(5);
        }

        final var gitCommand = args[0];
        final var localVCRepositoryUri = new LocalVCRepositoryUri(localVCBaseUri, Path.of(repositoryPath));
        final var projectKey = localVCRepositoryUri.getProjectKey();
        final var repositoryTypeOrUserName = localVCRepositoryUri.getRepositoryTypeOrUserName();
        ProgrammingExercise exercise;

        try {
            exercise = programmingExerciseRepository.findOneByProjectKeyOrThrow(projectKey, true);
        }
        catch (EntityNotFoundException e) {
            throw new LocalVCInternalException("Could not find single programming exercise with project key " + projectKey, e);
        }

        // git-upload-pack means fetch (read operation), git-receive-pack means push (write operation)
        final var repositoryAction = gitCommand.equals("git-upload-pack") ? RepositoryActionType.READ : gitCommand.equals("git-receive-pack") ? RepositoryActionType.WRITE : null;
        final var user = session.getAttribute(SshConstants.USER_KEY);
        session.setAttribute(SshConstants.REPOSITORY_EXERCISE_KEY, exercise);

        if (session.getAttribute(SshConstants.IS_BUILD_AGENT_KEY) && repositoryAction == RepositoryActionType.READ) {
            // The key already proved which build agent this is, and its origin was checked at authentication time.
            // What remains is scope: an agent may read the repositories of the build jobs it is actually running, not
            // every repository in the installation. The processing list is the authority and needs no expiry of its
            // own, because a job leaves it when it finishes, is cancelled, or hits the build timeout.
            String agentName = session.getAttribute(SshConstants.BUILD_AGENT_NAME_KEY);
            var buildJob = findBuildJobForRepository(agentName, localVCRepositoryUri);
            if (buildJob.isEmpty()) {
                log.warn("Build agent {} tried to read {}, which belongs to none of the build jobs it is currently running", agentName, localVCRepositoryUri);
                throw new AccessDeniedException("This repository does not belong to a build job of this build agent");
            }
            // Audited like the https path, and for the same reason: an agent reads student code, so the read has to be
            // attributable. This branch returns before the user authorization below, which is what writes the log for
            // everyone else, and a build agent session carries no user to attribute an entry to anyway.
            localVCServletService.saveBuildAgentVcsAccessLog(localVCRepositoryUri, agentName, buildJob.get().id(), hostOf(session.getClientAddress()), AuthenticationMechanism.SSH);
        }
        else {
            try {
                var participation = localVCServletService.authorizeUser(repositoryTypeOrUserName, user, exercise, repositoryAction, localVCRepositoryUri, true);
                localVCServletService.cacheAttributesInSshSession(user, participation, repositoryAction, AuthenticationMechanism.SSH, session.getClientAddress().toString(),
                        localVCRepositoryUri, session);
            }
            catch (LocalVCForbiddenException e) {
                log.error("User {} does not have access to the repository {}", user.getLogin(), repositoryPath);
                localVCServletService.saveFailedAccessVcsAccessLog(new AuthenticationContext.Session(session), repositoryTypeOrUserName, exercise, localVCRepositoryUri, user,
                        repositoryAction);
                throw new AccessDeniedException("User does not have access to this repository", e);
            }
        }

        // we cannot trust unvalidated user input
        final var localRepositoryPath = localVCRepositoryUri.getRelativeRepositoryPath().toString();
        try (Repository repo = localVCServletService.resolveRepository(localRepositoryPath)) {
            return repo.getDirectory().toPath();
        }
    }

    /**
     * Checks whether a repository belongs to one of the build jobs the given agent is currently processing.
     * <p>
     * This is the ssh counterpart of the per-build-job clone token used over https. No token is needed here, because
     * the public key already established which agent is connected: the missing constraint was only which repositories
     * that agent legitimately needs right now, and the processing list answers exactly that.
     *
     * Returns the job rather than a boolean, because the access log entry has to name which build job the read belongs
     * to. A yes/no answer would leave the audit entry unable to say more than "some agent read this".
     *
     * @param agentName            the short name of the authenticated build agent, may be null on a session that
     *                                 predates this attribute being set
     * @param localVCRepositoryUri the repository the agent is asking to read
     * @return the agent's running build job that declares this repository, or empty if none does
     */
    private Optional<BuildJobQueueItem> findBuildJobForRepository(String agentName, LocalVCRepositoryUri localVCRepositoryUri) {
        if (agentName == null || distributedDataAccessService.isEmpty() || buildJobCloneTokenService.isEmpty()) {
            return Optional.empty();
        }
        var tokenService = buildJobCloneTokenService.get();
        return distributedDataAccessService.get().getProcessingJobsForAgentByName(agentName).stream()
                .filter(buildJob -> tokenService.coversRepository(buildJob, localVCRepositoryUri)).findFirst();
    }

    /**
     * @param address the ssh client address, already the real client where the proxy protocol is in use
     * @return the address as a plain host string for the access log, or null if it is not an ip socket
     */
    @Nullable
    private static String hostOf(@Nullable SocketAddress address) {
        if (address instanceof InetSocketAddress inetSocketAddress && inetSocketAddress.getAddress() != null) {
            return inetSocketAddress.getAddress().getHostAddress();
        }
        return null;
    }
}
