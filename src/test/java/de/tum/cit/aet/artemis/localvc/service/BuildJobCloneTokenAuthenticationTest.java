package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkConfiguration;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkPolicy;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.localci.service.BuildAgentAddressRegistryService;
import de.tum.cit.aet.artemis.localci.service.BuildJobCloneTokenService;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Tests the decision made when a build agent presents the clone token of a build job over https.
 * <p>
 * This is the authorization decision that replaces the installation-wide build agent credential, so each way it can be
 * abused gets its own case: presenting a valid token for a repository the job does not use, after the job has finished,
 * from an address the agent is not connected from, or under a different agent's name.
 * <p>
 * Two properties matter as much as the rejections themselves. The username is an identifier and never a credential, so
 * naming an agent must achieve nothing on its own. And every rejection must fall through to normal user authentication
 * rather than refusing the request outright, because an agent short name could collide with a real login and that
 * person has to keep being able to use their own credentials.
 */
class BuildJobCloneTokenAuthenticationTest {

    private static final String BASE_URI = "http://localhost:8000";

    private static final String AGENT_NAME = "artemis-build-agent-1";

    private static final String AGENT_ADDRESS = "10.0.0.5";

    private static final String CLONE_TOKEN = "bjct-the-token-of-this-job";

    private static final String BUILD_JOB_ID = "job-1";

    private LocalVCServletService localVCServletService;

    private DistributedDataAccessService distributedDataAccessService;

    private BuildAgentAddressRegistryService buildAgentAddressRegistryService;

    private DistributedMap<String, BuildAgentInformation> buildAgentInformationMap;

    private RateLimitService rateLimitService;

    private String assignmentRepositoryUri;

    private String unrelatedRepositoryUri;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        distributedDataAccessService = mock(DistributedDataAccessService.class);
        buildAgentAddressRegistryService = mock(BuildAgentAddressRegistryService.class);

        // The cheap gate the authorization runs first: a single-key lookup that rejects a username which is not a
        // build agent at all, before anything reads the whole processing map.
        buildAgentInformationMap = mock(DistributedMap.class);
        when(distributedDataAccessService.getDistributedBuildAgentInformation()).thenReturn(buildAgentInformationMap);
        when(buildAgentInformationMap.get(AGENT_NAME))
                .thenReturn(new BuildAgentInformation(new BuildAgentDTO(AGENT_NAME, "address", "display"), 1, 0, List.of(), null, null, null, 0));

        // Permissive by default: a void mock does nothing, so every other test in this class runs as if under the limit.
        rateLimitService = mock(RateLimitService.class);
        // The default for a caller with budget left; the tests that care set it to false explicitly
        when(rateLimitService.hasRemainingBudget(any(), any())).thenReturn(true);
        localVCServletService = new LocalVCServletService(null, null, null, null, null, null, null, null, null, null, null, null, Optional.empty(), null, rateLimitService, null,
                null, Optional.of(distributedDataAccessService), Optional.of(buildAgentAddressRegistryService), Optional.of(new BuildJobCloneTokenService()),
                policyAllowingEverything(), null, null);
        ReflectionTestUtils.setField(localVCServletService, "localVCBaseUri", URI.create(BASE_URI));

        // Build the expected URIs the same way the production code derives them from the request path, rather than
        // hard-coding a format that LocalVCRepositoryUri could change
        assignmentRepositoryUri = repositoryUriOf("/git/TESTEXERCISE/testexercise-student1.git");
        unrelatedRepositoryUri = repositoryUriOf("/git/TESTEXERCISE/testexercise-student2.git");

