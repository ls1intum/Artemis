package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static de.tum.cit.aet.artemis.core.util.HttpRequestUtils.getPeerIpString;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCCredentials.extractUsernameAndPassword;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCCredentials.secretMatches;

import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkPolicy;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.localci.service.BuildAgentAddressRegistryService;
import de.tum.cit.aet.artemis.localci.service.BuildJobCloneTokenService;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCInternalException;
import de.tum.cit.aet.artemis.localvc.service.LocalVCCredentials.UsernameAndPassword;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

/**
 * Recognises the git requests that build agents and CI systems make to clone for a build, and records them in the access log.
 * <p>
 * Local CI build agents authenticate with a token that is scoped to one running build job, or with an ssh key. A local VC node without local CI, whose client is Jenkins,
 * accepts one credential that is shared by the whole installation.
 */
@Lazy
@Service
@Profile(PROFILE_LOCALVC)
public class LocalVCBuildAgentAuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(LocalVCBuildAgentAuthenticationService.class);

    public static final String BUILD_USER_NAME = "buildjob_user";

    /**
     * Marks a request that was authorized as a build agent cloning for one of its build jobs. Set once the credential
     * has been accepted, so later stages can recognise build agent traffic without guessing from the username.
     */
    static final String BUILD_AGENT_CLONE_REQUEST_ATTRIBUTE = "artemis.buildAgentClone";

    // Optional: a node running LocalVC with Jenkins has no local CI, so it has neither build jobs nor a registry
    private final Optional<DistributedDataAccessService> distributedDataAccessService;

    private final Optional<BuildAgentAddressRegistryService> buildAgentAddressRegistryService;

    private final Optional<BuildJobCloneTokenService> buildJobCloneTokenService;

    private final BuildAgentNetworkPolicy buildAgentNetworkPolicy;

    private final RateLimitService rateLimitService;

    private final LocalVCRepositoryResolverService repositoryResolver;

    // TODO As soon as only LocalVC is supported, this Optional can be removed
    private final Optional<VcsAccessLogService> vcsAccessLogService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    // Optional on purpose: an installation whose build agents authenticate with an ssh key never uses this credential
    // pair, and then must not have to configure one. Every read of these fields is guarded by StringUtils.hasText, and
    // LocalVCBuildAgentCredentialsValidator fails startup when the https case leaves them unset.
    @Value("${artemis.version-control.build-agent-git-username:}")
    private String buildAgentGitUsername;

    @Value("${artemis.version-control.build-agent-git-password:}")
    private String buildAgentGitPassword;

    /**
     * Whether the build agents of this installation clone over ssh, using the key pair they generate at startup and
     * publish to the core nodes, rather than over https with {@code build-agent-git-username} and
     * {@code build-agent-git-password}. The two mechanisms are alternatives, not a fallback chain: a build agent picks
     * exactly one in {@code BuildJobGitService.authenticate}, so when ssh is configured, this node stops honouring the
     * shortcut below rather than leaving a second repository-wide read path open that nothing uses. The credentials are
     * still processed as ordinary Basic credentials afterwards, which grants only whatever the named account may access.
     * <p>
     * This closes the https door only. {@code GitPublickeyAuthenticatorService} keeps authenticating a registered build
     * agent by its public key whatever this property says, deliberately: a key is per-agent and reaches this node only
     * through an agent that has joined the cluster, so there is no shared secret to withdraw, and agents can be moved to
     * ssh one at a time before the core nodes follow.
     */
    @Value("${artemis.version-control.build-agent-use-ssh:false}")
    private boolean useSshForBuildAgent;

    public LocalVCBuildAgentAuthenticationService(Optional<DistributedDataAccessService> distributedDataAccessService,
            Optional<BuildAgentAddressRegistryService> buildAgentAddressRegistryService, Optional<BuildJobCloneTokenService> buildJobCloneTokenService,
            BuildAgentNetworkPolicy buildAgentNetworkPolicy, RateLimitService rateLimitService, LocalVCRepositoryResolverService repositoryResolver,
            Optional<VcsAccessLogService> vcsAccessLogService, ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            ProgrammingExerciseRepository programmingExerciseRepository) {
        this.distributedDataAccessService = distributedDataAccessService;
        this.buildAgentAddressRegistryService = buildAgentAddressRegistryService;
        this.buildJobCloneTokenService = buildJobCloneTokenService;
        this.buildAgentNetworkPolicy = buildAgentNetworkPolicy;
        this.rateLimitService = rateLimitService;
        this.repositoryResolver = repositoryResolver;
        this.vcsAccessLogService = vcsAccessLogService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Decides whether a fetch is a build agent cloning a repository of a build job it is currently running.
     * <p>
     * Replaces the installation-wide build agent credential on the https path with something bounded on three axes:
     * the caller must be a registered agent connected from the address it is calling from, must present the token of a
     * job that agent currently holds, and may only read the repositories that job declares. Nothing here is time
     * based - a job leaves the processing list when it finishes, is cancelled or hits the build timeout, and the token
     * stops working at that moment.
     * <p>
     * The username is the agent's short name, which is an identifier and not a credential: it is the Hazelcast client
     * name, the key of the build agent information map, and is shown in the admin UI. It selects whose jobs and whose
     * addresses to check; the token is what authenticates.
     * <p>
     * Every failure falls through to normal user authentication rather than rejecting, because a short name could
     * collide with a real login and that person must still be able to use their own credentials.
     *
     * @param request             the incoming git request
     * @param authorizationHeader the Basic authorization header of that request
     * @return whether the request is an authorized build agent clone
     */
    public boolean authenticateBuildJobCloneToken(HttpServletRequest request, String authorizationHeader) {
        if (distributedDataAccessService.isEmpty() || buildAgentAddressRegistryService.isEmpty() || buildJobCloneTokenService.isEmpty()) {
            // No local CI on this node, so there are no build jobs and nothing can present a valid token
            return false;
        }

        try {
            UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);
            String agentName = usernameAndPassword.username();
            String presentedToken = usernameAndPassword.password();
            if (!StringUtils.hasText(agentName) || !StringUtils.hasText(presentedToken)) {
                return false;
            }

            // Cheapest possible gates first, and deliberately so. This method runs for every read request that carries
            // any Basic header, ahead of the rate limiter, so anything expensive here is reachable by an unauthenticated
            // caller in a loop - and on the ordinary student clone it is pure overhead that must stay off the hot path.
            //
            // The prefix is a purely local check and rejects every credential that is not a clone token at all: a
            // password, a vcpat- access token, an ssh key request. Every token this installation mints carries it, so
            // nothing that could match is turned away, and the prefix is not a secret - it exists to make a credential
            // recognisable in a log. Only past it is a single-key lookup performed to reject a username that is not a
            // build agent, and only past that may the whole-map reads below run.
            if (!presentedToken.startsWith(BuildJobCloneTokenService.CLONE_TOKEN_PREFIX)) {
                return false;
            }
            if (distributedDataAccessService.get().getDistributedBuildAgentInformation().get(agentName) == null) {
                return false;
            }

            // The allowlist is pure local state and costs nothing, so it stays here: a caller outside the configured
            // build agent networks is rejected before any of the work below.
            String peerIpAddress = getPeerIpString(request, buildAgentNetworkPolicy::isTrustedProxy);
            if (!buildAgentNetworkPolicy.isWithinAllowedRanges(peerIpAddress)) {
                log.warn("Rejecting a build agent clone for agent {} from {}, which is outside the configured build agent networks", agentName, peerIpAddress);
                return false;
            }

            // Last gate before the first expensive read, and the only one that bounds repetition. Everything above is
            // O(1) local or single-key work; getProcessingJobsForAgentByName below reads the whole distributed
            // processing job map and deserializes every entry to filter it. A caller inside the build agent networks
            // who knows a registered agent name passes both cheap gates with any password at all, so without this the
            // scan is reachable in a loop by a caller that ordinary authentication would already be throttling.
            //
            // Two things keep the limit off legitimate agents, so it can be sized for guessing rather than for build
            // throughput. An address some agent is registered at skips it entirely, which follows the agents around
            // without an operator maintaining a list. And a check that succeeds spends nothing: only a decline does,
            // below. That covers the agents with no registration to go by - one sharing a JVM with a core node has no
            // observable connection - whose clone rate is otherwise the highest of all.
            //
            // Over the limit falls through rather than rejecting, matching the contract documented above: this method
            // never rejects a request, it only declines to treat it as a build agent clone. The request then meets the
            // ordinary authentication rate limiter and user authentication, which is what should be answering a caller
            // behaving like this anyway.
            IPAddress peerAddress = new IPAddressString(peerIpAddress).getAddress();
            boolean registeredAgentAddress = buildAgentAddressRegistryService.get().isRegisteredBuildAgentAddress(peerIpAddress);
            if (!registeredAgentAddress && !rateLimitService.hasRemainingBudget(peerAddress, RateLimitType.BUILD_AGENT_CLONE_TOKEN)) {
                log.warn("Rate limiting the build agent clone token check for agent {} from {}; falling through to user authentication", agentName, peerIpAddress);
                return false;
            }

            // Parsed before the scan although it is only needed after it. This is local string work that can throw, and
            // the catch at the end of this method returns without spending budget - deliberately, since a malformed
            // request is not a guess at a credential. Doing it after the scan would make an unparsable path a way to
            // run the scan for free, repeatedly.
            LocalVCRepositoryUri localVCRepositoryUri = repositoryResolver.parseRepositoryUri(request);

            var processingJobs = distributedDataAccessService.get().getProcessingJobsForAgentByName(agentName);
            if (processingJobs.isEmpty()) {
                // A registered agent running nothing, so no token can match
                spendCloneTokenBudget(registeredAgentAddress, peerAddress);
                return false;
            }

            var tokenService = buildJobCloneTokenService.get();
            BuildJobQueueItem matchingBuildJob = null;
            for (BuildJobQueueItem buildJob : processingJobs) {
                if (tokenService.tokenMatches(buildJob, presentedToken)) {
                    matchingBuildJob = buildJob;
                    break;
                }
            }
            if (matchingBuildJob == null) {
                // The one case that looks like credential guessing against a live agent, so it must not be the one case
                // that leaves no trace.
                log.warn("Build agent {} from {} presented a credential matching none of its {} running build jobs", agentName, peerIpAddress, processingJobs.size());
                spendCloneTokenBudget(registeredAgentAddress, peerAddress);
                return false;
            }

            // Origin after the token, which is the opposite of what this used to do. The origin check is no longer
            // answerable from local state alone: on a miss it reconciles against the middleware, which queries the
            // connected clients and takes a lock other requests wait on. The name that reaches this far is only an
            // identifier - it is the middleware's client name, rendered in the admin UI and guessable - so with the
            // origin check first, a caller presenting any password at all could force that work in a loop, ahead of the
            // rate limiter this path deliberately sits in front of. Requiring the token first means only a caller who
            // already holds a live job's secret can cause it. Both conditions still have to pass, so nothing is
            // weakened: a token read out of the queue by another party remains useless away from the agent's address.
            if (!buildAgentAddressRegistryService.get().isRegisteredAddressOfAgent(agentName, peerIpAddress)) {
                // Also the signal for a misconfigured proxy, where the token is right but the address the request
                // appears to come from is not the agent's.
                log.warn("Rejecting a build agent clone claiming to be agent {} from {}, which is not an address that agent is connected from", agentName, peerIpAddress);
                spendCloneTokenBudget(registeredAgentAddress, peerAddress);
                return false;
            }

            if (!tokenService.coversRepository(matchingBuildJob, localVCRepositoryUri)) {
                log.warn("Build agent {} presented the token of build job {} for repository {}, which is not one of that job's repositories {}", agentName, matchingBuildJob.id(),
                        localVCRepositoryUri, tokenService.getRepositoryIdentities(matchingBuildJob));
                spendCloneTokenBudget(registeredAgentAddress, peerAddress);
                return false;
            }
            // Tells the pre-upload hook that this request is a build agent clone, so that it does not relabel
            // whichever access log entry happens to be newest for this repository. It used to recognise a build
            // agent by the literal buildjob_user, which an agent presenting its own short name never matches.
            request.setAttribute(BUILD_AGENT_CLONE_REQUEST_ATTRIBUTE, agentName);
            // Only on the handshake, like the rate limiter above: git follows /info/refs with a git-upload-pack
            // using the same credentials, and one clone should leave one audit entry rather than two.
            if (request.getRequestURI().endsWith("/info/refs")) {
                saveBuildAgentVcsAccessLog(localVCRepositoryUri, agentName, matchingBuildJob.id(), peerIpAddress, AuthenticationMechanism.BUILD_JOB_TOKEN);
            }
            return true;
        }
        catch (Exception e) {
            // Anything unexpected here means this is not a valid build agent clone. Fall through rather than reject,
            // so a malformed header or an unparsable repository path is still handled by the normal path below.
            log.debug("Could not authenticate the request as a build agent clone", e);
            return false;
        }
    }

    /**
     * Charges one attempt against a caller's clone-token budget, for a check that reached the distributed scan and then
     * declined.
     * <p>
     * Only declines are charged. A build agent whose checks succeed therefore never approaches the limit however many
     * repositories it clones, which is what lets the limit be sized like any other guessing bound instead of having to
     * clear the busiest plausible agent - the sizing that made an earlier default an order of magnitude too permissive.
     *
     * @param registeredAgentAddress whether this address is already exempt because some agent is registered at it
     * @param peerAddress            the resolved client address, may be null if it could not be parsed
     */
    private void spendCloneTokenBudget(boolean registeredAgentAddress, @Nullable IPAddress peerAddress) {
        if (!registeredAgentAddress) {
            rateLimitService.consumePerMinute(peerAddress, RateLimitType.BUILD_AGENT_CLONE_TOKEN);
        }
    }

    /**
     * Decides whether a fetch is made with the credential that Jenkins uses to clone for a build, which is shared by the whole installation.
     * <p>
     * Two conditions close this shortcut entirely rather than narrowing it, because what it grants - repository-wide read, ahead of the rate limit, the authorization checks
     * and the access log - is worth strictly less than the attack surface it carries wherever something else can do the job.
     * <p>
     * Local CI is one of them: every build job there carries a token scoped to its own repositories, so no Artemis build agent has any use for a shared credential.
     * LocalVCBuildAgentCredentialsValidator already refuses to start such a node with one configured; this makes the shortcut unreachable rather than merely unconfigured, so
     * a credential that arrives by some other route still opens nothing. What remains is a local VC node without local CI, whose client is Jenkins - not an Artemis build
     * agent, and with neither key nor build job.
     * <p>
     * The other is ssh: build agents that authenticate with a key never present this pair.
     *
     * An accepted request is marked as a build agent clone, like one that authenticated with a build job token. Without the mark, the hook that completes the access log of
     * a fetch would take the Jenkins fetch for one of a student whenever the shared user name is not {@value #BUILD_USER_NAME}, and relabel the newest entry of the repository.
     *
     * @param request             the fetch request
     * @param authorizationHeader the Basic authorization header of that request
     * @return whether the request presents the shared build agent credential and may be served without any further check
     * @throws LocalVCAuthException if the shortcut is open and the header cannot be read
     */
    public boolean authenticateSharedBuildAgentCredential(HttpServletRequest request, String authorizationHeader) throws LocalVCAuthException {
        if (useSshForBuildAgent || distributedDataAccessService.isPresent()) {
            return false;
        }
        UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);
        // A blank configured credential must never match: this shortcut returns ahead of the rate limit, the
        // repository authorization checks and the access log, so an empty configured password would hand
        // repository-wide read access to anyone presenting the build-agent username. ConfigurationValidator
        // rejects that configuration under prod, but this path also runs where that validation does not.
        // The hasText guard is not made redundant by the constant-time comparison below: a blank configured
        // password would still match an equally blank provided one.
        boolean accepted = StringUtils.hasText(buildAgentGitUsername) && StringUtils.hasText(buildAgentGitPassword)
                && Objects.equals(usernameAndPassword.username(), buildAgentGitUsername) && secretMatches(buildAgentGitPassword, usernameAndPassword.password());
        if (accepted) {
            request.setAttribute(BUILD_AGENT_CLONE_REQUEST_ATTRIBUTE, usernameAndPassword.username());
        }
        return accepted;
    }

    /**
     * Records a build agent clone in the VCS access log, which the old shared-credential shortcut never did.
     * <p>
     * Shared by both mechanisms rather than reimplemented per transport: the ssh path resolves the same participation
     * from the same repository uri, and having one implementation is what stops the two from drifting into logging
     * different things - or, as ssh originally did, nothing at all.
     * <p>
     * Best effort: an audit entry that cannot be written must not fail the build.
     *
     * @param localVCRepositoryUri the repository being read
     * @param agentName            the short name of the build agent
     * @param buildJobId           the id of the build job the read belongs to
     * @param ipAddress            the address the agent connected from
     * @param mechanism            how the agent authenticated
     */
    public void saveBuildAgentVcsAccessLog(LocalVCRepositoryUri localVCRepositoryUri, String agentName, String buildJobId, String ipAddress, AuthenticationMechanism mechanism) {
        try {
            ProgrammingExercise exercise = getProgrammingExerciseOrThrow(localVCRepositoryUri.getProjectKey());
            var participation = programmingExerciseParticipationService.fetchParticipationWithSubmissionsByRepository(localVCRepositoryUri.getRepositoryTypeOrUserName(),
                    localVCRepositoryUri.toString(), exercise);
            String commitHash = repositoryResolver.getCommitHash(localVCRepositoryUri);
            vcsAccessLogService.ifPresent(service -> service.saveBuildAgentAccessLog(participation, agentName, buildJobId, commitHash, ipAddress, mechanism));
        }
        catch (EntityNotFoundException e) {
            // An auxiliary repository has no participation of its own, so there is nothing to attribute the access to.
            // Expected for those, and it happens on every build, so it must not be a warning.
            log.debug("No participation to record a build agent access against for {}", localVCRepositoryUri);
        }
        catch (Exception e) {
            log.warn("Could not write a VCS access log entry for build agent {} cloning {}: {}", agentName, localVCRepositoryUri, e.getMessage());
        }
    }

    /**
     * Whether a git request over HTTPS was made by a build agent or a CI system cloning for a build, rather than by a person.
     * <p>
     * Local CI agents are recognised by the attribute their authorization sets. A CI system using the shared build
     * credential, which returns from the authorization before that attribute is set, is recognised by the credential's
     * username instead; by the time a completed request is inspected, authentication has already succeeded, so the
     * username cannot be claimed without the password.
     *
     * @param request a git request that has been served
     * @return true if the request was a clone for a build
     */
    public boolean isBuildAgentClone(HttpServletRequest request) {
        if (request.getAttribute(BUILD_AGENT_CLONE_REQUEST_ATTRIBUTE) != null) {
            return true;
        }
        try {
            String username = extractUsernameAndPassword(request.getHeader(HttpHeaders.AUTHORIZATION)).username();
            return BUILD_USER_NAME.equals(username) || (StringUtils.hasText(buildAgentGitUsername) && buildAgentGitUsername.equals(username));
        }
        catch (LocalVCAuthException e) {
            return false;
        }
    }

    private ProgrammingExercise getProgrammingExerciseOrThrow(String projectKey) {
        try {
            return programmingExerciseRepository.findOneByProjectKeyOrThrow(projectKey, false);
        }
        catch (EntityNotFoundException e) {
            throw new LocalVCInternalException("Could not find single programming exercise with project key " + projectKey, e);
        }
    }
}