        when(buildAgentAddressRegistryService.isRegisteredAddressOfAgent(AGENT_NAME, AGENT_ADDRESS)).thenReturn(true);
    }

    private static BuildAgentNetworkPolicy policyAllowingEverything() {
        return new BuildAgentNetworkPolicy(new BuildAgentNetworkConfiguration(), new MockEnvironment());
    }

    private static BuildAgentNetworkPolicy policyAllowingOnly(String range) {
        BuildAgentNetworkConfiguration configuration = new BuildAgentNetworkConfiguration();
        configuration.setAllowedRanges(List.of(range));
        return new BuildAgentNetworkPolicy(configuration, new MockEnvironment());
    }

    private static String repositoryUriOf(String path) {
        return new LocalVCRepositoryUri(URI.create(BASE_URI), Path.of(path)).toString();
    }

    private static BuildJobQueueItem buildJob(String cloneToken, String... repositoryUris) {
        String assignment = repositoryUris.length > 0 ? repositoryUris[0] : null;
        var repositoryInfo = new RepositoryInfo("slug", RepositoryType.USER, RepositoryType.USER, assignment, null, null, new String[0], new String[0]);
        var jobTimingInfo = new JobTimingInfo(ZonedDateTime.now(), ZonedDateTime.now(), null, null, 60);
        var buildConfig = new BuildConfig("script", "image", "commit", "assignmentCommit", "testCommit", "main", null, null, false, false, List.of(), 0, null, null, null, null);
        return new BuildJobQueueItem(BUILD_JOB_ID, "name", new BuildAgentDTO(AGENT_NAME, "address", "display"), 1L, 2L, 3L, 0, 1, null, repositoryInfo, jobTimingInfo, buildConfig,
                null, cloneToken);
    }

    private static HttpServletRequest request(String username, String password, String repositoryPath, String peerAddress) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String credentials = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic " + credentials);
        // Deliberately not /info/refs: that path additionally writes an access log entry, which needs the database.
        // The authorization decision under test is identical for both requests of a git operation.
        when(request.getRequestURI()).thenReturn(repositoryPath + "/git-upload-pack");
        when(request.getRemoteAddr()).thenReturn(peerAddress);
        return request;
    }

    private boolean authenticate(HttpServletRequest request) {
        return Boolean.TRUE
                .equals(ReflectionTestUtils.invokeMethod(localVCServletService, "authenticateBuildJobCloneToken", request, request.getHeader(HttpHeaders.AUTHORIZATION)));
    }

    @Test
    void shouldAcceptTheTokenOfARunningJobForItsOwnRepository() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));

        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isTrue();
    }

    /**
     * The point of scoping the token: holding it must not open the repositories of other participants.
     */
    @Test
    void shouldRejectARepositoryThatTheJobDoesNotUse() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));

        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student2.git", AGENT_ADDRESS)))
                .as("the token of one participation must not open another").isFalse();
        assertThat(unrelatedRepositoryUri).isNotEqualTo(assignmentRepositoryUri);
    }

    /**
     * What replaces an expiry. The job leaves the processing list when it finishes, is cancelled, or hits the build
     * timeout, and the token has to stop working at that moment without any clock being involved.
     */
    @Test
    void shouldRejectATokenOnceTheJobIsNoLongerRunning() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of());

        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();
    }

    @Test
    void shouldRejectAnAddressTheAgentIsNotConnectedFrom() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));
        when(buildAgentAddressRegistryService.isRegisteredAddressOfAgent(AGENT_NAME, "203.0.113.9")).thenReturn(false);

        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", "203.0.113.9")))
                .as("a token stolen from the queue is useless unless it is also presented from the owning agent's address").isFalse();
    }

    @Test
    void shouldRejectAPeerOutsideTheConfiguredNetworks() {
        ReflectionTestUtils.setField(localVCServletService, "buildAgentNetworkPolicy", policyAllowingOnly("10.0.0.0/8"));
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));

        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", "203.0.113.9"))).isFalse();
    }

    @Test
    void shouldAcceptAPeerInsideTheConfiguredNetworks() {
        ReflectionTestUtils.setField(localVCServletService, "buildAgentNetworkPolicy", policyAllowingOnly("10.0.0.0/8"));
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));

        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isTrue();
    }

    /**
     * Everything ahead of the processing map read is O(1); the read itself pulls and deserializes every entry. A caller
     * inside the build agent networks who knows an agent name passes the cheap gates with any password, so the read has
     * to be bounded per source rather than merely reached less often. Asserting the return value alone would not show
     * that: it is already false for a wrong token. The point is that the expensive call is never made.
     */
    @Test
    void shouldNotReadTheProcessingJobsOnceTheSourceIsOverTheLimit() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));
        when(rateLimitService.hasRemainingBudget(any(), eq(RateLimitType.BUILD_AGENT_CLONE_TOKEN))).thenReturn(false);

        assertThat(authenticate(request(AGENT_NAME, "bjct-arbitrary-password", "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();
        // Even the correct token gets no fast path while over the limit; it falls through to user authentication.
        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();

        verify(distributedDataAccessService, never()).getProcessingJobsForAgentByName(any());
    }

    /**
     * The limit must be spent per source address, and only past the cheap gates, so ordinary non-agent traffic cannot
     * exhaust the agents' budget.
     */
    @Test
    void shouldNotSpendTheLimitForAUsernameThatIsNotABuildAgent() {
        assertThat(authenticate(request("not-an-agent", "whatever", "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();

        verify(rateLimitService, never()).consumePerMinute(any(), any());
    }

    /**
     * A check that succeeds must cost nothing. Build agents clone several repositories per job and run jobs
     * concurrently, so charging every successful check would force the limit to be sized for the busiest plausible
     * agent instead of for guessing - which is how an earlier default ended up an order of magnitude too permissive.
     */
    @Test
    void shouldNotSpendTheLimitOnASuccessfulCheck() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));

        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isTrue();

        verify(rateLimitService, never()).consumePerMinute(any(), any());
    }

    /**
     * The counterpart: a credential that reached the scan and matched nothing is exactly what the limit exists to
     * bound, so that attempt is charged.
     */
    @Test
    void shouldSpendTheLimitOnACheckThatReachedTheScanAndDeclined() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));

        assertThat(authenticate(request(AGENT_NAME, "bjct-not-the-token", "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();

        verify(rateLimitService).consumePerMinute(any(), eq(RateLimitType.BUILD_AGENT_CLONE_TOKEN));
    }

    /**
     * An address some build agent is registered at skips the limiter altogether. That is the automatic form of listing
     * an agent in {@code artemis.rate-limiting.exempt-addresses}: it follows the agents as they connect instead of
     * being a list an operator has to keep correct.
     */
    @Test
    void shouldNotLimitAnAddressABuildAgentIsRegisteredAt() {
        when(buildAgentAddressRegistryService.isRegisteredBuildAgentAddress(AGENT_ADDRESS)).thenReturn(true);
        when(rateLimitService.hasRemainingBudget(any(), any())).thenReturn(false);
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));

        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS)))
                .as("an exhausted budget must not stop a " + "registered agent, whose address is exempt").isTrue();

        assertThat(authenticate(request(AGENT_NAME, "bjct-not-the-token", "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();
        verify(rateLimitService, never()).consumePerMinute(any(), any());
    }

    @Test
    void shouldRejectAWrongToken() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));

        assertThat(authenticate(request(AGENT_NAME, "bjct-not-the-token", "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();
    }

    /**
     * The username carries no authority of its own. Naming an agent that is running a job, with anything other than
     * that job's token, must achieve nothing.
     */
    @Test
    void shouldRejectAnAgentNameWithoutAMatchingToken() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(CLONE_TOKEN, assignmentRepositoryUri)));

        assertThat(authenticate(request(AGENT_NAME, "", "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();
        assertThat(authenticate(request(AGENT_NAME, "arbitrary-password", "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();
    }

    /**
     * A job that carries no token, which is what a rolling upgrade produces while some core node still issues none,
     * must not be openable by presenting no password.
     */
    @Test
    void shouldRejectAJobWithoutATokenEvenWhenNoPasswordIsPresented() {
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(buildJob(null, assignmentRepositoryUri)));

        assertThat(authenticate(request(AGENT_NAME, "", "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();
    }

    /**
     * A node running LocalVC with Jenkins has no local CI, so there are no build jobs and nothing can present a token.
     * The branch has to decline rather than fail.
     */
    @Test
    void shouldDeclineWhenTheNodeHasNoLocalCi() {
        localVCServletService = new LocalVCServletService(null, null, null, null, null, null, null, null, null, null, null, null, Optional.empty(), null, null, null, null,
                Optional.empty(), Optional.empty(), Optional.empty(), policyAllowingEverything(), null, null);
        ReflectionTestUtils.setField(localVCServletService, "localVCBaseUri", URI.create(BASE_URI));

        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();
        verify(buildAgentAddressRegistryService, never()).isRegisteredAddressOfAgent(any(), any());
    }

    /**
     * A malformed authorization header must decline rather than propagate, so the request still reaches normal user
     * authentication.
     */
    @Test
    void shouldDeclineOnAMalformedAuthorizationHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic not-valid-base64!!");
        when(request.getRequestURI()).thenReturn("/git/TESTEXERCISE/testexercise-student1.git/git-upload-pack");

        assertThat(authenticate(request)).isFalse();
    }

    /**
     * The gate that keeps this method cheap for unauthenticated callers. It runs for every read request carrying any
     * Basic header and ahead of the rate limiter, so a username that is not a build agent must be rejected by a single
     * key lookup and must never reach the whole-map read below.
     */
    @Test
    void shouldRejectAnUnknownUsernameWithoutReadingTheProcessingJobs() {
        assertThat(authenticate(request("not-an-agent", CLONE_TOKEN, "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();

        verify(buildAgentInformationMap).get("not-an-agent");
        verify(distributedDataAccessService, never()).getProcessingJobsForAgentByName(any());
    }

    /**
     * The ordering that replaced "origin before the token", and the reason it was reversed. The origin check is no longer
     * answerable from local state: on a miss it reconciles against the middleware, querying the connected clients and
     * taking a lock that other requests wait on. The username that gets this far is only an identifier - the
     * middleware's client name, which is rendered in the admin UI and guessable - so with the origin check first, a
     * caller presenting any password could force that work in a loop, ahead of the rate limiter this path deliberately
     * precedes. Requiring the token first means only a caller who already holds a live job's secret can cause it.
     * <p>
     * The cost of the reversal is that a wrong password now reaches the processing-list read, which the earlier ordering
     * kept behind the origin check. That read is bounded and gated by the single-key agent lookup above, and it is far
     * cheaper than a provider query plus a lock other requests block on, so this is the better of the two exposures
     * rather than a free improvement.
     */
    @Test
    void shouldNotCheckTheOriginForARequestWithoutAValidToken() {
        assertThat(authenticate(request(AGENT_NAME, "bjct-not-the-token", "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();

        verify(buildAgentAddressRegistryService, never()).isRegisteredAddressOfAgent(any(), any());
    }

    /**
     * And the same for a password that is not even shaped like a token, which is what a credential-stuffing flood looks
     * like. Nothing that can reach the middleware may run for it.
     */
    @Test
    void shouldNotCheckTheOriginForAnArbitraryPassword() {
        assertThat(authenticate(request(AGENT_NAME, "hunter2", "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();

        verify(buildAgentAddressRegistryService, never()).isRegisteredAddressOfAgent(any(), any());
    }

    /**
     * A request whose repository path cannot be parsed must not reach the distributed scan.
     * <p>
     * The catch at the end of the method returns without spending budget, which is right - a malformed request is not a
     * guess at a credential - but it means anything that can throw after the scan is a way to run the scan for free,
     * repeatedly. Parsing the path is local work, so it happens before.
     */
    @Test
    void shouldNotReadTheProcessingJobsForAnUnparsableRepositoryPath() {
        assertThat(authenticate(request(AGENT_NAME, CLONE_TOKEN, "/git/not-a-repository-path", AGENT_ADDRESS))).isFalse();

        verify(distributedDataAccessService, never()).getProcessingJobsForAgentByName(any());
    }

    /**
     * The cheapest gate of all, and the one that keeps this method off the hot path of ordinary traffic. A student's
     * password or {@code vcpat-} access token is not a clone token and can be recognised as such with a string
     * comparison, so it must not cost a distributed lookup - every human git fetch in the installation carries one and
     * reaches this method before anything else.
     * <p>
     * Turning the prefix into a gate is safe because every token is minted with it. It is checked here rather than
     * assumed, since dropping it from {@code generateCloneToken} would otherwise reject every clone in the installation
     * with nothing pointing at the cause.
     */
    @Test
    void shouldRejectACredentialWithoutTheTokenPrefixBeforeAnyDistributedLookup() {
        assertThat(new BuildJobCloneTokenService().generateCloneToken()).as("the prefix is load bearing: it is what lets a non-token credential be rejected locally")
                .startsWith(BuildJobCloneTokenService.CLONE_TOKEN_PREFIX);

        assertThat(authenticate(request(AGENT_NAME, "vcpat-a-users-access-token", "/git/TESTEXERCISE/testexercise-student1.git", AGENT_ADDRESS))).isFalse();

        verify(buildAgentInformationMap, never()).get(any());
        verify(distributedDataAccessService, never()).getProcessingJobsForAgentByName(any());
    }
}
